package com.example.vap_back.benchmark;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 실행 중인 Spring/Kafka/Redis를 대상으로 하는 성능 측정 테스트.
 *
 * 기본 test task에서는 실행되지 않으며 RUN_PERFORMANCE_TESTS=true일 때만 활성화된다.
 * Kafka throughput은 애플리케이션 비즈니스 처리시간이 아니라 broker에서 consumer가
 * 메시지를 성공적으로 읽는 처리량이다. 애플리케이션 처리시간은 별도 결과 파일의
 * processing latency 측정값과 함께 해석해야 한다.
 */
@Tag("performance")
@EnabledIfEnvironmentVariable(named = "RUN_PERFORMANCE_TESTS", matches = "true")
class PerformanceBenchmarkTest {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(envInt("HTTP_CONNECT_TIMEOUT_SECONDS", 3)))
            .build();
    private static final Path RESULT_DIR = Path.of("build", "performance-results");

    @Test
    void synchronousApiVsKafkaEnqueueLatency() throws Exception {
        String syncUrl = env("SYNC_API_URL", "http://localhost:8080/api/internal/news/crawl?category=it");
        String asyncUrl = env("ASYNC_API_URL", "http://localhost:8080/api/internal/news/crawl/async?category=it");
        String brokers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        int samples = envInt("BENCHMARK_SAMPLES", 30);
        List<HttpResult> syncResults = new ArrayList<>();
        for (int i = 0; i < samples; i++) syncResults.add(postResult(syncUrl, timeoutMillis()));
        List<HttpResult> kafkaResults = new ArrayList<>();
        for (int i = 0; i < samples; i++) kafkaResults.add(postResult(asyncUrl, timeoutMillis()));
        List<Long> sync = latencies(syncResults);
        List<Long> kafka = latencies(kafkaResults);

        // API comparison is HTTP-to-HTTP: synchronous completion vs Kafka publish ack.
        // Direct producer measurements remain available in consumerThroughputOneVsTwo().
        writeCsv("sync-vs-kafka-enqueue.csv", "mode,samples,success,timeout,http_5xx,avg_ms,p50_ms,p95_ms\n" +
                httpRow("synchronous-api", syncResults) + httpRow("kafka-publish-api", kafkaResults));
        print("동기 API vs Kafka publish API", Map.of("sync", stats(sync), "kafka", stats(kafka)));
    }

    @Test
    void directKafkaProducerAckLatency() throws Exception {
        String brokers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        int samples = envInt("BENCHMARK_SAMPLES", 30);
        String topic = env("KAFKA_BENCHMARK_TOPIC", "performance-benchmark");
        createTopic(brokers, topic, 2);
        List<Long> kafka = new ArrayList<>();
        try (KafkaProducer<String, String> producer = producer(brokers)) {
            for (int i = 0; i < samples; i++) {
                long start = System.nanoTime();
                producer.send(new ProducerRecord<>(topic, "sample-" + i, "benchmark-" + i)).get();
                kafka.add(nanosToMillis(System.nanoTime() - start));
            }
        }
        writeCsv("kafka-producer-ack.csv", "mode,samples,avg_ms,p50_ms,p95_ms\n" + row("kafka-producer-ack", kafka));
    }

    @Test
    void concurrentCrawlTimeoutCount() throws Exception {
        String url = env("SYNC_API_URL", "http://localhost:8080/api/internal/news/crawl?category=it");
        int concurrency = envInt("CRAWL_CONCURRENCY", 10);
        long timeout = timeoutMillis();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<HttpResult>> results = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                results.add(pool.submit(() -> postResult(url, timeout)));
            }
            List<HttpResult> samples = results.stream().map(PerformanceBenchmarkTest::getHttpUnchecked).toList();
            long timeoutCount = samples.stream().filter(HttpResult::timeout).count();
            long serverErrors = samples.stream().filter(r -> r.status() >= 500).count();
            long successCount = samples.stream().filter(r -> r.status() >= 200 && r.status() < 300).count();
            writeCsv("concurrent-crawl.csv", "concurrency,timeout_ms,timeout_count,total\n" +
                    concurrency + "," + timeout + "," + timeoutCount + "," + samples.size() + "\n" +
                    "success_count," + successCount + "\nhttp_5xx_count," + serverErrors + "\n");
            System.out.printf("[performance] concurrent crawl: N=%d, success=%d, timeout=%d, http5xx=%d%n", concurrency, successCount, timeoutCount, serverErrors);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void consumerThroughputOneVsTwo() throws Exception {
        String brokers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        String topic = env("KAFKA_THROUGHPUT_TOPIC", "performance-throughput-" + UUID.randomUUID());
        int messages = envInt("KAFKA_MESSAGES", 1000);
        createTopic(brokers, topic, 2);
        try (KafkaProducer<String, String> producer = producer(brokers)) {
            for (int i = 0; i < messages; i++) producer.send(new ProducerRecord<>(topic, "key-" + i, "payload-" + i));
            producer.flush();
        }
        double one = consume(brokers, topic, messages, 1);
        double two = consume(brokers, topic, messages, 2);
        writeCsv("consumer-throughput.csv", "consumers,messages,elapsed_ms,messages_per_second\n" +
                "1," + messages + "," + one + "," + perSecond(messages, one) + "\n" +
                "2," + messages + "," + two + "," + perSecond(messages, two) + "\n");
        System.out.printf("[performance] consumer throughput: 1=%.2f msg/s, 2=%.2f msg/s%n", perSecond(messages, one), perSecond(messages, two));
    }

    @Test
    void databaseVsRedisReadLatencyAndCacheHitRatio() throws Exception {
        String dbUrl = System.getenv("READ_DB_URL");
        String cacheUrl = System.getenv("READ_CACHE_URL");
        String redisKey = System.getenv("REDIS_CACHE_KEY");
        assumeTrue(dbUrl != null && cacheUrl != null, "READ_DB_URL과 READ_CACHE_URL을 설정해야 합니다.");
        int samples = envInt("BENCHMARK_SAMPLES", 30);
        List<Long> db = measureGet(dbUrl, samples);
        List<Long> cache = measureGet(cacheUrl, samples);
        String hitRatio = redisKey == null ? "not-measured" : measureRedisHitRatio(redisKey, samples);
        writeCsv("db-vs-redis.csv", "mode,samples,avg_ms,p50_ms,p95_ms,cache_hit_ratio\n" +
                rowWithHitRatio("database", db, "not-measured") + rowWithHitRatio("redis-cache", cache, hitRatio));
        print("DB vs Redis", Map.of("database", stats(db), "redis", stats(cache), "hitRatio", hitRatio));
    }

    private static List<Long> measureGet(String url, int samples) throws Exception {
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMillis())).GET().build(), HttpResponse.BodyHandlers.discarding());
            values.add(nanosToMillis(System.nanoTime() - start));
        }
        return values;
    }

    private static long httpPostMillis(String url, long timeout) throws Exception {
        long start = System.nanoTime();
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeout)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        return nanosToMillis(System.nanoTime() - start);
    }

    private static HttpResult postResult(String url, long timeout) {
        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            return new HttpResult(nanosToMillis(System.nanoTime() - start), response.statusCode(), false);
        } catch (HttpTimeoutException e) {
            return new HttpResult(nanosToMillis(System.nanoTime() - start), 0, true);
        } catch (Exception e) {
            return new HttpResult(nanosToMillis(System.nanoTime() - start), 0, false);
        }
    }

    private static List<Long> latencies(List<HttpResult> results) { return results.stream().map(HttpResult::latencyMs).toList(); }
    private static String httpRow(String mode, List<HttpResult> results) { Stats s = stats(latencies(results)); long success = results.stream().filter(r -> r.status() >= 200 && r.status() < 300).count(); long timeout = results.stream().filter(HttpResult::timeout).count(); long errors = results.stream().filter(r -> r.status() >= 500).count(); return mode + "," + results.size() + "," + success + "," + timeout + "," + errors + "," + s.avg + "," + s.p50 + "," + s.p95 + "\n"; }

    private static String measureRedisHitRatio(String key, int samples) {
        String uri = env("REDIS_URI", "redis://localhost:6379");
        try (RedisClient client = RedisClient.create(uri); StatefulRedisConnection<String, String> connection = client.connect()) {
            int hits = 0;
            for (int i = 0; i < samples; i++) if (connection.sync().exists(key) > 0) hits++;
            return String.format("%.4f", hits / (double) samples);
        }
    }

    private static double consume(String brokers, String topic, int expected, int consumers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(consumers);
        String group = "performance-" + UUID.randomUUID();
        long start = System.nanoTime();
        AtomicInteger received = new AtomicInteger();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < consumers; i++) futures.add(pool.submit(() -> consumePartitionSet(brokers, topic, group, expected, received)));
            futures.forEach(PerformanceBenchmarkTest::getUnchecked);
            assertThat(received.get()).isEqualTo(expected);
            return nanosToMillis(System.nanoTime() - start);
        } finally { pool.shutdownNow(); }
    }

    private static int consumePartitionSet(String brokers, String topic, String group, int expected, AtomicInteger received) {
        try (KafkaConsumer<String, String> consumer = consumer(brokers, group)) {
            consumer.subscribe(Collections.singletonList(topic));
            int count = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (received.get() < expected && System.nanoTime() < deadline) {
                int polled = consumer.poll(Duration.ofMillis(200)).count();
                if (polled > 0) {
                    int remaining = expected - received.get();
                    int accepted = Math.min(polled, remaining);
                    received.addAndGet(accepted);
                    count += accepted;
                }
            }
            return count;
        }
    }

    private static int getUnchecked(Future<Integer> future) {
        try { return future.get(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } catch (ExecutionException e) { throw new RuntimeException(e.getCause()); }
    }
    private static HttpResult getHttpUnchecked(Future<HttpResult> future) { try { return future.get(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } catch (ExecutionException e) { return new HttpResult(0, 0, false); } }

    private static KafkaProducer<String, String> producer(String brokers) {
        Properties p = new Properties(); p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers); p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class); p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class); p.put(ProducerConfig.ACKS_CONFIG, "all"); return new KafkaProducer<>(p);
    }
    private static KafkaConsumer<String, String> consumer(String brokers, String group) {
        Properties p = new Properties(); p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers); p.put(ConsumerConfig.GROUP_ID_CONFIG, group); p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); return new KafkaConsumer<>(p);
    }
    private static void createTopic(String brokers, String topic, int partitions) throws Exception { try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers))) { admin.createTopics(List.of(new NewTopic(topic, partitions, (short) 1))).all().get(); } catch (ExecutionException e) { if (!e.getCause().getMessage().contains("TopicExists")) throw e; } }
    private static String row(String mode, List<Long> values) { Stats s = stats(values); return mode + "," + values.size() + "," + s.avg + "," + s.p50 + "," + s.p95 + "\n"; }
    private static String rowWithHitRatio(String mode, List<Long> values, String hitRatio) { Stats s = stats(values); return mode + "," + values.size() + "," + s.avg + "," + s.p50 + "," + s.p95 + "," + hitRatio + "\n"; }
    private static Stats stats(List<Long> values) { List<Long> sorted = values.stream().sorted().collect(Collectors.toList()); return new Stats(sorted.stream().mapToLong(Long::longValue).average().orElse(0), percentile(sorted, .50), percentile(sorted, .95)); }
    private static long percentile(List<Long> sorted, double percentile) { return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percentile) - 1)); }
    private static double perSecond(int count, double elapsedMs) { return count / (elapsedMs / 1000.0); }
    private static long nanosToMillis(long nanos) { return Math.max(0, nanos / 1_000_000); }
    private static long timeoutMillis() { return envInt("REQUEST_TIMEOUT_MS", 10_000); }
    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    private static int envInt(String name, int fallback) { try { return Integer.parseInt(env(name, String.valueOf(fallback))); } catch (NumberFormatException e) { return fallback; } }
    private static void print(String name, Map<String, Object> result) { System.out.println("[performance] " + name + " => " + result); }
    private static void writeCsv(String name, String content) throws Exception { Files.createDirectories(RESULT_DIR); Files.writeString(RESULT_DIR.resolve(name), content); }
    private record Stats(double avg, long p50, long p95) { @Override public String toString() { return String.format("avg=%.2fms,p50=%dms,p95=%dms", avg, p50, p95); } }
    private record HttpResult(long latencyMs, int status, boolean timeout) { }
}
