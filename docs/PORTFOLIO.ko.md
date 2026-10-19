[English](PORTFOLIO.md) | [한국어](PORTFOLIO.ko.md)

# EventLite — 프로젝트 과정과 개인 기여

EventLite는 행사와 장소, 일정, 공개 조회, 관리자 변경을 다루는 웹 서비스입니다. 팀의 구현 결과 위에 조회 비용, 데이터 일관성, 장애 대응, 재현 가능한 검증을 보강했습니다. [실행 안내](../README.ko.md)와 [구조 문서](ARCHITECTURE.ko.md)를 함께 제공합니다.

[GitHub 저장소](https://github.com/Dongmyung378/Event-Website) | [포트폴리오 소개](https://park-dongmyung.com/projects/project-10/)

## 진행 시기와 기여 구분

| 단계 | 기간 또는 근거 | 범위 |
| --- | --- | --- |
| 원래 팀 프로젝트 | 2025년 2월부터 5월 | 맨체스터대학교 COMP23412 Software Engineering에서 제공한 EventLite 기본 코드 위에 서비스 구현 |
| 이전 유지보수 | 2026-10-05 검증, 비교 리비전 `a18ca3d` | 권한과 입력 오류 수정, 외부 연동 설정 분리, 지도 회귀 검증, 초기 포트폴리오 문서 |
| 이후 강화 | 2026년 10월 검증 | DB 조회 범위 제한, 일정 일관성, 동시 수정, 피드 캐시, 복구 도구, 검증 범위 확대 |

이전 [verification.json](verification.json)은 Java 212개, JavaScript 10개, 선 커버리지 95.24%, 분기 커버리지 72.68%의 기록으로 유지합니다. [2026-10-15 검증](reports/verification.json)과 [2026-10-19 로컬 회귀검사 결과](reports/ci-repair-verification.json)를 별도로 보관하며, 이후의 측정값을 2025년 과제 당시 성과로 소급하지 않습니다.

**박동명은 팀 프로젝트에서 테스트 케이스 작성, 보안 구현, 주요 관리 기능에 참여했습니다.** 행사와 장소 관리 등 기본 기능의 구현과 검증을 맡았으며, 최초 지도 연결은 다른 팀원이 담당했습니다. 전체 서비스는 팀의 결과이고, 이후 유지보수를 근거로 모든 소스나 추가 테스트를 개인의 원래 기여로 소개하지 않습니다.

제공된 과목 기본 코드의 원저작자는 Robert Haines, Markel Vigo, Mustafa Mustafa, Tom Carroll, Caroline Jay이며, 원래 저작권과 BSD 조건을 [LICENSE](../LICENSE)에 유지합니다.

## 문제와 구현 선택

서비스는 이미 기본 CRUD를 지원했습니다. 이번 강화에서는 데이터 증가, 두 관리자의 동시 수정, 외부 피드 실패, 복원한 DB로의 재시작을 다뤘습니다. 원래 입력 필드, 경로, 보안 정책, 지도 기능, 기본 읽기 전용 API 응답은 유지했습니다.

| 영역 | 초기 상태 | 개선과 근거 |
| --- | --- | --- |
| 일정 일관성 | HTML과 API가 다음 행사 정렬과 필터를 별도로 구현 | 공통 조회 서비스, 주입 가능한 시계, ID 동률 처리, 날짜와 시간대 경계 테스트 |
| 조회 비용 | 다음 행사와 인기 장소를 계산할 때 전체 데이터를 로드할 수 있음 | DB 조건 검색과 제한, 관계 집계, 선택형 페이지 처리, SQL과 엔티티 로드 수 측정 |
| 동시 변경 | 폼을 연 뒤 변경된 정보를 이전 폼이 덮어쓸 수 있음 | 원본 버전 전송, 트랜잭션 검사, JPA 낙관적 잠금, 409 안내, 실제 동시 수정 검증 |
| 관계 변경 | 장소 삭제와 행사 등록이 경합할 수 있음 | 같은 장소 행 잠금, 참조 검사와 외래 키 유지 |
| 외부 장애 | 반복 방문할 때 느린 피드 요청이 반복될 수 있음 | 정상 응답 캐시, 제한된 이전 결과 재사용, 갱신 중복 방지, 실패 후 대기, 지연 전송 모의 측정 |
| 실행과 복구 | 빌드 성공만으로 실제 복구 흐름을 확인하기 어려움 | 브라우저 CRUD와 충돌 검증, 파일 DB 재시작, 정상 종료, 오프라인 스냅샷, 복원 레코드 비교, 두 운영체제 CI 구성 |

실제 브라우저 검증에서는 메서드 필터 순서도 확인했습니다. 정상 관리자와 CSRF를 사용하는 장소 삭제 폼이 숨김 `delete` 메서드 변환 전에 거절되는 문제를 수정해, 메서드 변환을 Security보다 먼저 실행합니다. 실제 서블릿 서버 테스트로 관리자 삭제와 함께 익명 접근, 일반 사용자, CSRF 누락, API 쓰기, 지원하지 않는 메서드의 차단도 검증합니다.

## 수치로 확인한 결과

| 검증 | 2026-10-19 Windows 로컬 결과 |
| --- | --- |
| Windows Java 테스트 | 278개 통과, 실패와 오류 및 건너뜀 0개 |
| JavaScript 지도와 스냅샷 테스트 | 15개 통과 |
| 브라우저 실행 회귀검사 | [2개 통과](reports/browser-launch-verification.json) |
| Java 줄 커버리지 | 95.31% (691 / 725) |
| Java 분기 커버리지 | 76.32% (232 / 304) |
| Maven 커버리지 기준 | 줄 90%, 분기 70% 이상 통과 |
| 초기 DB 업그레이드를 포함한 브라우저 검증 | 22개 통과, 실패 0개 |

H2 테스트 DB의 Spring 컨텍스트 격리를 확인하는 회귀검사 2개를 추가했습니다. 다른 컨텍스트를 열어도 기존 행이 유지되고, 다른 컨텍스트를 닫아도 사용 중인 스키마와 행이 유지되는지 검사합니다. `test` 프로필은 컨텍스트마다 별도 메모리 DB를 사용합니다.

[2026-10-19 로컬 검증 기록](reports/ci-repair-verification.json)에 위 JDK 17 결과와 보존된 이전 JAR를 사용한 브라우저 검증을 담았습니다. 이전 Windows와 Linux의 Java 276개 결과는 [2026-10-15 기록](reports/verification.json)에 보존합니다. GitHub Actions는 새 DB로 공통 브라우저 검사 21개를 실행하며, 선택 인자 `--seed-jar`를 지정하면 이전 DB 업그레이드 검사가 추가됩니다.

행사 100,000개와 장소 100개 조건의 결과입니다.

| 작업 | 초기 SQL 수 | 현재 SQL 수 | 초기 엔티티 로드 수 | 현재 엔티티 로드 수 |
| --- | ---: | ---: | ---: | ---: |
| 다음 행사 3개 | 101 | 1 | 100,100 | 6 |
| 인기 장소 3개 | 101 | 1 | 100,100 | 3 |
| 기존 행사 전체 목록 | 101 | 101 | 100,100 | 100,100 |

행사 1,000개, 10,000개, 100,000개에서 4개 작업을 비교해 총 12개 조건을 확인했으며, 결과 SHA-256은 모두 일치했습니다. 데이터가 적을 때 장소별 다음 행사 조회 시간이 소폭 늘어난 조건 등 개선이 없었던 결과도 포함합니다. 작은 요청이 모두 빨라졌다고 해석하는 대신, DB가 처리하고 로드하는 범위를 줄인 근거로 설명합니다.

[비교 보고서](reports/query-comparison.json), [초기 조회 측정](reports/initial-query-benchmark.json), [현재 조회 측정](reports/final-query-benchmark.json)에 원시 샘플과 결과 서명을 보존합니다. H2 메모리 DB와 장소 100개를 사용하고, 조건마다 워밍업 5회와 측정 21회를 실행하며 각 샘플은 새로운 읽기 트랜잭션과 영속성 컨텍스트를 사용합니다. 중앙값과 p95는 순차적인 서비스 조회 시간이며 HTTP 트래픽이나 운영 지연 시간이 아닙니다. 앱 코드와 실행 기반이 함께 바뀐 비교이므로 시간 차이를 단일 코드 수정의 효과로 해석하지 않습니다.

피드 측정은 실제 `MastodonService`에 일정한 지연을 주는 가짜 전송 계층을 연결합니다. 실제 네트워크 없이 최초 조회와 반복 조회, 외부 전송 횟수를 구분해 기록합니다. 지정한 지연은 실험 조건이며 실제 Mastodon 서버의 측정 지연이 아닙니다.

전체 목록 경로는 조회 구조를 유지했습니다. 행사 10만 개의 중앙값은 204.146 → 203.760ms, p95는 231.944 → 237.591ms였습니다. 1만 개 조건 등 오히려 느려진 결과도 있으며 이 경로를 최적화했다고 주장하지 않습니다.

## 비교 재현

현재 저장소 루트에서 실행합니다. 일반 검증은 [README의 재현 명령](../README.ko.md#검증-재현)을 사용합니다. 비교 스크립트는 동일한 데이터와 측정 코드로 다른 체크아웃도 실행할 수 있습니다. 초기 결과를 다시 측정하려면 `a18ca3d` 리비전의 별도 체크아웃이 필요합니다. 소스 ZIP으로는 현재 버전을 실행하고 함께 제공한 이전 보고서를 확인할 수 있습니다.

초기 체크아웃이 `../eventlite-initial`에 있을 때 Windows PowerShell:

```powershell
$env:EVENTLITE_BENCHMARK_PROJECT = (Resolve-Path ../eventlite-initial).Path
node scripts/query-benchmark.mjs target/benchmark/initial-query.json initial 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/initial-timeline.json initial 150
Remove-Item Env:EVENTLITE_BENCHMARK_PROJECT
node scripts/query-benchmark.mjs target/benchmark/current-query.json current 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/current-timeline.json current 150
```

macOS / Linux:

```bash
EVENTLITE_BENCHMARK_PROJECT=../eventlite-initial node scripts/query-benchmark.mjs target/benchmark/initial-query.json initial 1000,10000,100000
EVENTLITE_BENCHMARK_PROJECT=../eventlite-initial node scripts/timeline-benchmark.mjs target/benchmark/initial-timeline.json initial 150
node scripts/query-benchmark.mjs target/benchmark/current-query.json current 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/current-timeline.json current 150
```

같은 컴퓨터에서 다른 부하를 줄인 상태로 실행하고 전체 샘플을 보존합니다. 조회 측정이 대상 체크아웃의 런타임 클래스패스를 준비하므로 먼저 실행해야 합니다. 측정 데이터는 실행일의 다음 날짜를 기준으로 생성하므로 과거 보고서의 절대 날짜 SHA를 그대로 재현하려면 당시 기준 날짜도 맞춰야 합니다. 같은 날 두 리비전을 비교하면 새로 생성한 동일 조건 데이터의 결과를 대조할 수 있습니다.

실제 초기 버전 파일 DB의 업그레이드와 복구를 확인하려면 먼저 해당 버전의 실행 JAR를 빌드한 뒤 실행합니다.

```text
node scripts/verify-browser.mjs --seed-jar <초기-버전-실행-JAR> --output target/browser-verification
```

검증기는 분리된 파일 DB를 만듭니다. 초기 논리 레코드 보존, 새 버전 열의 null을 0으로 채우는 동작, 업그레이드한 행사와 장소의 수정 가능 여부를 확인합니다. 이어서 재시작, 체크섬을 확인하는 오프라인 백업, 새 DB로의 복원, 시작 후 레코드 일치, 로그인과 삭제를 검증합니다. 실제 외부 계정을 복사하거나 게시하지 않습니다.

## 검증 범위와 회고

이 프로젝트는 API 계약 유지, DB 조회 분석, 트랜잭션 경계, 동시성 충돌 처리, 브라우저 자동 검증, 로컬 데이터 복구를 실제 코드와 기록으로 보여줍니다. 개선이 없거나 실패한 측정도 남겨 구현 선택을 검토할 수 있게 했습니다.

커버리지는 실행한 코드의 비율이며 전체 기능의 정확성을 보장하지 않습니다. 로컬 검증과 별도 환경 검증은 GitHub Actions 실행과 구분합니다. 실제 Mapbox와 Mastodon 계정 응답, 운영 트래픽, 운영 DB 복구는 통제된 테스트 범위 밖입니다. Boot 4.0.8에서도 JSON 호환성을 위해 폐기 예정인 Jackson 2 연결 모듈을 사용하며, Jackson 3 전면 전환은 이후 유지보수 과제입니다.

초기 코드와 과거 결과를 보존하고, 소스 배포 자료에서는 개인 일지, DB 파일, 자격 증명, Git 메타데이터를 제외합니다. 원래 팀 기여와 이후 유지보수, 공개 재현 근거를 구분해 설명합니다.
