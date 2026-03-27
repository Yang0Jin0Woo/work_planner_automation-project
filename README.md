# BPA Project

- 브라우저에서 문서를 업로드하면 AI가 문서 내용을 실행 계획 형태로 구조화해 주고, 사용자가 결과를 검토하고 수정할 수 있도록 만든 Spring Boot 기반 업무지원 웹 애플리케이션입니다.

## 프로젝트 개요

- 일반 사무 업무에서는 회의자료, 보고서, 제안서처럼 형식은 잘 정리되어 있어도 실제 후속 업무로 바로 연결하기 어려운 문서가 많습니다. 이 프로젝트는 비정형 문서 내용을 읽고 다음과 같은 실무형 정보로 재구성하는 데 초점을 맞췄습니다.

  - 문서 핵심 요약
  - 목표 항목
  - 우선순위 기반 실행 과제
  - 일정 초안
  - 리스크
  - 추가 확인이 필요한 사항

  ➡ 단순 요약이 아니라, 실무자가 바로 검토하고 손볼 수 있는 초안 형태의 결과를 제공하는 것을 목표로 했습니다.

## 주요 기능

- 문서 업로드 화면 제공
- 문서 유형 선택 지원
  - 회의자료
  - 보고서
  - 제안서
- PDF, PPT, PPTX 업로드 지원
- PDFBox와 Apache POI를 이용한 문서 텍스트 추출
- OpenAI Responses API 기반 문서 분석
- 청크별 구조화 추출 후 최종 병합 기반 긴 문서 분석
- Structured Outputs(JSON Schema) 기반 결과 구조화
- 분석 결과 화면 제공
- 결과 수정 후 재저장 지원
- 업로드 형식 오류, OpenAI 호출 오류, 응답 파싱 오류 처리

## 현재 구현 범위

- 현재 프로젝트는 MVP 단계입니다. 실제 구현 상태는 아래와 같습니다.

  - 업로드된 파일의 메타데이터를 생성하고 분석에 사용
  - PDF, PPT, PPTX에서 텍스트를 추출해 AI 분석에 전달
  - 긴 문서는 청크 단위로 나눈 뒤 청크별 구조화 추출 후 병합
  - 병합된 후보 정보를 바탕으로 최종 실행 계획 JSON 생성
  - 분석 결과는 메모리 세션에 저장
  - 결과 화면에서 사용자가 직접 내용을 수정 가능

- 아직 구현되지 않았거나 제한이 있는 부분도 있습니다.

  - 업로드 파일을 디스크에 영구 저장하지는 않음
  - 분석 결과를 DB 또는 JSON 파일로 영속 저장하지는 않음
  - 청크 기준이 아직 문서 구조 단위가 아니라 텍스트 길이 중심임
  - 분석 근거 페이지, 청크 출처, 신뢰도 필드는 아직 제공하지 않음
  - 테스트 코드는 최소만 존재
  - 인증, 사용자별 작업 이력, 분석 이력 조회 기능 없음

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- Gradle
- Spring MVC
- Thymeleaf
- Spring Validation
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
│  │  │  ├─ exception
│  │  │  ├─ model
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

### 패키지별 역할

- `controller`
  - 메인 화면 진입
  - 업로드 처리
  - 결과 조회
  - 수정 결과 저장
- `service`
  - 파일 메타데이터 생성
  - 문서 유형별 텍스트 추출
  - OpenAI API 호출
  - 청크 분석 결과 병합
  - 분석 세션 관리
- `dto`
  - 업로드 요청 데이터
  - 청크 분석 데이터 구조
  - 최종 분석 결과 데이터 구조
- `model`
  - 저장 파일 정보
  - 분석 세션 상태 정보
- `util`
  - 파일명 정리
  - 프롬프트 생성
  - JSON Schema 생성

## 동작 흐름

1. 사용자가 문서 유형을 선택하고 파일을 업로드합니다.
2. 서버가 파일 메타데이터를 생성합니다.
3. 확장자에 맞는 추출기를 선택해 텍스트를 추출합니다.
4. 추출된 텍스트가 길면 청크 단위로 분할합니다.
5. 각 청크에서 요약, 목표, 작업, 리스크, 질문 후보를 구조화해 추출합니다.
6. 청크별 후보 정보를 병합하면서 중복을 줄이고 업무 항목을 정리합니다.
7. 병합된 후보 정보를 바탕으로 최종 실행 계획 JSON을 생성합니다.
8. 결과를 세션에 저장하고 결과 페이지에 보여줍니다.
9. 사용자는 결과를 수정하고 다시 저장할 수 있습니다.

## OpenAI 연동 방식

- OpenAI API를 사용하여 응답을 정해진 구조의 JSON으로 받도록 설계했습니다.

### 적용 포인트

- 문서 유형별 프롬프트 분리
- 청크 추출용 프롬프트와 최종 계획용 프롬프트 분리
- Structured Outputs용 JSON Schema 명시
- 긴 문서 대응을 위한 청크 분할
- 청크별 구조화 추출 후 병합 방식 적용
- `max_output_tokens` 부족 시 재시도
- 문서가 길면 compact 모드로 항목 수 축소

### 왜 이렇게 설계했는가

- 문서 길이가 길어질수록 한 번에 구조화 응답을 받기 어렵고, JSON이 중간에 잘릴 가능성이 커집니다. 이를 줄이기 위해 다음과 같이 2단계 분석 구조를 사용했습니다.

  - 1단계: 청크별 구조화 추출
  - 2단계: 병합된 후보 정보를 바탕으로 최종 구조화

- 이 방식은 단순 요약보다 구현 난도가 높지만, 뒤쪽 정보 누락을 줄이고 업무 항목을 더 안정적으로 정리하는 데 유리합니다.

### 현재 병합 로직

- 요약, 목표, 리스크, 질문은 중복 문장을 정규화해 후보를 병합
- 작업 항목은 과업명 기준으로 중복을 줄이고 우선순위, 상태, 담당자, 일정 정보를 보존
- 병합된 후보 목록을 최종 프롬프트 입력으로 재구성

## 실행 방법

- 메인 화면: `http://localhost:8080/`
- 문서 업로드 요청: `http://localhost:8080/documents/upload`
- 분석 결과 화면: `http://localhost:8080/documents/{sessionId}`

## 설정 파일

- 현재 공개 설정은 `src/main/resources/application.properties`에 있습니다.

```properties
spring.application.name=bpa-project
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
spring.thymeleaf.cache=false
spring.config.import=optional:application-secret.properties

server.port=8080
```

- 추가로 불러올 수 있는 비공개 설정 파일 예시는 아래와 같습니다.

```properties
app.openai.api-url=https://api.openai.com/v1/responses
app.openai.api-key=${OPENAI_API_KEY:}
app.openai.model=gpt-4.1
app.openai.max-output-tokens=2300
```

- `application-secret.properties`는 선택 사항이며, 현재 코드에서는 `optional:application-secret.properties`로 불러오도록 되어 있습니다.

## 한계와 보완 필요 사항

- 파일 메타데이터는 생성하지만 실제 업로드 파일을 영구 저장하지 않음
- 분석 결과를 메모리 기반으로만 관리하므로 서버 재시작 시 유지되지 않음
- 청크 분할이 아직 제목, 페이지, 슬라이드 기반이 아니라 문자 수 중심임
- 분석 근거 문장, 페이지 번호, 청크 출처를 결과에 함께 저장하지 않음
- 수정 저장 시 서버 측 검증이 충분하지 않음
- 테스트 범위가 매우 제한적임
- 사용자 인증과 분석 이력 관리가 없음

## 향후 개선 방향

- 업로드 파일 실제 저장 기능 추가
- 분석 결과 JSON 또는 DB 저장 기능 추가
- 분석 이력 조회 화면 추가
- 청크 기준을 페이지, 슬라이드, 문단 구조 중심으로 개선
- 청크별 근거 문장과 출처 필드 추가
- 서버 측 입력 검증 강화
- OpenAI 연동부 단위 테스트 및 예외 처리 테스트 추가
- 문서 원문 미리보기 및 분석 근거 표시
- 사용자 인증 및 작업 공간 분리
