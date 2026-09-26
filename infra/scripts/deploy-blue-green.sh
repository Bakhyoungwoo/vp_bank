#!/usr/bin/env bash
# Blue-green 배포: 비활성 슬롯만 새 이미지로 올리고, 헬스체크 통과 후 트래픽을 전환한다.
# 전환 직후 일정 시간 동안 해당 슬롯의 5xx 비율을 감시해서, 기준을 넘으면 자동으로 이전 슬롯으로 되돌린다.
#
# 이 스크립트는 compose-prod.yaml, nginx/ 디렉토리와 같은 위치(예: infra/)에서 실행한다.
# 로컬 검증과 실제 서버 배포(SSH로 이 스크립트를 그대로 실행) 양쪽에서 동일하게 쓴다.
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-compose-prod.yaml}"
ACTIVE_CONF="${ACTIVE_CONF:-nginx/conf.d/active.conf}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-60}"
MONITOR_SECONDS="${MONITOR_SECONDS:-60}"
ERROR_RATE_THRESHOLD="${ERROR_RATE_THRESHOLD:-0.05}"   # 5%

log() { echo "[deploy-blue-green] $*"; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

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
        if compose exec -T "$slot" wget -q -O- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# 지정한 슬롯의 /actuator/prometheus에서 5xx 카운트와 전체 카운트 합을 뽑는다.
# 별도 Prometheus 서버 없이, 배포 대상 슬롯 자신의 actuator 메트릭을 직접 읽는다.
snapshot_counts() {
    local slot="$1"
    local metrics
    metrics=$(compose exec -T "$slot" wget -q -O- http://localhost:8080/actuator/prometheus 2>/dev/null || true)
    local total err
    total=$(echo "$metrics" | awk -F'[{} ]+' '/^http_server_requests_seconds_count\{/ { sum += $NF } END { print sum+0 }')
    err=$(echo "$metrics" | awk -F'[{} ]+' '/^http_server_requests_seconds_count\{/ && /status="5/ { sum += $NF } END { print sum+0 }')
    echo "$total $err"
}

switch_traffic() {
    local slot="$1"
    echo "set \$active ${slot};" > "$ACTIVE_CONF"
    compose exec -T nginx nginx -s reload
    log "트래픽을 ${slot}로 전환했습니다."
}

main() {
    local active target
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

    read -r before_total before_err <<< "$(snapshot_counts "$target")"

    switch_traffic "$target"

    log "${MONITOR_SECONDS}s 동안 5xx 비율을 관찰합니다..."
    sleep "$MONITOR_SECONDS"

    read -r after_total after_err <<< "$(snapshot_counts "$target")"
    local delta_total=$((after_total - before_total))
    local delta_err=$((after_err - before_err))

    if [ "$delta_total" -le 0 ]; then
        log "관찰 기간 동안 트래픽이 없어 5xx 비율을 판단할 수 없습니다 — 배포를 성공으로 간주합니다."
        exit 0
    fi

    local ratio
    ratio=$(awk -v e="$delta_err" -v t="$delta_total" 'BEGIN { printf "%.4f", e/t }')
    log "관찰 기간 요청 ${delta_total}건 중 5xx ${delta_err}건 (비율 ${ratio}, 임계치 ${ERROR_RATE_THRESHOLD})"

    if awk -v r="$ratio" -v th="$ERROR_RATE_THRESHOLD" 'BEGIN { exit !(r > th) }'; then
        log "5xx 비율이 임계치를 초과했습니다 — ${active}로 자동 롤백합니다."
        switch_traffic "$active"
        exit 1
    fi

    log "배포 성공. 활성 슬롯: ${target}"
}

main "$@"
