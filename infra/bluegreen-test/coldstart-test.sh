#!/usr/bin/env bash
# "서버에 아무 것도 안 떠 있는 최초 배포" 상황에서 실제 scripts/deploy-blue-green.sh
# (--no-recreate / --no-deps 적용된 현재 버전)를 그대로 돌려,
#   1) nginx가 정상 기동하는지
#   2) blue/green 두 슬롯이 실제로 어떤 순서로 뜨는지
# 를 로그로 남긴다.
#
# run.sh와 달리 시작 전에 전체 스택을 완전히 내려서(down -v) 진짜 "빈 서버" 상태를 재현한다.
# 사용법 (infra 폴더에서, Git Bash): bash bluegreen-test/coldstart-test.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."                      # infra/
OUT="bluegreen-test/result-coldstart-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
export DOCKER_USERNAME=vaptest
export IMAGE_TAG=v1
# 로컬 dev 스택(compose.yaml/compose.perf.yaml)과 컨테이너/볼륨/네트워크가 절대 겹치지
# 않도록 별도 프로젝트로 격리한다 (2026-09-30: 격리 안 했다가 dev용 perf-mysql 볼륨을
# 실수로 날린 적이 있음 - 그 이후로는 destructive한 down -v 전에 항상 먼저 확인받는다).
export COMPOSE_PROJECT_NAME=vap-bgtest
MERGED=.bgtest-isolate.yaml   # infra/ 최상위에 둬야 docker compose가 .env를 자동으로 찾는다
sed -E 's/container_name: vap-(mysql|redis|kafka|ai|backend-blue|backend-green|nginx)$/container_name: vap-bgtest-\1/' \
  compose-prod.yaml > "$MERGED"
C="docker compose -f $MERGED"
log(){ echo "[coldstart-test $(date +%T)] $*" | tee -a "$OUT/run.log"; }

timestamp_stream() {
  while IFS= read -r line; do
    printf '%s %s\n' "$(date +%s.%3N)" "$line"
  done
}

# 0) .env / 이미지 준비 (rollback-test.sh와 동일한 v1 이미지 재사용)
if [ ! -f .env ]; then
  sed 's/\r$//' .env.example \
    | sed 's/=change-me/=bgtest-pass/; s/^JWT_SECRET=.*/JWT_SECRET=bluegreen-test-secret-key-0123456789abcdef/' > .env
  log ".env를 테스트 값으로 생성했습니다."
fi
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

# 1) 진짜 "빈 서버" 재현: 볼륨까지 완전히 내린다
log "전체 스택 완전히 내림 (down -v) - 최초 배포 상황 재현"
$C down -t 5 -v >/dev/null 2>&1 || true
printf 'set $active backend-blue;\n' > nginx/conf.d/active.conf

# 2) 컨테이너 start 이벤트를 배포 동안 백그라운드로 수집 (순서 판정용)
DEPLOY_START_ISO=$(date -u +%Y-%m-%dT%H:%M:%S)
docker events --since "$DEPLOY_START_ISO" --filter 'event=start' \
  --format '{{.Time}} {{.Actor.Attributes.name}}' > "$OUT/docker-events.log" 2>&1 &
EVENTS_PID=$!
sleep 1   # docker events가 구독을 시작할 시간을 준다

# 3) 배포 실행 (실제 scripts/deploy-blue-green.sh, SKIP_PULL - 로컬 이미지만 사용)
log "최초 배포 실행 (아무 컨테이너도 없는 상태에서 시작)"
DEPLOY_START=$(date +%s%3N)
set +e
COMPOSE_FILE="$MERGED" DOCKER_USERNAME=vaptest IMAGE_TAG=v1 SKIP_PULL=true HEALTH_TIMEOUT_SECONDS=120 MONITOR_SECONDS=10 \
  bash scripts/deploy-blue-green.sh 2>&1 | timestamp_stream | tee "$OUT/deploy.log"
DEPLOY_RC=${PIPESTATUS[0]}
set -e
DEPLOY_END=$(date +%s%3N)
log "배포 종료 rc=${DEPLOY_RC} ($(( (DEPLOY_END-DEPLOY_START)/1000 ))s)"

sleep 2
kill "$EVENTS_PID" 2>/dev/null || true

# 4) nginx 기동 여부 확인
NGINX_STATUS=$(docker inspect -f '{{.State.Status}}' vap-bgtest-nginx 2>/dev/null || echo "not-found")
NGINX_HEALTH_HTTP=$(curl -fs -o /dev/null -w '%{http_code}' http://localhost/actuator/health 2>/dev/null || echo "curl-failed")
{
  echo "nginx container status : ${NGINX_STATUS}"
  echo "nginx proxy http status : ${NGINX_HEALTH_HTTP} (curl http://localhost/actuator/health)"
  echo "--- nginx 컨테이너 로그 (최근 40줄) ---"
  docker logs --tail 40 vap-bgtest-nginx 2>&1
} > "$OUT/nginx-status.txt"

# 5) blue/green/nginx 실제 시작 순서: docker events 로그 + StartedAt 둘 다 남긴다
{
  echo "--- docker events (event=start), 시간순 ---"
  grep -E 'vap-bgtest-(backend-blue|backend-green|nginx)\b' "$OUT/docker-events.log" || echo "(이벤트 없음 - 아래 StartedAt 참고)"
  echo
  echo "--- docker inspect StartedAt (배포 종료 후 조회) ---"
  for c in vap-bgtest-backend-blue vap-bgtest-backend-green vap-bgtest-nginx; do
    st=$(docker inspect -f '{{.State.StartedAt}}' "$c" 2>/dev/null || echo "not-found")
    echo "${c}: ${st}"
  done
} | tee "$OUT/slot-order.txt"

docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}' | grep vap-bgtest > "$OUT/containers-after.txt"

log "결과 폴더: infra/${OUT}"
echo "=================================================="
cat "$OUT/nginx-status.txt"
echo "=================================================="
cat "$OUT/slot-order.txt"
echo "=================================================="
