#!/usr/bin/env bash
# Blue-Green 무중단 배포 검증
#   사용법 (infra 폴더에서, Git Bash):  bash bluegreen-test/run.sh original   또는   bash bluegreen-test/run.sh fixed
#   original: 현재 scripts/deploy-blue-green.sh 그대로 실행
#   fixed   : 기반 서비스 기동에 --no-recreate, 대상 슬롯 갱신에 --no-deps를 적용한 버전으로 실행
set -euo pipefail
export MSYS_NO_PATHCONV=1

MODE="${1:-original}"
cd "$(dirname "$0")/.."                      # infra/
OUT="bluegreen-test/result-${MODE}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
export DOCKER_USERNAME=vaptest
# 로컬에 떠 있을 수 있는 다른 dev 스택(compose.yaml/compose.perf.yaml 등)과 컨테이너/볼륨/
# 네트워크가 절대 겹치지 않도록 별도 프로젝트로 격리한다. (2026-09-30: 격리 없이 돌렸다가
# 같은 프로젝트명(infra)을 공유하던 dev용 perf-mysql 볼륨을 실수로 날린 적이 있음)
export COMPOSE_PROJECT_NAME=vap-bgtest
C="docker compose -f compose-prod.yaml -f bluegreen-test/compose.isolate.yaml"
log(){ echo "[bg-test $(date +%T)] $*" | tee -a "$OUT/run.log"; }

# 0) 테스트용 .env (없을 때만 생성, 로컬 전용 값)
if [ ! -f .env ]; then
  sed 's/\r$//' .env.example \
    | sed 's/=change-me/=bgtest-pass/; s/^JWT_SECRET=.*/JWT_SECRET=bluegreen-test-secret-key-0123456789abcdef/' > .env
  log ".env를 테스트 값으로 생성했습니다."
fi

# 1) 이미지 준비: 같은 이미지에 v1, v2 태그 (v2 = '새 버전' 역할)
if [ "${SKIP_BUILD:-false}" != "true" ]; then
  log "백엔드 jar / 이미지 빌드"
  (cd ../backend && ./gradlew -q clean build -x test)
  docker build -q -f Dockerfile -t vaptest/vap-backend:v1 ../backend >/dev/null
  docker build -q -f ../ai/Dockerfile -t vaptest/vap-python:v1 ../ai >/dev/null
fi
for i in vap-backend vap-python; do docker tag vaptest/$i:v1 vaptest/$i:v2; done

# container_name(vap-mysql 등 고정값)만 vap-bgtest- 접두사로 치환한 사본을 만든다.
# `docker compose config`로 병합하면 ${IMAGE_TAG} 같은 변수가 그 시점 값으로 굳어버려서
# 뒤에서 v1 -> v2로 바꾸는 이 테스트의 핵심 동작이 깨지므로, sed로만 치환해 변수는 그대로 둔다.
MERGED=.bgtest-isolate.yaml   # infra/ 최상위에 둬야 docker compose가 .env를 자동으로 찾는다
sed -E 's/container_name: vap-(mysql|redis|kafka|ai|backend-blue|backend-green|nginx)$/container_name: vap-bgtest-\1/' \
  compose-prod.yaml > "$MERGED"
C="docker compose -f $MERGED"
DEPLOY_COMPOSE_FILE="$MERGED"   # deploy-blue-green.sh에 COMPOSE_FILE로 넘길 값

# 2) 초기 상태: 전체 v1, 활성 슬롯 blue
log "스택 초기화 (v1, active=blue)"
IMAGE_TAG=v1 $C down -t 5 >/dev/null 2>&1 || true
printf 'set $active backend-blue;\n' > nginx/conf.d/active.conf
IMAGE_TAG=v1 $C up -d >/dev/null
for i in $(seq 1 90); do
  curl -fs http://localhost/actuator/health 2>/dev/null | grep -q '"status":"UP"' && break; sleep 2
done
curl -fs http://localhost/actuator/health | grep -q '"status":"UP"' || { log "blue가 기동되지 않았습니다"; exit 1; }
for s in blue green; do
  for i in $(seq 1 90); do
    $C exec -T backend-$s curl -fs http://localhost:8080/actuator/health 2>/dev/null | grep -q UP && break; sleep 2
  done
done
log "blue/green 모두 UP"

# 3) Kafka consumer group 스냅샷 (백그라운드, 3초 간격)
( while true; do
    echo "=== $(date +%T)"
    for g in news-crawler-group analysis-worker-group users-group; do
      docker exec vap-bgtest-kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
        --describe --group $g --members 2>/dev/null | grep -v '^$' || true
    done
    sleep 3
  done ) > "$OUT/kafka-groups.log" 2>&1 &
KPID=$!
{ echo "blue  IP: $(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' vap-bgtest-backend-blue)"
  echo "green IP: $(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' vap-bgtest-backend-green)"; } > "$OUT/ips-before.txt"

# 활성 슬롯(blue) 컨테이너 ID 스냅샷 - 배포 중 "재생성"되는지(=ID가 바뀌는지) 판정용.
# IMAGE_TAG가 v1->v2로 바뀌면, active(blue)를 다시 "up -d"할 때도 compose가 blue의 설정이
# 바뀐 것으로 보고 재생성할 수 있다 - 이게 이 테스트의 핵심 관심사다.
BLUE_ID_BEFORE=$(docker inspect -f '{{.Id}}' vap-bgtest-backend-blue)

# 4) 부하 시작
NET=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' vap-bgtest-nginx)
docker rm -f vap-bgtest-k6 >/dev/null 2>&1 || true
docker run --name vap-bgtest-k6 -i --network "$NET" -e VUS="${VUS:-10}" grafana/k6 run --quiet - \
  < bluegreen-test/k6-deploy.js > "$OUT/k6.out" 2> "$OUT/k6.err" &
K6PID=$!
sleep 15

# 5) 배포 실행 (v2)
sed 's/\r$//' scripts/deploy-blue-green.sh > scripts/.deploy-under-test.sh
if [ "$MODE" = "fixed" ]; then
  sed -i 's/compose up -d mysql redis kafka ai nginx "\$active"/compose up -d --no-recreate mysql redis kafka ai nginx "$active"/;
          s/compose up -d "\$target"/compose up -d --no-deps "$target"/' scripts/.deploy-under-test.sh
fi
DEPLOY_START=$(date +%s%3N)
log "배포 시작 (mode=$MODE)"
set +e
COMPOSE_FILE="$DEPLOY_COMPOSE_FILE" IMAGE_TAG=v2 SKIP_PULL=true MONITOR_SECONDS=30 HEALTH_TIMEOUT_SECONDS=180 \
  bash scripts/.deploy-under-test.sh 2>&1 | tee "$OUT/deploy.log"
DEPLOY_RC=${PIPESTATUS[0]}
set -e
DEPLOY_END=$(date +%s%3N)
log "배포 종료 rc=$DEPLOY_RC ($(( (DEPLOY_END-DEPLOY_START)/1000 ))s)"
sleep 15

BLUE_ID_AFTER=$(docker inspect -f '{{.Id}}' vap-bgtest-backend-blue 2>/dev/null || echo "not-found")
if [ "$BLUE_ID_BEFORE" = "$BLUE_ID_AFTER" ]; then
  BLUE_RECREATED="no"
else
  BLUE_RECREATED="yes"
fi

# 6) 정리
docker kill -s INT vap-bgtest-k6 >/dev/null 2>&1 || true
wait $K6PID 2>/dev/null || true
kill $KPID 2>/dev/null || true
rm -f scripts/.deploy-under-test.sh
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}' | grep vap-bgtest > "$OUT/containers-after.txt"

# 7) 결과
SUM=$(grep -o 'K6_SUMMARY.*' "$OUT/k6.out" | sed 's/K6_SUMMARY //' || true)
FAILS=$(grep -o 'FAIL [0-9]* [0-9]*' "$OUT/k6.err" || true)
{
  echo "mode           : $MODE"
  echo "deploy rc      : $DEPLOY_RC"
  echo "k6             : $SUM"
  if [ -n "$FAILS" ]; then
    echo "$FAILS" | awk -v s="$DEPLOY_START" '
      { t=$2; n++; st[$3]++; if(!f||t<f)f=t; if(t>l)l=t }
      END { printf "failed requests: %d\n", n
            printf "failure window : +%.1fs ~ +%.1fs after deploy start (%.1fs)\n", (f-s)/1000, (l-s)/1000, (l-f)/1000
            for (k in st) printf "  status %s: %d\n", k, st[k] }'
  else
    echo "failed requests: 0"
  fi
  echo "active slot(blue) container recreated: ${BLUE_RECREATED} (before=${BLUE_ID_BEFORE:0:12} after=${BLUE_ID_AFTER:0:12})"
  echo "--- recreated containers during deploy"
  grep -iE "recreat|start" "$OUT/deploy.log" || echo "(compose 출력 없음)"
} | tee "$OUT/summary.txt"
log "결과 폴더: infra/$OUT"
