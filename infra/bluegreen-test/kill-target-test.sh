#!/usr/bin/env bash
# 전환 직후 대상(target=green) 슬롯을 docker kill해서, 지표 수집 실패를 감지해
# 즉시 롤백하는지 검증한다.
#   사용법 (infra 폴더에서, Git Bash): bash bluegreen-test/kill-target-test.sh [old|new]
#     new (기본): 현재 scripts/deploy-blue-green.sh (2026-09-30 수정판)
#     old       : bluegreen-test/deploy-blue-green.OLD.sh (수정 전 버전 - 죽은 슬롯을
#                 "트래픽 없음 -> 성공"으로 오판하던 버그 재현용)
set -euo pipefail
export MSYS_NO_PATHCONV=1

MODE="${1:-new}"
cd "$(dirname "$0")/.."
export DOCKER_USERNAME=vaptest
export IMAGE_TAG=v1
export COMPOSE_PROJECT_NAME=vap-bgtest
ISOLATED=.bgtest-isolate.yaml
sed -E 's/container_name: vap-(mysql|redis|kafka|ai|backend-blue|backend-green|nginx)$/container_name: vap-bgtest-\1/' \
  compose-prod.yaml > "$ISOLATED"
C="docker compose -f $ISOLATED"

OUT="bluegreen-test/result-killtarget-${MODE}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
log(){ echo "[kill-target-test $(date +%T)] $*"; }

if [ ! -f .env ]; then
  sed 's/\r$//' .env.example \
    | sed 's/=change-me/=bgtest-pass/; s/^JWT_SECRET=.*/JWT_SECRET=bluegreen-test-secret-key-0123456789abcdef/' > .env
fi
if [ "${SKIP_BUILD:-false}" != "true" ]; then
  if ! docker image inspect vaptest/vap-backend:v1 >/dev/null 2>&1; then
    (cd ../backend && ./gradlew -q clean build -x test)
    docker build -q -f Dockerfile -t vaptest/vap-backend:v1 ../backend >/dev/null
  fi
  if ! docker image inspect vaptest/vap-python:v1 >/dev/null 2>&1; then
    docker build -q -f ../ai/Dockerfile -t vaptest/vap-python:v1 ../ai >/dev/null
  fi
fi

log "기준 상태로 리셋 (v1, active=blue)"
$C down -t 5 >/dev/null 2>&1 || true
printf 'set $active backend-blue;\n' > nginx/conf.d/active.conf
$C up -d >/dev/null
for i in $(seq 1 90); do
  curl -fs http://localhost/actuator/health 2>/dev/null | grep -q '"status":"UP"' && break; sleep 2
done
curl -fs http://localhost/actuator/health | grep -q '"status":"UP"' || { log "기준 상태 기동 실패"; exit 1; }

DEPLOY_SCRIPT="scripts/deploy-blue-green.sh"
[ "$MODE" = "old" ] && DEPLOY_SCRIPT="bluegreen-test/deploy-blue-green.OLD.sh"

log "배포 시작 (${DEPLOY_SCRIPT}) - 백그라운드 실행, 전환 로그를 보면 green을 kill 합니다"
COMPOSE_FILE="$ISOLATED" MONITOR_SECONDS=30 HEALTH_TIMEOUT_SECONDS=60 SKIP_PULL=true SMOKE_ENABLED=true \
  bash "$DEPLOY_SCRIPT" > "$OUT/deploy.log" 2>&1 &
DEPLOY_PID=$!

SWITCHED=false
for i in $(seq 1 90); do
  if grep -q '트래픽을 backend-green로 전환했습니다' "$OUT/deploy.log" 2>/dev/null; then
    SWITCHED=true
    break
  fi
  sleep 1
done

if [ "$SWITCHED" != "true" ]; then
  log "전환 로그를 못 봤습니다 - 배포가 실패했거나 너무 오래 걸립니다."
  set +e; wait "$DEPLOY_PID"; set -e
  cat "$OUT/deploy.log"
  exit 1
fi

sleep 2   # base snapshot이 찍힐 시간을 준다
KILL_TIME=$(date +%s.%3N)
log "green 컨테이너를 kill 합니다 (t=${KILL_TIME})"
docker kill vap-bgtest-backend-green >/dev/null

set +e
wait "$DEPLOY_PID"
DEPLOY_RC=$?
set -e
log "배포 프로세스 종료 rc=${DEPLOY_RC}"

ACTIVE_AFTER=$(grep -oE 'backend-(blue|green)' nginx/conf.d/active.conf | head -1)
ROLLBACK_LOG_COUNT=$(grep -cE '즉시 롤백합니다|자동 롤백합니다' "$OUT/deploy.log" || true)

{
  echo "mode                  : ${MODE} (${DEPLOY_SCRIPT})"
  echo "kill_time             : ${KILL_TIME}"
  echo "deploy rc             : ${DEPLOY_RC}"
  echo "active.conf 최종값    : ${ACTIVE_AFTER}"
  echo "롤백 관련 로그 라인 수: ${ROLLBACK_LOG_COUNT}"
  if [ "$ACTIVE_AFTER" = "backend-blue" ] && [ "$ROLLBACK_LOG_COUNT" -gt 0 ]; then
    echo "판정: OK - blue로 롤백됨, 롤백 로그도 확인됨"
  else
    echo "판정: 버그 재현 - green이 죽었는데도 blue로 복구되지 않음 (또는 롤백 로그 없음)"
  fi
  echo "--- deploy.log ---"
  cat "$OUT/deploy.log"
} | tee "$OUT/summary.txt"

log "결과 폴더: infra/${OUT}"

# 정리: green을 다시 살려서 다음 테스트가 깨끗한 상태에서 시작하게 한다
$C up -d backend-green >/dev/null 2>&1 || true
