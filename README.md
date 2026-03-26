# BPA Project

브라우저에서 PDF를 업로드하면 AI가 실행 계획 초안을 구조화해 보여주고, 사용자가 필요 시 내용을 수정할 수 있는 Spring Boot 기반 업무지원 도구입니다.

## 프로젝트 배경 / 문제 정의

일반 사무 업무에서는 회의자료, 보고서, 제안서처럼 형식은 잘 정리되어 있지만 실제로 무엇을 해야 하는지까지 바로 연결되지 않는 문서가 많습니다.

문서를 읽고 실행 계획으로 바꾸는 과정에서는 다음과 같은 문제가 자주 발생합니다.

- 문서 요약은 가능하지만 실제 실행 항목, 우선순위, 일정 초안까지 정리하는 데 시간이 많이 듭니다.
- 회의자료나 보고서 내용을 실무형 액션 아이템으로 재구성하는 작업이 담당자마다 다르게 수행됩니다.
- 한 번 만든 결과를 브라우저에서 검토하고 필요한 경우 수정하는 흐름이 분리되어 있습니다.

이 프로젝트는 PDF 중심 MVP로 시작하여, 문서 업로드부터 AI 분석, 결과 검토, 선택적 수정까지 한 번에 이어지는 업무지원 흐름을 제공하는 것을 목표로 합니다.

## 주요 기능

- 메인 페이지에서 프로젝트 소개와 문서 업로드 폼 제공
- 문서 유형 선택 지원: 회의자료, 보고서, 제안서
- PDF 업로드 시 로컬 저장 후 텍스트 추출
- OpenAI Responses API 호출
- Structured Outputs 방식으로 JSON 구조 응답 수신
- 핵심 요약, 목표, 해야 할 일, 우선순위, 일정 초안, 리스크, 추가 확인 필요 항목 표시
- 결과 페이지 기본 읽기 전용 제공
- `수정하기` 클릭 시 편집 모드 진입, `저장` 또는 `취소` 지원
- PPT/PPTX는 1차 버전에서 업로드 및 안내 메시지 제공
- 분석 결과를 로컬 JSON 파일과 메모리 캐시로 관리
- 파일 형식 오류, OpenAI 호출 오류, JSON 파싱 오류 처리

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- Gradle
- Thymeleaf
- OpenAI Responses API
- Structured Outputs(JSON Schema)
- Apache PDFBox
- Spring Validation
- 로컬 파일 저장소 기반 처리

## 프로젝트 구조

```text
BPA_project
├─ build.gradle
├─ src
│  ├─ main
│  │  ├─ java
│  │  │  └─ com/example/BPA_project
│  │  │     ├─ controller
│  │  │     ├─ service
│  │  │     ├─ dto
│  │  │     ├─ model
│  │  │     ├─ util
│  │  │     ├─ exception
│  │  │     ├─ config
│  │  │     └─ BpaProjectApplication.java
│  │  └─ resources
│  │     ├─ static/css
│  │     ├─ templates
│  │     ├─ application.properties
│  │     └─ application-secret.properties
│  └─ test
│     └─ java/com/example/BPA_project
├─ storage
│  ├─ uploads
│  ├─ analysis
│  └─ reports
└─ README.md
```

주요 역할은 다음과 같습니다.

- `controller`: 페이지 진입, 업로드 처리, 결과 조회, 수정 저장
- `service`: 파일 저장, PDF 텍스트 추출, OpenAI 호출, 세션 저장
- `dto`: 업로드 요청과 AI 분석 결과 구조
- `model`: 업로드 파일 정보와 분석 세션 상태
- `util`: 파일명 처리, 프롬프트 생성, JSON 스키마 생성
- `config`: `application.properties` 바인딩 설정

## 실행 흐름과 실행 방법

### 실행 흐름

1. 사용자가 메인 페이지에서 문서 유형을 선택하고 PDF 파일을 업로드합니다.
2. 서버가 파일을 `storage/uploads`에 저장합니다.
3. PDF인 경우 PDFBox로 텍스트를 추출합니다.
4. 추출 텍스트와 문서 유형별 프롬프트를 기반으로 OpenAI Responses API를 호출합니다.
5. Structured Outputs 응답을 `AnalysisResultDto`로 매핑합니다.
6. 결과를 `storage/analysis`의 JSON 파일과 메모리 세션에 저장합니다.
7. 결과 화면에서 사용자는 기본 읽기 전용으로 내용을 확인합니다.
8. 필요 시 `수정하기`를 눌러 편집 모드로 전환하고, `저장` 또는 `취소`를 선택합니다.

### 실행 방법

1. Java 17이 설치되어 있어야 합니다.
2. 프로젝트 루트에서 비공개 설정 파일과 환경 변수를 준비합니다.
3. 아래 명령으로 애플리케이션을 실행합니다.

```powershell
$env:OPENAI_API_KEY="your-api-key"
.\gradlew bootRun
```

4. 브라우저에서 `http://localhost:8080`에 접속합니다.

## 환경 변수 및 설정

공개 설정은 `src/main/resources/application.properties`에 두고, OpenAI 관련 설정은 `src/main/resources/application-secret.properties`로 분리했습니다.

### application.properties

```properties
spring.application.name=bpa-project
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
spring.thymeleaf.cache=false
spring.config.import=optional:application-secret.properties

server.port=8080

app.storage.upload-dir=./storage/uploads
app.storage.analysis-dir=./storage/analysis
app.storage.report-dir=./storage/reports
```

### application-secret.properties

```properties
app.openai.api-url=https://api.openai.com/v1/responses
app.openai.api-key=${OPENAI_API_KEY:}
app.openai.model=gpt-4.1
app.openai.max-output-tokens=1200
```

### 환경 변수

- `OPENAI_API_KEY`: OpenAI API 인증 키

참고:
- `application-secret.properties`는 `.gitignore`에 추가되어 Git 추적 대상에서 제외됩니다.
- 실제 배포 환경에서는 로컬 파일 대신 OS 환경 변수 또는 별도 시크릿 관리 체계를 사용하는 편이 안전합니다.

## OpenAI API 적용 이슈 요약

이번 구현 과정에서 OpenAI API 연동 시 다음과 같은 점을 확인했습니다.

### 1. Structured Outputs JSON Schema 형식이 정확해야 함

Responses API에서 Structured Outputs를 사용할 때는 JSON Schema 형식이 정확해야 합니다. 특히 배열 필드의 `items` 구조가 잘못되면 OpenAI가 400 오류를 반환하거나, 응답을 정상 구조로 만들지 못할 수 있습니다.

적용 포인트:
- `JsonSchemaFactory`에서 `goals`, `risks`, `questions`, `tasks`의 스키마를 명확히 정의
- `additionalProperties: false`, `required` 항목을 포함해 구조를 고정

### 2. max-output-tokens가 너무 작으면 JSON이 잘릴 수 있음

긴 PDF는 응답 생성 단계에서 출력 토큰이 부족해 JSON이 중간에 잘릴 수 있습니다.
이 프로젝트는 이를 줄이기 위해 `청크별 1차 요약 -> 최종 구조화` 2단계로 처리합니다.

현재 운영 방식:
- 청크 요약 단계에서 문서를 분할해 핵심만 먼저 압축
- 최종 구조화 단계에서 JSON 생성
- 출력 토큰 부족 시 더 큰 한도로 재시도
- 그래도 길면 compact 모드로 항목 수를 줄여 다시 시도

권장 운영값:
- 기본: `2500`
- 재시도: `4000`
- compact 재시도: `4500`

이렇게 쓰는 이유는:

- 단순 경고가 아니라 실제 해결 구조가 보임
- 왜 2단계 처리를 넣었는지 설명됨
- 운영자가 설정값을 바로 이해할 수 있음

### 3. 비용은 파일 크기보다 입력 텍스트 길이에 더 크게 영향받음

PDF 파일이 크더라도 실제 텍스트가 적을 수 있고, 반대로 파일은 작아도 텍스트가 매우 길 수 있습니다. OpenAI 비용은 업로드 파일 크기보다 모델에 전달되는 텍스트 토큰 수에 더 직접적으로 영향을 받습니다.

현재 대응:
- 업로드 파일 제한은 `25MB`
- OpenAI로 보내는 본문은 `truncate()`로 길이 제한 적용

현재 적용 방식:
- 긴 PDF는 여러 청크로 나눈 뒤 각 청크를 먼저 1차 요약
- 청크 요약들을 다시 합쳐 최종 Structured Outputs JSON으로 구조화
- 한 번에 긴 원문 전체를 보내는 방식보다 max_output_tokens 초과와 JSON 잘림 위험을 줄임

향후 개선 방향:
- 긴 문서는 청크 단위 분석 후 통합
- 문서 앞부분만 보내는 방식 대신 섹션별 요약 전략 도입

### 4. 429 Too Many Requests는 코드 문제가 아니라 quota 문제일 수 있음

다음 오류는 코드 오류가 아니라 OpenAI 계정의 사용량 한도 또는 결제 상태 문제일 수 있습니다.

```text
429 TOO_MANY_REQUESTS: You exceeded your current quota
```

확인 포인트:
- API 키가 quota가 남아 있는 프로젝트에 연결되어 있는지 확인
- 결제 수단 및 사용량 한도 확인
- 필요하면 새 키 발급 또는 더 저렴한 모델 사용 검토

### 5. 실패 시 원문 에러 메시지를 보여주는 것이 디버깅에 중요함

초기에는 `OpenAI API call failed.`처럼 포괄적인 메시지만 보여서 원인 파악이 어려웠습니다. 이후 상태 코드와 에러 본문을 함께 노출하도록 보강하여 문제 원인을 더 빨리 확인할 수 있게 했습니다.

현재 개선 내용:
- 4xx/5xx 응답 시 상태 코드와 OpenAI 에러 메시지 표시
- `incomplete` 응답 상태 감지
- 구조화 응답 파싱 실패 시 잘림 가능성 안내

### 6. MVP 단계에서는 보수적인 설정이 유리함

초기 단계에서는 업로드 크기, 토큰 수, 응답 구조를 너무 공격적으로 잡기보다 보수적으로 시작하는 편이 안정적입니다.

현재 권장 설정:
- 업로드 제한: `25MB`
- `app.openai.max-output-tokens=1200`
- PDF 중심 분석
- PPT/PPTX는 업로드 후 안내만 제공

## 향후 개선 사항

- PPT/PPTX 텍스트 추출 또는 PDF 자동 변환 기능 추가
- 업로드 용량 초과 시 사용자 친화적 한글 오류 화면 제공
- 분석 이력 목록 페이지 제공
- PDF 본문이 긴 경우 청크 단위 분석 및 요약 품질 개선
- OpenAI 모델 선택과 프롬프트 튜닝 옵션 제공
- 문서 원문 미리보기 및 분석 근거 표시
- 파일 크기, 페이지 수, 처리 시간에 대한 운영 모니터링 추가
- 인증/권한과 사용자별 작업 공간 분리