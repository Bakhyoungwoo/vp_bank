# VAP production EC2

2026-09-30 기준 이 계정/리전에는 EC2 인스턴스가 없다 (아래 "2026-09-28 setup" 참고). 즉 지금
`terraform apply`는 "기존 인스턴스를 안 건드리고 교체본을 만드는" 게 아니라 **처음부터 새로
만드는 것**이다. 이 구성이 만드는 것:

- Ubuntu 24.04 EC2 (`t3.medium`, 기본값) — Spring 2벌(blue/green) + Kafka + MySQL + AI 서버
  (KR-SBERT 모델 포함)를 동시에 띄우기엔 `t3.micro`(1GB)로는 절대 부족하다. 반드시
  `t3.medium`(4GB) 이상으로 띄울 것.
- 스왑 4GB (`user_data`에서 부팅 시 생성) — `t3.medium`도 blue-green 전환 순간(새 슬롯이 뜨는
  동안 기존 슬롯도 같이 떠 있음)엔 빠듯해서, OOM으로 헬스체크가 실패하는 걸 막기 위한 안전장치다.
- Elastic IP
- SSH는 관리자 IP(`admin_cidr`)에서만, HTTP(80)는 전체 공개. **443은 열지 않는다** — TLS 설정이
  없는 상태에서 포트만 열어봤자 쓸 곳이 없다.
- IMDSv2 강제 (`metadata_options.http_tokens = "required"`)
- Docker Engine과 Compose 플러그인 설치

Run from this directory after authenticating the AWS CLI:

```powershell
terraform init
terraform apply
```

`user_data`는 부팅 후 비동기로 실행된다. **apply 직후 바로 배포 워크플로를 돌리지 말 것** —
Docker 설치 스크립트와 `deploy.yml`의 "Ensure Docker is installed" 단계가 동시에 `apt` 락을
잡으려 하면 충돌한다. 인스턴스에 SSH로 접속해 `cloud-init status --wait`가 끝난 뒤에 배포하라.

새 서버가 응답하면 `~/.env`를 만들고(자동화 안 됨, 직접 scp), GitHub Actions의 `SERVER_HOST`와
`EC2_SECURITY_GROUP_ID`를 갱신한 뒤 배포 워크플로를 다시 돌린다. 전체 순서는 아래 "EC2 검증
체크리스트" 참고.

Do not commit a real `terraform.tfvars` file or any production secrets.

## Notes from the 2026-09-28 setup

- The instance created that day was launched manually from the AWS console rather than via this
  `terraform apply`, so its security group was named `launch-wizard-1` (the default the console
  gives it), not `vap-production-terraform` from `main.tf`. `EC2_SECURITY_GROUP_ID` was pointed at
  that console-created group. The instance was later terminated (verified 2026-09-30: no EC2
  instances exist in this account/region), and the group was deleted the same day since nothing
  referenced it any more.
- **Whenever the instance is recreated (via `terraform apply` or the console), update both
  `SERVER_HOST` and `EC2_SECURITY_GROUP_ID` in GitHub Actions secrets to the new instance's values
  before rerunning `deploy.yml`** — `main.tf` creates its own `vap-production-terraform` security
  group, so the ID will differ from any previous manually-created one.
- Since there's currently no instance, `deploy.yml`'s `deploy` job is gated behind the
  `DEPLOY_ENABLED` repo variable (Settings → Secrets and variables → Actions → Variables) so
  `build-spring`/`build-python` keep running on push without the SSH step failing every time.
  **Once the instance is recreated and `SERVER_HOST`/`EC2_SECURITY_GROUP_ID` are updated, also
  set the `DEPLOY_ENABLED` repo variable to `true`** to re-enable the deploy job.

## EC2 검증 체크리스트

실제 AWS 자격증명, SSH 키(`vap-production.pem`), GitHub repo 관리 권한이 필요한 단계는 전부
**본인**으로 표시했다 — 이 레포의 자동화(Claude Code 포함)가 대신 실행할 수 없다.

| 순서 | 단계 | 담당 |
|---|---|---|
| 1 | `terraform.tfvars` 작성 (`admin_cidr`를 현재 공인 IP로) | **본인** — 비밀값이라 커밋 안 하고 로컬에서만 |
| 2 | `terraform apply` | **본인** — 과금 발생하는 실제 리소스 생성, 명시적 승인 필요 |
| 3 | SSH 접속 후 `cloud-init status --wait` 확인 | **본인** — pem 키로 직접 접속 |
| 4 | 서버 `~/.env` 업로드 (scp) | **본인** — 자동화 안 됨, 비밀값 직접 전달 |
| 5 | GitHub Secrets `SERVER_HOST`, `EC2_SECURITY_GROUP_ID` 갱신 | **본인** — repo 설정 권한 필요 |
| 6 | repo variable `DEPLOY_ENABLED=true` | **본인** — repo 설정 권한 필요 |
| 7 | 배포 (master에 push) | 맡길 수 있음 — 위 단계가 끝나면 `deploy.yml`이 push 시 자동 실행 |
| 8 | SSH로 `docker stats`를 띄워 메모리 사용량 확인 (특히 blue-green 전환 순간) | **본인** — 실서버 SSH 필요 |
| 9 | `infra/bluegreen-test`의 k6 부하 스크립트로 트래픽을 주는 동안 재배포 실행 | **본인** — 실서버 대상 부하 테스트라 직접 관찰 필요 |
| 10 | (선택) 의도적으로 헬스체크 실패를 유발해 자동 롤백 확인 | **본인** |
| 11 | 확인 끝나면 `terraform destroy` | **본인** — apply한 PC에만 `terraform.tfstate`가 있으므로 그 PC에서 실행. 과금 중단용 파괴 작업, 명시적 승인 필요 |
| 12 | repo variable `DEPLOY_ENABLED`를 다시 `false`로 | **본인** — 인스턴스가 없는 동안 배포 job이 매번 SSH 실패로 안 터지게 |
