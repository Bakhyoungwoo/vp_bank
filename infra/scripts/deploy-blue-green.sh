#!/usr/bin/env bash
# Blue-green 배포: 비활성 슬롯만 새 이미지로 올리고, 헬스체크 통과 후 트래픽을 전환한다.
# 전환 후 5초 간격으로 누적 5xx 비율을 감시하다가 급격히 나쁘면(EARLY_ABORT_THRESHOLD) 즉시
# 롤백하고, 그렇지 않으면 관찰이 끝난 시점에 기존 기준(ERROR_RATE_THRESHOLD)으로 최종 판정한다.
#
# 이 스크립트는 compose-prod.yaml, nginx/ 디렉토리와 같은 위치(예: infra/)에서 실행한다.
# 로컬 검증과 실제 서버 배포(SSH로 이 스크립트를 그대로 실행) 양쪽에서 동일하게 쓴다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

COMPOSE_FILE="${COMPOSE_FILE:-compose-prod.yaml}"
ACTIVE_CONF="${ACTIVE_CONF:-nginx/conf.d/active.conf}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-60}"
MONITOR_SECONDS="${MONITOR_SECONDS:-60}"
POLL_SECONDS="${POLL_SECONDS:-5}"
ERROR_RATE_THRESHOLD="${ERROR_RATE_THRESHOLD:-0.05}"        # 관찰 종료 시점 최종 판정 (5%)
EARLY_ABORT_THRESHOLD="${EARLY_ABORT_THRESHOLD:-0.20}"      # 관찰 도중 조기 중단 (20%, 최종 판정보다 훨씬 높게)
MIN_SAMPLE="${MIN_SAMPLE:-20}"                              # 이 요청 수는 쌓여야 비율을 신뢰한다 (조기 중단/최종 판정 공통)

# 운영 트래픽이 거의 없는 시간대엔 관찰 기간 동안 요청이 거의 안 들어와 매번 "판정 불가"가
# 되므로, 기본적으로 nginx를 거쳐 스스로 GET 스모크 트래픽을 흘려 표본을 만든다.
# 기본값 /actuator/health는 인증이 필요 없고(SecurityConfig permitAll), DB/Redis
# HealthIndicator를 거치므로 정적 파일(/)보다 실제 의존성 연결 여부를 더 잘 반영한다.
# (SMOKE_URLS를 인증이 필요한 실제 비즈니스 API로 바꾸고 싶다면 README의 "인증이 필요한
# 스모크 대상" 절 참고 - 이 스크립트 자체는 로그인 흐름을 갖고 있지 않다)
SMOKE_ENABLED="${SMOKE_ENABLED:-true}"
SMOKE_URLS="${SMOKE_URLS:-/actuator/health}"                # 공백으로 여러 경로 지정 가능
SMOKE_RPS="${SMOKE_RPS:-2}"

log() { echo "[deploy-blue-green] $*"; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

preflight() {
    command -v docker >/dev/null 2>&1 || { log "docker 명령을 찾을 수 없습니다."; exit 1; }
    docker compose version >/dev/null 2>&1 || { log "Docker Compose 플러그인이 필요합니다."; exit 1; }
    [ -f "$COMPOSE_FILE" ] || { log "Compose 파일을 찾을 수 없습니다: $COMPOSE_FILE"; exit 1; }
    [ -f "$ACTIVE_CONF" ] || { log "Nginx active 설정을 찾을 수 없습니다: $ACTIVE_CONF"; exit 1; }
    [ -f .env ] || { log ".env가 없습니다. 운영 비밀값을 먼저 배치하십시오."; exit 1; }
    [ -n "${DOCKER_USERNAME:-}" ] || { log "DOCKER_USERNAME이 설정되지 않았습니다."; exit 1; }
    compose config >/dev/null
}

current_active_slot() {
    # active.conf: "set $active backend-blue;" 형태에서 슬롯 이름만 뽑는다.
    grep -oE 'backend-(blue|green)' "$ACTIVE_CONF" | head -1
}

other_slot() {
    if [ "$1" = "backend-blue" ]; then echo "backend-green"; else echo "backend-blue"; fi
}

wait_for_health() {
    local slot="$1"
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
    while [ $SECONDS -lt $deadline ]; do
        if compose exec -T "$slot" curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# 지정한 슬롯의 /actuator/prometheus에서 5xx 카운트와 전체 카운트 합을 뽑는다.
# 별도 Prometheus 서버 없이, 배포 대상 슬롯 자신의 actuator 메트릭을 직접 읽는다.
# 컨테이너가 죽었거나 응답이 없으면 (측정 불가와 "0건"을 구분하기 위해) 실패(1)를 반환한다.
snapshot_counts() {
    local slot="$1"
    local metrics
    if ! metrics=$(compose exec -T "$slot" curl -fsS http://localhost:8080/actuator/prometheus 2>/dev/null); then
        return 1
    fi
    [ -n "$metrics" ] || return 1
    local total err
    total=$(echo "$metrics" | awk -F'[{} ]+' '/^http_server_requests_seconds_count\{/ { sum += $NF } END { print sum+0 }')
    err=$(echo "$metrics" | awk -F'[{} ]+' '/^http_server_requests_seconds_count\{/ && /status="5/ { sum += $NF } END { print sum+0 }')
    echo "$total $err"
}

# snapshot_counts를 한 번 더 재시도해서, 일시적인 지연/타이밍 문제로 인한 단발성 실패를
# 흡수한다 (연속 2회 실패해야 진짜 장애로 본다는 정책을 폴링 루프 밖 1회성 호출에도 맞춘 것).
snapshot_with_tolerance() {
    local slot="$1"
    local line
    if line=$(snapshot_counts "$slot"); then echo "$line"; return 0; fi
    sleep 2
    if line=$(snapshot_counts "$slot"); then echo "$line"; return 0; fi
    return 1
}

switch_traffic() {
    local slot="$1"
    echo "set \$active ${slot};" > "$ACTIVE_CONF"
    compose exec -T nginx nginx -s reload
    log "트래픽을 ${slot}로 전환했습니다."
}

SMOKE_PID=""

start_smoke_traffic() {
    [ "$SMOKE_ENABLED" = "true" ] || return 0

    local interval
    interval=$(awk -v r="$SMOKE_RPS" 'BEGIN { if (r <= 0) r = 1; printf "%.3f", 1/r }')

    (
        while true; do
            for path in $SMOKE_URLS; do
                curl -fsS -o /dev/null "http://localhost${path}" 2>/dev/null || true
                sleep "$interval"
            done
        done
    ) &
    SMOKE_PID=$!
    log "스모크 트래픽 시작 (PID ${SMOKE_PID}, URLS='${SMOKE_URLS}', RPS=${SMOKE_RPS})"
}

stop_smoke_traffic() {
    if [ -n "$SMOKE_PID" ]; then
        kill "$SMOKE_PID" 2>/dev/null || true
        wait "$SMOKE_PID" 2>/dev/null || true
        SMOKE_PID=""
    fi
}

# 전환 후 관찰을 수행한다.
#   반환값 0 = 성공(그대로 유지), 1 = 롤백함, 2 = 판정 불가(표본 부족, target에 그대로 유지)
monitor_and_maybe_rollback() {
    local target="$1" active="$2"

    local base_line
    if ! base_line=$(snapshot_with_tolerance "$target"); then
        log "전환 직후 ${target} 지표 수집 실패 — 즉시 롤백합니다."
        switch_traffic "$active"
        return 1
    fi
    local base_total base_err
    read -r base_total base_err <<< "$base_line"
    local prev_total=$base_total prev_err=$base_err
    local fail_streak=0

    start_smoke_traffic "$target"
    trap stop_smoke_traffic RETURN

    local elapsed=0
    while [ "$elapsed" -lt "$MONITOR_SECONDS" ]; do
        sleep "$POLL_SECONDS"
        elapsed=$((elapsed + POLL_SECONDS))

        local cur_line
        if ! cur_line=$(snapshot_counts "$target"); then
            fail_streak=$((fail_streak + 1))
            log "${elapsed}s 시점 ${target} 지표 수집 실패 (연속 ${fail_streak}회)"
            if [ "$fail_streak" -ge 2 ]; then
                log "지표 수집이 연속 2회 실패했습니다 — 즉시 롤백합니다."
                switch_traffic "$active"
                return 1
            fi
            continue
        fi
        fail_streak=0

        local cur_total cur_err
        read -r cur_total cur_err <<< "$cur_line"

        # 카운터가 이전 폴링보다 줄었다 = Micrometer 카운터가 리셋됨(컨테이너 재시작/죽음 추정)
        if [ "$cur_total" -lt "$prev_total" ] || [ "$cur_err" -lt "$prev_err" ]; then
            log "${elapsed}s 시점 ${target} 지표가 감소했습니다(재시작 추정, total ${prev_total}->${cur_total}) — 즉시 롤백합니다."
            switch_traffic "$active"
            return 1
        fi
        prev_total=$cur_total
        prev_err=$cur_err

        local dt=$((cur_total - base_total))
        local de=$((cur_err - base_err))
        if [ "$dt" -ge "$MIN_SAMPLE" ]; then
            local ratio
            ratio=$(awk -v e="$de" -v t="$dt" 'BEGIN { printf "%.4f", e/t }')
            if awk -v r="$ratio" -v th="$EARLY_ABORT_THRESHOLD" 'BEGIN { exit !(r > th) }'; then
                log "조기 중단: ${elapsed}s 시점 누적 5xx 비율 ${ratio}(표본 ${dt}건) - EARLY_ABORT_THRESHOLD(${EARLY_ABORT_THRESHOLD}) 초과, 즉시 롤백합니다."
                switch_traffic "$active"
                return 1
            fi
        fi
    done

    local final_line
    if ! final_line=$(snapshot_with_tolerance "$target"); then
        log "관찰 종료 시점 ${target} 지표 수집 실패 — 즉시 롤백합니다."
        switch_traffic "$active"
        return 1
    fi
    local final_total final_err
    read -r final_total final_err <<< "$final_line"
    local dt=$((final_total - base_total))
    local de=$((final_err - base_err))

    if [ "$dt" -lt "$MIN_SAMPLE" ]; then
        log "판정 불가: 관찰 기간 요청 ${dt}건 < MIN_SAMPLE(${MIN_SAMPLE}) — 비율을 신뢰할 수 없어 ${target}에 그대로 유지합니다."
        return 2
    fi

    local ratio
    ratio=$(awk -v e="$de" -v t="$dt" 'BEGIN { printf "%.4f", e/t }')
    log "관찰 기간 요청 ${dt}건 중 5xx ${de}건 (비율 ${ratio}, 임계치 ${ERROR_RATE_THRESHOLD})"

    if awk -v r="$ratio" -v th="$ERROR_RATE_THRESHOLD" 'BEGIN { exit !(r > th) }'; then
        log "5xx 비율이 임계치를 초과했습니다 — ${active}로 자동 롤백합니다."
        switch_traffic "$active"
        return 1
    fi

    return 0
}

main() {
    local active target
    preflight
    active=$(current_active_slot)
    target=$(other_slot "$active")
    log "현재 활성 슬롯: ${active}, 배포 대상 슬롯: ${target}"

    # 서버에 아무 것도 안 떠 있는 최초 배포 상황을 대비해, 활성 슬롯과 나머지 의존 서비스를
    # 먼저 기동해둔다(이미 떠 있으면 아무 일도 하지 않는다 - docker compose up -d는 멱등적).
    log "기반 서비스(mysql/redis/kafka/ai/nginx)와 현재 활성 슬롯(${active})을 확인합니다."
    if [ "${SKIP_PULL:-false}" != "true" ]; then
        compose pull ai
    fi
    compose up -d mysql redis kafka ai nginx "$active"
    if ! wait_for_health "$active"; then
        log "활성 슬롯(${active})이 정상 기동되지 않았습니다 — 배포를 중단합니다."
        exit 1
    fi

    log "${target} 컨테이너를 최신 이미지로 갱신합니다."
    if [ "${SKIP_PULL:-false}" != "true" ]; then
        compose pull "$target"
    fi
    compose up -d "$target"

    log "${target} 헬스체크 대기 중 (최대 ${HEALTH_TIMEOUT_SECONDS}s)..."
    if ! wait_for_health "$target"; then
        log "헬스체크 실패 — 트래픽을 전환하지 않고 배포를 중단합니다."
        exit 1
    fi
    log "${target} 헬스체크 통과."

    switch_traffic "$target"

    log "${MONITOR_SECONDS}s 동안 ${POLL_SECONDS}s 간격으로 5xx 비율을 관찰합니다 (조기중단 ${EARLY_ABORT_THRESHOLD} / 최종판정 ${ERROR_RATE_THRESHOLD})..."
    local rc=0
    monitor_and_maybe_rollback "$target" "$active" || rc=$?

    case "$rc" in
        0) log "배포 성공. 활성 슬롯: ${target}" ;;
        2) log "배포 결과 판정 불가 (표본 부족) — 활성 슬롯은 ${target}로 유지됩니다. 수동 확인이 필요합니다."
           exit 2 ;;
        *) exit 1 ;;
    esac
}

main "$@"
