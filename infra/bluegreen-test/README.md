# bluegreen-test

`infra/scripts/deploy-blue-green.sh`를 로컬 Docker에서 검증하기 위한 하네스. 전부 별도 Compose
프로젝트(`COMPOSE_PROJECT_NAME=vap-bgtest`)와 접두사가 붙은 컨테이너 이름(`vap-bgtest-*`)으로
격리되어 있어서, 같은 `infra/` 디렉터리에서 돌아가는 다른 dev 스택(`compose.yaml` +
`compose.perf.yaml` 등)의 컨테이너·볼륨·네트워크를 건드리지 않는다.

모든 스크립트는 `infra/` 에서, Git Bash로 실행한다. 최초 실행 시 `../backend`를 gradle로
빌드하고 `vaptest/vap-backend:v1`, `vaptest/vap-python:v1` 이미지를 만든다(이미 있으면
스킵 - `SKIP_BUILD=true`로 강제 스킵 가능).

**주의**: 어떤 스크립트든 `down -v`(볼륨 삭제)를 실행하는 건 `coldstart-test.sh` 하나뿐이고,
그 볼륨도 `vap-bgtest_*`로 격리돼 있다. 그래도 이 프로젝트 바깥의 볼륨을 지우는 명령은 항상
먼저 확인 후 실행한다 (2026-09-30: 격리 전에 `down -v`로 dev용 perf-mysql 볼륨을 실수로 지운 적
있음).

## 스크립트

### `run.sh [before|after] [MONITOR_SECONDS]`
v1 이미지로 두 슬롯을 띄운 뒤 v2로 "배포"하면서 k6로 부하를 걸어, `before`(기본 -
`deploy-blue-green.PRE-NORECREATE.sh`, `--no-recreate`/`--no-deps` 적용 전인 commit
5f2d067 시점 스크립트)와 `after`(현재 `scripts/deploy-blue-green.sh`)를 비교한다. 활성
슬롯(blue) 컨테이너 ID를 배포 전후로 비교해서 "재생성됐는지"를 판정한다.

```
SKIP_BUILD=true bash bluegreen-test/run.sh before 60
SKIP_BUILD=true bash bluegreen-test/run.sh after 60
```

측정 조건: `HEALTH_TIMEOUT_SECONDS=180`, k6 `VUS=10`(env로 조절 가능), `MONITOR_SECONDS`는
두 번째 인자(기본 30).

### `coldstart-test.sh`
"서버에 아무 것도 안 떠 있는 최초 배포" 상황을 `down -v`로 재현한 뒤 현재
`scripts/deploy-blue-green.sh`를 그대로 돌려서, nginx가 정상 기동하는지와 blue/green이
실제로 어떤 순서로 뜨는지(`docker events` + `docker inspect StartedAt`)를 기록한다.

```
SKIP_BUILD=true bash bluegreen-test/coldstart-test.sh
```

**알려진 문제**: 진짜 빈 DB(볼륨까지 지운 뒤)에서는 blue/green이 동시에 뜨면서 두 인스턴스가
동시에 Liquibase 마이그레이션을 실행해, 한쪽이 `DATABASECHANGELOG` 테이블 생성 경합에서 져서
크래시할 수 있다(타이밍에 따라 재현/비재현 - 2회 실행 중 1회 재현됨). `deploy-blue-green.sh`나
`--no-recreate`/`--no-deps`와는 무관한 별개 결함이며 아직 안 고쳤다.

### `rollback-test.sh [--monitor-seconds N] [--no-k6] <rate...>`
`backend/.../config/FaultInjectionConfig.java`(`fault.inject.rate` 프로퍼티, 운영에는 항상
비활성)를 이용해 green 슬롯에만 지정한 비율로 500을 주입하고, 실제로 자동 롤백이 발동하는지
검증한다. `compose-prod.yaml`은 건드리지 않고 `compose.fault.yaml` 오버레이로만 적용한다.

```
SKIP_BUILD=true bash bluegreen-test/rollback-test.sh --monitor-seconds 60 0.5 0.03
SKIP_BUILD=true bash bluegreen-test/rollback-test.sh --no-k6 --monitor-seconds 60 0.5   # k6 없이 스모크 트래픽만으로 검증
```

- `--monitor-seconds N`: `deploy-blue-green.sh`의 `MONITOR_SECONDS`에 그대로 전달 (기본 30).
- `--no-k6`: k6 부하 컨테이너를 띄우지 않고, `deploy-blue-green.sh` 자체 스모크 트래픽
  (`SMOKE_ENABLED`, 기본 켜짐)만으로 판정이 되는지 본다.
- rate: `FAULT_INJECT_RATE` 값. 0.05(`ERROR_RATE_THRESHOLD`) 초과면 롤백, 이하면 유지가 기대값.
- 이 스크립트는 fault 주입 자체를 검증하는 게 목적이라, `deploy-blue-green.sh`를 호출할 때
  `SMOKE_URLS=/` 로 강제 오버라이드한다 (기본값 `/actuator/health`는
  `FaultInjectionConfig`가 `/actuator/**`를 제외하므로 fault의 영향을 받지 않는다 - 운영
  스모크 기본값 자체는 문제없이 집계되고, 이건 fault 주입을 "맞아야" 하는 이 테스트만의 사정).
- 결과에 `client-observed(k6, nginx 경유) failed requests`를 pre-switch/monitor/post-rollback
  3구간 + 상태코드별로 나눈 `failures-by-window.txt`가 같이 생성된다.

### `kill-target-test.sh [old|new]`
전환 직후 대상(green) 컨테이너를 `docker kill`해서, 지표 수집 자체가 실패하는 상황(컨테이너
사망)을 감지해 롤백하는지 검증한다. `new`(기본)는 현재 `scripts/deploy-blue-green.sh`,
`old`는 2026-09-30 수정 이전 버전(`deploy-blue-green.OLD.sh`, 비교용으로만 보관 - 실제
배포에는 쓰지 않음)을 쓴다.

```
SKIP_BUILD=true bash bluegreen-test/kill-target-test.sh new
SKIP_BUILD=true bash bluegreen-test/kill-target-test.sh old
```

## deploy-blue-green.sh 측정/판정 조건 (기본값)

| 변수 | 기본값 | 의미 |
|---|---|---|
| `MONITOR_SECONDS` | 60 | 전환 후 관찰 총 시간 |
| `POLL_SECONDS` | 5 | 관찰 중 폴링 간격 |
| `EARLY_ABORT_THRESHOLD` | 0.20 | 폴링 중 누적 비율이 이걸 넘으면 즉시 롤백 |
| `ERROR_RATE_THRESHOLD` | 0.05 | 관찰 종료 시점 최종 판정 기준 |
| `MIN_SAMPLE` | 20 | 이 요청 수 이상이어야 비율을 신뢰 (조기중단/최종판정 공통) |
| `SMOKE_ENABLED` | true | 자체 스모크 트래픽 on/off |
| `SMOKE_URLS` | `/actuator/health` | 스모크 대상 경로(공백으로 여러 개). 인증 불필요, DB/Redis HealthIndicator를 거침 |
| `SMOKE_RPS` | 2 | 스모크 요청 속도 |

지표 수집(`snapshot_counts`)이 연속 2회 실패하거나, 누적 카운터가 이전 폴링보다 줄어들면
(컨테이너 재시작/리셋 추정) 비율 계산 없이 즉시 롤백한다. 관찰 종료 시 표본이
`MIN_SAMPLE` 미만이면 "판정 불가"로 대상 슬롯에 그대로 유지하고 `exit 2`를 반환한다
(0=성공, 1=롤백, 2=판정 불가). 집계 시 `/actuator/prometheus`(이 스크립트 자신의 조회)만
제외하고, `/actuator/health`(스모크 기본 대상)는 그대로 포함한다.

**관찰 기간 요청 수 = 스모크 + 크롤러 콜백 등 실제 내부 트래픽 합계다.** `/actuator/prometheus`
외에는 아무것도 안 걸러내므로, AI 크롤러가 nginx를 거쳐 보내는 `POST /api/internal/news` 같은
실제 서비스 트래픽도 같이 잡힌다(의도된 동작 - 합성 스모크만이 아니라 실제 트래픽 건강도를
반영한다). 그래서 "관찰 기간 요청 N건"을 스모크 요청 수(`SMOKE_RPS`×시간)로 역산하면 안 맞을
수 있다.

### 인증 없는 뉴스 조회 GET API가 없다

`/api/news/**`, `/api/market/**`, `/api/stocks/**` 등 실질적인 조회 API는 전부
`SecurityConfig`에서 `.authenticated()`로 막혀 있다(`/api/news/recommend`도 마찬가지 -
비로그인 허용인 것처럼 적힌 주석이 실제와 다름, `SecurityConfig.java`에 TODO로 남겨둠).
인증 없이 DB/Redis를 거치는 경로는 `/actuator/health`(Spring Boot `HealthIndicator`가
DataSource/Redis를 확인)가 유일해서 이걸 기본값으로 쓴다.

더 깊은(실제 비즈니스 API) 스모크 검증이 필요하면 전용 계정(예: signup으로 미리 만들어 둔
`deploy-smoke@internal.local`)의 JWT를 배포 전에 발급해 `curl -H "Authorization: Bearer
$TOKEN"`으로 감싸는 wrapper를 만들거나, `SMOKE_URLS`를 그런 wrapper 스크립트 경로로 바꾸는
식으로 확장하는 걸 권장한다 (현재는 구현돼 있지 않음 - `deploy-blue-green.sh`는 로그인
흐름을 갖고 있지 않다).

## 최종 검증 결과 (2026-09-30, MONITOR_SECONDS=60 통일)

| 시나리오 | before (`--no-recreate` 적용 전) | after (적용 후) |
|---|---|---|
| 활성 슬롯(blue) 재생성 | **예** (컨테이너 ID 변경) | **아니오** (ID 동일) |
| 배포 중 k6 실패 요청 | 3332건, 전부 502, +11.0s~+47.8s(36.8초) | 0~1건 |

`after`의 "0~1건" 중 1건(`result-after-20260930-210457`)을 확인해보면, k6 자신이 보고한
`http_req_failed` 카운트일 뿐 실제 HTTP 실패가 아니었다. `k6.err`엔 우리 커스텀 `FAIL <ms>
<status>` 로그가 전혀 없고, 유일한 줄은 `level=error msg="test run was aborted because k6
received a 'interrupt' signal"` — `run.sh`가 배포 종료 15초 뒤 k6에 `SIGINT`를 보내 정상
종료시킬 때, 그 순간 날아가던 요청 1건이 "중단됨"으로 집계된 것이다. 특정 시점/상태코드가
없는 건 애초에 완료된 HTTP 응답이 아니기 때문이다(테스트 하네스 종료 타이밍의 부산물).

| rate=0.5 | 결과 |
|---|---|
| k6 부하 중 롤백 | 9.0초, 실패 329건(전부 500·monitor 구간) |
| 스모크 전용(`--no-k6`) 롤백 | 20.7초, 관측 비율 0.5342 (이 시나리오는 fault 주입 자체를 검증해야 해서 `SMOKE_URLS=/`로 오버라이드함 - `/actuator/health`는 fault injection 대상에서 제외됨) |

**운영 기본값 그대로**(fault 없음, k6 없음, `SMOKE_URLS=/actuator/health`, `SMOKE_RPS=2`,
`MONITOR_SECONDS=60`) 배포 1회: `exit 0`, 관찰 기간 요청 **339건**(0건 5xx) — `MIN_SAMPLE=20`을
가뿐히 넘겨서 "판정 불가"(exit 2) 없이 정상 성공 판정됨. 조정 불필요.

| rate=0.03 (대조군) × 5회 | 결과 |
|---|---|
| 오탐 롤백 | 0/5 |
| 관측 비율 범위 | 2.82% ~ 3.26% (전부 5% 미만) |

| docker kill (전환 직후 green 강제종료) | new | old |
|---|---|---|
| 판정 | 지표 수집 연속 2회 실패 → 즉시 롤백 (~10초) | "트래픽 없음 → 성공"으로 오판 |
| `active.conf` | `backend-blue`로 복구 | 죽은 `backend-green`에 그대로 남음 |
| exit code | 1 | 0 |

| 콜드스타트 (최초 배포) | 결과 |
|---|---|
| nginx 기동 | 정상 |
| blue/green 시작 순서 | `StartedAt` 차이 1.7~2ms — 사실상 동시(nginx의 `depends_on`이 둘 다 끌어옴). `--no-deps`는 최초 배포에 관찰 가능한 영향 없음 |
| Liquibase 동시 마이그레이션 | 2회 중 1회, 한쪽 인스턴스가 테이블 생성 경합에서 져서 크래시 (알려진 문제, 미수정) |

측정 환경: 로컬 Docker Desktop, `vaptest/vap-backend:v1`/`vaptest/vap-python:v1`(gradle 빌드
이미지). 결과 폴더(`result-*/`)와 병합 compose 파일(`.bgtest-*.yaml`)은 git에는 안 올라간다
(`.gitignore`).
