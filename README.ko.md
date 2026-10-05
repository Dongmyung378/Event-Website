[English](README.md) | [한국어](README.ko.md)

# EventLite — 행사·장소 관리 웹 서비스

행사와 장소를 등록·검색하고 장소별 일정을 확인하는 **Spring Boot 팀 프로젝트**입니다. 맨체스터대학교 **COMP23412 Software Engineering** 수업에서 제공한 EventLite 기본 코드를 바탕으로 **2025년 2월~5월**에 진행했습니다.

[GitHub 저장소](https://github.com/Dongmyung378/Event-Website) · [개인 포트폴리오의 프로젝트 소개](https://park-dongmyung.com/projects/project-10/)

**박동명은 테스트 케이스 작성, 보안 관련 구현, 지도 연결을 제외한 주요 기본 기능 구현에 참여했습니다.** 행사·장소 관리 등 기본 기능과 검증을 담당했으며, 지도 연결은 다른 팀원이 담당했습니다.

| 구분 | 내용 |
| --- | --- |
| 원래 프로젝트 | 2025년 2월~5월, 대학교 팀 과제 |
| 개인 기여 | 테스트 케이스 작성 · 보안 · 행사·장소 관리 등 주요 기본 기능 |
| 현재 저장소 | 팀 과제 결과와 이후 유지보수 변경을 함께 포함 |
| 유지보수 검증 | 2026-10-05, Java 212개·JavaScript 10개 테스트 통과 |

아래 검증 수치와 오류 수정은 **2026-10-05 유지보수 버전**의 결과입니다. 이를 2025년 팀 과제 당시의 검증 결과나 개인 단독 개발 성과로 소개하지 않습니다.

![EventLite 로컬 데모 홈](docs/images/eventlite-home.png)

[프로젝트 과정과 개인 기여](docs/PORTFOLIO.md) · [구조와 소스 코드 안내](docs/ARCHITECTURE.md) · [검증 기록](docs/verification.json)

## 주요 기능

- 행사·장소 목록, 상세 조회, 생성, 수정, 삭제 및 이름 검색
- 예정 행사와 지난 행사 구분, 장소별 행사 및 다음 3개 행사 조회
- 로그인과 `ADMINISTRATOR` 역할에 따른 관리 기능 접근 제어
- HTML 화면과 명시적인 읽기 전용 HAL JSON API
- 설정된 Mapbox 계정으로 장소 좌표 검색과 지도 표시
- 설정된 Mastodon 계정의 공개 게시물 표시 및 관리자 게시
- 외부 API 키 없이 실행할 수 있는 메모리 DB 기반 로컬 데모

## 기술

Java 17 · Spring Boot · Spring MVC · Spring Security · Spring Data JPA · Thymeleaf · H2 · Bootstrap · JUnit 5 · Mockito · MockMvc · WebTestClient · JaCoCo

MVC 화면과 읽기 전용 HAL API가 같은 서비스·JPA 저장소를 사용합니다. 생성·수정·삭제는 관리자 권한과 CSRF 검사를 적용한 HTML 관리 기능을 통해 수행합니다. 패키지별 역할과 설계 선택은 [구조 문서](docs/ARCHITECTURE.md)에 정리했습니다.

## 실행

필수: **JDK 17**, 최초 의존성 다운로드를 위한 인터넷 연결. Maven Wrapper가 포함되어 있어 Maven을 별도로 설치할 필요가 없습니다. JavaScript 회귀 테스트에는 **Node.js 22 이상**을 사용합니다.

Windows PowerShell:

```powershell
$env:JAVA_HOME = '설치한 JDK 17 경로'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

macOS / Linux:

```bash
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

실행 후 `http://localhost:8080/home`으로 접속합니다. 데모 로그인은 `demo-admin` / `demo-password`입니다. 이 계정은 **명시적으로 `demo` 프로필을 켰을 때만** 생성됩니다. 데모 DB는 종료하면 삭제되는 별도 메모리 DB이며, 기존 `db/` 파일을 변경하지 않습니다. 외부 서비스는 데모에서 기본적으로 꺼져 있습니다.

일반 프로필에는 기본 관리자 계정이 없습니다. 관리 기능을 쓰려면 `EVENTLITE_ADMIN_USERNAME`, `EVENTLITE_ADMIN_PASSWORD`를 환경 변수로 지정하십시오. 비밀번호는 BCrypt로 인코딩해 인증에 사용합니다. 기존 파일 DB를 열려면 해당 DB의 사용자 이름과 비밀번호를 환경 변수로 지정해야 합니다.

`.env.example`은 설정 예시이며 자동으로 읽히지 않습니다. DB·인증·외부 서비스 환경 변수와 Mapbox 설정은 [환경 설정 안내](docs/ARCHITECTURE.md#환경-설정)를 확인하십시오. 외부 서비스 계정이 없어도 로컬 데모의 기본 조회·관리 기능을 사용할 수 있습니다.

## 유지보수 버전 검증 — 2026-10-05

```powershell
.\mvnw.cmd verify
node --test src/test/javascript/event-maps.test.cjs
```

macOS / Linux에서는 `sh ./mvnw verify`를 사용합니다. 테스트는 `test` 프로필의 메모리 DB와 테스트 전용 인증 정보를 사용합니다.

| 검증 | 결과 |
| --- | --- |
| Java 테스트 | 212개 통과, 실패·오류·skip 0개 |
| JavaScript 테스트 | 10개 통과, 실패 0개 |
| JaCoCo Java 선 커버리지 | 95.24% |
| JaCoCo Java 분기 커버리지 | 72.68% |
| 브라우저 | 로그인·행사 생성 → 같은 ID 수정 → 삭제, JavaScript 오류 0개 |
| 화면 | PC 1440px·모바일 390px, 가로 넘침 없음 |

테스트는 별도 메모리 DB와 가짜 외부 응답을 사용했습니다. 실제 외부 계정의 API는 호출하지 않았습니다. Java 상세 결과는 `target/surefire-reports/`, 커버리지는 `target/site/jacoco/index.html`에 생성됩니다. 검증 범위와 한계는 [검증 및 회고](docs/PORTFOLIO.md#유지보수-검증)에, 기록은 [verification.json](docs/verification.json)에 있습니다.

GitHub Actions 설정이 포함되어 있습니다. 위 수치는 로컬 실행 기록이며 GitHub CI 실행 결과와는 구분합니다.

## 실행 화면

![모바일 행사 상세](docs/images/eventlite-details-mobile.png)

## 팀 과제 이후 개선

권한 검사 누락, 적은 장소 수, 날짜 경계, 지도 팝업의 HTML 삽입, 주소 변경 후 남는 좌표, 외부 게시 실패 표시를 수정하고 회귀 테스트를 추가했습니다. 변경 조건과 결과는 [프로젝트 회고](docs/PORTFOLIO.md#유지보수에서-개선한-조건)에 정리했습니다.

## 공개 범위와 남은 운영 작업

이 프로젝트는 팀 과제와 이후 유지보수 결과를 보여주는 포트폴리오 자료입니다. 외부 계정의 실제 게시·지도 응답은 자격 증명을 설정한 환경에서 별도 확인해야 합니다. 과거 소스의 토큰은 해당 서비스에서 폐기·교체해야 하며, 현재 소스에서 제거했다고 기존 Git 이력까지 사라지는 것은 아닙니다. 공개용 배포 자료에는 Git 이력, DB 파일, 개인 환경 파일을 포함하지 않습니다.

공개 서비스 운영에는 TLS, 운영용 DB·백업, 관리자 계정 수명 관리, 트래픽 제한, 의존성 업데이트를 운영 환경에 맞춰 추가해야 합니다.

## 출처와 라이선스

기본 코드: COMP23412 EventLite, University of Manchester. Robert Haines, Markel Vigo, Mustafa Mustafa, Tom Carroll, Caroline Jay가 제공한 기본 프로젝트에서 출발했습니다. 원래 저작권과 BSD 라이선스를 유지합니다. [라이선스 원문](LICENSE)을 확인하십시오. 과목 제공 기본 코드 자체를 개인 구현으로 주장하지 않습니다.
