#!/usr/bin/env bash
# 블루그린 자동 롤백(5xx 비율 감시) 검증.
#   사용법 (infra 폴더에서, Git Bash): bash bluegreen-test/rollback-test.sh [--monitor-seconds N] <rate...>
#   예: bash bluegreen-test/rollback-test.sh 0.5 0.03
#       bash bluegreen-test/rollback-test.sh --monitor-seconds 60 0.5
#     0.5  -> ERROR_RATE_THRESHOLD(5%)를 넘겨서 자동 롤백이 발동해야 하는 케이스
#     0.03 -> 임계치 미만이라 롤백이 발동하지 않아야 하는 대조군
#
# backend/config/FaultInjectionConfig.java (fault.inject.rate 프로퍼티)를 이용해
# green 슬롯에만 FAULT_INJECT_RATE 환경변수를 주입한다. compose-prod.yaml은
# 건드리지 않고 bluegreen-test/compose.fault.yaml 오버레이로만 적용한다.
set -euo pipefail
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."                      # infra/
export DOCKER_USERNAME=vaptest
export IMAGE_TAG=v1                          # 이 테스트는 이미지 교체가 아니라 설정(fault rate) 차이만 배포 대상으로 삼는다
# 로컬 dev 스택(compose.yaml/compose.perf.yaml)과 컨테이너/볼륨/네트워크가 절대 겹치지
# 않도록 별도 프로젝트로 격리한다 (2026-09-30: 격리 안 했다가 dev용 perf-mysql 볼륨을
# 실수로 날린 적이 있음).
export COMPOSE_PROJECT_NAME=vap-bgtest
ISOLATED=.bgtest-isolate.yaml   # infra/ 최상위에 둬야 docker compose가 .env를 자동으로 찾는다
sed -E 's/container_name: vap-(mysql|redis|kafka|ai|backend-blue|backend-green|nginx)$/container_name: vap-bgtest-\1/' \
  compose-prod.yaml > "$ISOLATED"
C="docker compose -f $ISOLATED"
MERGED=.bgtest-fault.yaml   # infra/ 최상위에 둬야 docker compose가 .env를 자동으로 찾는다

log(){ echo "[rollback-test $(date +%T)] $*"; }

MONITOR_SECONDS_ARG=30
NO_K6=false
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --monitor-seconds) MONITOR_SECONDS_ARG="$2"; shift 2 ;;
    --no-k6) NO_K6=true; shift ;;
    *) ARGS+=("$1"); shift ;;
  esac
done
RATES=("${ARGS[@]}")
[ ${#RATES[@]} -gt 0 ] || RATES=(0.5 0.03)

# 0) 테스트용 .env (없을 때만 생성)
if [ ! -f .env ]; then
  sed 's/\r$//' .env.example \
    | sed 's/=change-me/=bgtest-pass/; s/^JWT_SECRET=.*/JWT_SECRET=bluegreen-test-secret-key-0123456789abcdef/' > .env
  log ".env를 테스트 값으로 생성했습니다."
fi

# 1) 이미지 준비 (v1 하나만 있으면 됨 - 이번 테스트는 이미지가 아니라 환경변수로 '새 버전'을 흉내낸다)
if [ "${SKIP_BUILD:-false}" != "true" ]; then
  if ! docker image inspect vaptest/vap-backend:v1 >/dev/null 2>&1; then
    log "백엔드 jar / 이미지 빌드"
    (cd ../backend && ./gradlew -q clean build -x test)
    docker build -q -f Dockerfile -t vaptest/vap-backend:v1 ../backend >/dev/null
  fi
  if ! docker image inspect vaptest/vap-python:v1 >/dev/null 2>&1; then
    log "AI 이미지 빌드"
    docker build -q -f ../ai/Dockerfile -t vaptest/vap-python:v1 ../ai >/dev/null
  fi
fi

reset_baseline() {
  log "기준 상태로 리셋 (v1, fault 없음, active=blue)"
  $C down -t 5 >/dev/null 2>&1 || true
  printf 'set $active backend-blue;\n' > nginx/conf.d/active.conf
  $C up -d >/dev/null
  for i in $(seq 1 90); do
    curl -fs http://localhost/actuator/health 2>/dev/null | grep -q '"status":"UP"' && break; sleep 2
  done
  curl -fs http://localhost/actuator/health | grep -q '"status":"UP"' || { log "기준 상태 기동 실패"; exit 1; }
}

# 로그 한 줄마다 epoch(초, ms) 타임스탬프를 붙여 스트리밍한다.
timestamp_stream() {
  while IFS= read -r line; do
    printf '%s %s\n' "$(date +%s.%3N)" "$line"
  done
}

run_scenario() {
  local rate="$1"
  local monitor_s="$2"
  local out="bluegreen-test/result-rollback-rate${rate}-m${monitor_s}-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$out"
  log "=== 시나리오 시작: FAULT_INJECT_RATE=${rate}, MONITOR_SECONDS=${monitor_s} ==="

  reset_baseline
  { echo "blue  IP: $(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' vap-bgtest-backend-blue)"
    echo "green IP: $(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' vap-bgtest-backend-green)"; } > "$out/ips-before.txt"

  # green 슬롯에만 fault rate를 주입한 병합 compose 파일 생성. IMAGE_TAG는 이 스크립트
  # 안에서 항상 v1으로 고정이라, config로 굳혀도(container_name/이미지/환경변수 전부 값이
  # 고정됨) 뒤에서 값이 바뀔 일이 없어 안전하다 (run.sh처럼 IMAGE_TAG를 나중에 바꾸는
  # 스크립트에는 이 방식을 쓰면 안 된다 - 그건 sed로만 patch해야 한다).
  FAULT_INJECT_RATE="$rate" docker compose -f "$ISOLATED" -f bluegreen-test/compose.fault.yaml config > "$MERGED"

  K6PID=""
  if [ "$NO_K6" != "true" ]; then
    # 부하 시작: 배포(헬스체크 최대 60s) + 관찰(monitor_s) + 여유 30s
    local duration_s=$((60 + monitor_s + 30))
    NET=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' vap-bgtest-nginx)
    docker rm -f vap-bgtest-k6 >/dev/null 2>&1 || true
    docker run --name vap-bgtest-k6 -i --network "$NET" -e VUS="${VUS:-10}" -e DURATION="${duration_s}s" \
      grafana/k6 run --quiet - < bluegreen-test/k6-deploy.js > "$out/k6.out" 2> "$out/k6.err" &
    K6PID=$!
    sleep 10   # k6 setup(로그인)이 끝나고 정상 트래픽이 흐르기 시작할 시간을 준다
  else
    log "--no-k6: k6 부하 없이 deploy-blue-green.sh 자체 스모크 트래픽(SMOKE_ENABLED, 기본 켜짐)만으로 진행합니다."
    : > "$out/k6.out"; : > "$out/k6.err"
  fi

  log "배포 시작 (target=green, fault.inject.rate=${rate}, monitor=${monitor_s}s)"
  set +e
  # SMOKE_URLS=/ 로 명시 오버라이드: deploy-blue-green.sh 기본값(/actuator/health)은
  # FaultInjectionConfig가 /actuator/**를 제외하므로 fault rate의 영향을 받지 않는다.
  # 이 테스트는 스모크 트래픽 자체가 fault를 맞아야 하므로 인터셉터를 통과하는 /를 쓴다.
  COMPOSE_FILE="$MERGED" MONITOR_SECONDS="$monitor_s" HEALTH_TIMEOUT_SECONDS=60 SKIP_PULL=true SMOKE_URLS=/ \
    bash scripts/deploy-blue-green.sh 2>&1 | timestamp_stream | tee "$out/deploy.log"
  DEPLOY_RC=${PIPESTATUS[0]}
  set -e
  log "배포 종료 rc=${DEPLOY_RC}"

  [ -n "$K6PID" ] && wait $K6PID 2>/dev/null
  true
  docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}' | grep vap-bgtest > "$out/containers-after.txt"

  # deploy.log에서 전환 시각 / 롤백 여부 / 스크립트 자체 판정 비율을 뽑는다 (epoch seconds)
  local t_switch t_rollback ratio rolled_back
  t_switch=$(grep -m1 '트래픽을 backend-green로 전환했습니다' "$out/deploy.log" | awk '{print $1}')
  if grep -qE '자동 롤백합니다|즉시 롤백합니다' "$out/deploy.log"; then
    rolled_back="yes"
    t_rollback=$(grep -m1 '트래픽을 backend-blue로 전환했습니다' "$out/deploy.log" | awk '{print $1}')
  else
    rolled_back="no"
    t_rollback=""
  fi
  ratio=$(grep -oE '비율 [0-9.]+' "$out/deploy.log" | head -1 | awk '{print $2}')

  # k6 FAIL 로그(epoch ms, status)를 전환 시각 기준으로 3구간(pre-switch / monitor / post-rollback)
  # + 상태코드별로 나눈다. rate=0.03에서 주입 외의 실패가 있었는지(pre-switch에 실패가
  # 있는지 등)를 보려는 목적.
  # k6가 --quiet에서도 console.error를 time="..." level=error msg="FAIL <ms> <status>" source=console
  # 형태로 감싸서 찍으므로, 줄 시작이 아니라 FAIL 패턴이 어디에 있든 매치한다.
  awk -v tsw="$t_switch" -v trb="$t_rollback" '
    match($0, /FAIL [0-9]+ [0-9]+/) {
      line = substr($0, RSTART, RLENGTH)
      split(line, f, " ")
      t = f[2]/1000; status = f[3]
      if (t < tsw) win="pre-switch"
      else if (trb != "" && t >= trb) win="post-rollback"
      else win="monitor"
      n[win]++; total++
      key=win"|"status; bystatus[key]++
    }
    END {
      printf "total_failed=%d\n", total+0
      for (w in n) printf "window=%s count=%d\n", w, n[w]
      for (k in bystatus) { split(k, a, "|"); printf "window=%s status=%s count=%d\n", a[1], a[2], bystatus[k] }
    }
  ' "$out/k6.err" > "$out/failures-by-window.txt"

  local sum fail_count
  sum=$(grep -o 'K6_SUMMARY.*' "$out/k6.out" | sed 's/K6_SUMMARY //' || true)
  fail_count=$(grep -cE 'FAIL [0-9]+ [0-9]+' "$out/k6.err" || true)

  {
    echo "rate               : ${rate}"
    echo "monitor_seconds    : ${monitor_s}"
    echo "deploy rc          : ${DEPLOY_RC}"
    echo "rolled_back        : ${rolled_back}"
    echo "script-observed 5xx ratio (target slot metrics): ${ratio:-N/A}"
    if [ -n "$t_switch" ] && [ -n "$t_rollback" ]; then
      echo "switch->rollback elapsed : $(awk -v a="$t_switch" -v b="$t_rollback" 'BEGIN{printf "%.1fs", b-a}')"
    fi
    echo "client-observed(k6, nginx 경유) failed requests: ${fail_count}"
    echo "--- 구간별/상태코드별 실패 (pre-switch=green 전환 전, monitor=관찰 구간, post-rollback=롤백 후) ---"
    cat "$out/failures-by-window.txt"
    echo "k6 summary         : ${sum}"
  } | tee "$out/summary.txt"

  log "결과 폴더: infra/${out}"
  LAST_RESULT_DIR="$out"   # run_scenario 안에서 tee/log/cat이 stdout을 계속 쓰므로,
                           # 경로는 $(...)로 캡처하지 않고 전역 변수로 직접 넘긴다.
}

RESULTS=()
for r in "${RATES[@]}"; do
  run_scenario "$r" "$MONITOR_SECONDS_ARG"
  RESULTS+=("$LAST_RESULT_DIR")
done

log "=== 전체 요약 ==="
for d in "${RESULTS[@]}"; do
  echo "--- $d ---"
  cat "$d/summary.txt"
done
