# BPA Project

브라우저에서 문서를 업로드하면 AI가 문서 내용을 분석해 실행 가능한 업무계획 초안을 생성하고, 사용자가 결과를 검토·수정할 수 있도록 만든 Spring Boot 기반 웹 애플리케이션입니다.

## 프로젝트 개요

일반 사무 환경에서는 회의자료, 보고서, 제안서처럼 비정형 문서를 빠르게 읽고 다음 업무로 연결해야 하는 경우가 많습니다. 이 프로젝트는 문서 내용을 바탕으로 다음과 같은 정보를 구조화하는 데 초점을 둡니다.

- 문서 요약
- 목표
- 우선순위 작업
- 일정 초안
- 리스크
- 확인 필요 사항

단순 요약이 아니라, 사용자가 바로 검토하고 수정할 수 있는 업무계획 초안을 제공하는 것이 목표입니다.

## 주요 기능

- 문서 업로드 화면 제공
- 문서 유형 선택 지원
  - 회의자료
  - 보고서
  - 제안서
- PDF, PPT, PPTX 업로드 지원
- PDFBox, Apache POI 기반 본문 텍스트 추출
- 표, 제목, 시각 자료 메타데이터 분리 추출
- 이미지·차트가 있는 페이지/슬라이드에 대한 OCR·시각 해석 보조 입력 지원
- OpenAI Responses API 기반 단계별 문서 분석
- Structured Outputs(JSON Schema) 기반 결과 생성
- 업로드 후 비동기 분석 처리
- 분석 상태 조회 화면 제공
  - PENDING
  - PROCESSING
  - COMPLETED
  - FAILED
- `max_output_tokens` 초과 시 단계별 재시도 처리
  - 청크 분석 단계 재시도
  - 요약 압축 단계 재시도
  - 최종 구조화 단계 재시도
- 결과 화면에서 직접 수정 및 재저장 지원
- MySQL 기반 분석 결과 영속 저장
- 저장된 분석 결과 목록 조회 및 선택 삭제/전체 삭제 기능

## 현재 구현 범위

현재 프로젝트는 다음 흐름을 지원합니다.

1. 사용자가 문서 유형을 선택하고 파일을 업로드합니다.
2. 서버가 파일 메타데이터와 분석 세션을 먼저 저장합니다.
3. 업로드 요청은 빠르게 종료하고 결과 화면으로 즉시 이동합니다.
4. 백그라운드 스레드에서 확장자에 맞는 추출기를 선택합니다.
5. 문서에서 본문, 표, 시각 자료 정보를 분리 추출합니다.
6. 필요 시 시각 자료 스냅샷을 기반으로 OCR 및 차트 해석 입력을 추가합니다.
7. 추출된 내용을 청크 단위로 분석하고 병합합니다.
8. 응답이 길이 제한에 걸리면 단계별로 더 큰 토큰 수로 재시도합니다.
9. 최종 실행계획 JSON을 생성하고 누락 점검을 수행합니다.
10. 분석 결과를 MySQL에 저장하고 세션 상태를 COMPLETED 또는 FAILED로 갱신합니다.
11. 결과 화면에서 사용자는 진행 상태를 확인하거나, 완료 후 내용을 수정하고 다시 저장할 수 있습니다.
12. 홈 화면에서 저장된 분석 결과를 관리하고 삭제할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- Gradle
- Spring MVC
- Thymeleaf
- Spring Validation
- Spring Data JPA
- MySQL
- Spring Async
- OpenAI Responses API
- Jackson
- Apache PDFBox
- Apache POI

## 프로젝트 구조

```text
BPA_project
├─ build.gradle
├─ src
│  ├─ main
│  │  ├─ java/com/example/BPA_project
│  │  │  ├─ config
│  │  │  ├─ controller
│  │  │  ├─ dto
│  │  │  ├─ entity
│  │  │  ├─ exception
│  │  │  ├─ model
│  │  │  ├─ repository
│  │  │  ├─ service
│  │  │  ├─ util
│  │  │  └─ BpaProjectApplication.java
│  │  └─ resources
│  │     ├─ static/css
│  │     ├─ templates
│  │     └─ application.properties
│  └─ test
│     └─ java/com/example/BPA_project
├─ gradle
├─ gradlew
├─ gradlew.bat
└─ README.md
```

## 실행 방법

### 1. MySQL 준비

```sql
CREATE DATABASE bpa_project CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 설정 파일 작성

`src/main/resources/application-secret.properties` 예시:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bpa_project?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

app.openai.api-url=https://api.openai.com/v1/responses
app.openai.api-key=${OPENAI_API_KEY:}
app.openai.model=gpt-4.1
app.openai.max-output-tokens=3000
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows PowerShell에서는:

```powershell
.\gradlew.bat bootRun
```

### 4. 접속 경로

- 메인 화면: `http://localhost:8080/`
- 문서 업로드: `http://localhost:8080/documents/upload`
- 분석 결과: `http://localhost:8080/documents/{sessionId}`
- 분석 결과 관리: `http://localhost:8080/documents/delete`

## 상태 기반 결과 화면

- `PENDING`: 업로드 완료 후 분석 대기 상태
- `PROCESSING`: 문서 추출, 시각 자료 분석, OpenAI 분석 진행 상태
- `COMPLETED`: 분석 완료 및 결과 수정 가능 상태
- `FAILED`: 분석 실패 상태

결과 화면은 `PENDING` 또는 `PROCESSING` 상태일 때 자동 새로고침으로 완료 여부를 확인합니다.

## 토큰 초과 대응

긴 문서를 분석할 때는 단계별 출력 길이 제한에 걸릴 수 있습니다. 현재 구현은 아래 순서로 대응합니다.

1. 청크 분석 단계에서 `max_output_tokens` 초과 시 재시도
2. 요약 압축 단계에서 `max_output_tokens` 초과 시 재시도
3. 최종 구조화 단계에서 더 큰 토큰 수 또는 compact 모드로 재시도

이 방식으로 긴 문서에서도 분석 실패 가능성을 낮추도록 보완했습니다.

## 설정 파일

기본 설정은 `src/main/resources/application.properties`에 있습니다.

```properties
spring.application.name=bpa-project
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
spring.thymeleaf.cache=false
spring.config.import=optional:application-secret.properties

server.port=8080
```

## 한계 및 개선 방향

- 업로드 파일 자체는 영구 저장하지 않고 메타데이터 중심으로 관리합니다.
- 분석 결과의 출처 페이지/슬라이드 근거 표시 기능은 아직 없습니다.
- 분석 실패 시 재시도 버튼과 이력 비교 기능은 아직 없습니다.
- 테스트 범위가 제한적입니다.
- 분석 이력 검색, 사용자 인증, 권한 분리 기능은 아직 없습니다.
- 향후 PDF/Excel 내보내기, 근거 추적, 이력 비교 기능을 추가할 수 있습니다.
