# Gemma4 Mobile 툴 시스템 설계

## 개요

Gemma4 Mobile 앱에 툴/함수 호출 기능을 추가하여, 모델이 자율적으로 인터넷 검색과 디바이스 내부 데이터(캘린더, SMS, 전화, 연락처, 알람)에 접근할 수 있게 한다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 툴 호출 방식 | 모델 자율 판단 (`<tool_call>` 토큰 파싱) |
| 디바이스 접근 범위 | 캘린더, SMS, 전화 기록, 연락처, 알람 (앱 실행 제외) |
| 읽기/쓰기 | 모두 지원 |
| 인터넷 검색 | DuckDuckGo HTML 스크래핑 (API 키 불필요) |
| 위험 액션 확인 | SMS 발송, 전화 걸기만 사용자 확인 |
| UI 표현 | 리치 카드 + 상태 표시 |
| 체이닝 | 다중 툴 순차 호출 지원 (최대 5회) |

---

## 아키텍처

### 전체 흐름

```
사용자 입력
    ↓
ChatViewModel.sendMessage()
    ↓
시스템 프롬프트에 툴 스키마 주입
    ↓
GemmaInferenceEngine.generateStream()
    ↓
ToolCallParser — 모델 출력에서 <tool_call> 감지
    ↓ (감지됨)
ToolRouter — 해당 툴 매칭
    ↓
ConfirmationGate — SMS 발송/전화 걸기면 사용자 확인
    ↓
ToolExecutor — 실제 실행 (검색, 캘린더 등)
    ↓
결과를 컨텍스트에 추가 → 모델이 다음 <tool_call> 또는 최종 응답 생성
    ↓
UI 렌더링 (리치 카드 + 상태 표시)
```

### 체이닝 루프

```
while (모델 출력에 <tool_call> 존재):
    1. 파싱 → 실행 → 결과 수집
    2. <tool_result>를 컨텍스트에 추가
    3. 모델에 다시 생성 요청
    4. 최대 5회 제한 (무한루프 방지)
최종 텍스트 응답을 사용자에게 표시
```

---

## 툴 정의

### 툴 프로토콜

모델이 생성하는 포맷:
```
<tool_call>
{"name": "search_web", "arguments": {"query": "날씨 서울"}}
</tool_call>
```

실행 결과를 모델에 재주입하는 포맷:
```
<tool_result>
{"name": "search_web", "result": {"items": [...]}}
</tool_result>
```

### 툴 목록 (11개)

| 툴 이름 | 기능 | 확인 필요 |
|---|---|---|
| `search_web` | DuckDuckGo 웹 검색 | 아니오 |
| `read_calendar` | 캘린더 일정 조회 | 아니오 |
| `write_calendar` | 캘린더 일정 추가/수정 | 아니오 |
| `read_sms` | SMS 메시지 읽기 | 아니오 |
| `send_sms` | SMS 메시지 보내기 | **예** |
| `read_call_log` | 통화 내역 조회 | 아니오 |
| `make_call` | 전화 걸기 | **예** |
| `read_contacts` | 연락처 검색/조회 | 아니오 |
| `write_contact` | 연락처 추가 | 아니오 |
| `read_alarms` | 알람 목록 조회 | 아니오 |
| `set_alarm` | 알람 설정 | 아니오 |

### 각 툴 상세

#### search_web
- **인자**: `query` (검색어, string)
- **반환**: `items` 배열 — 각 항목에 `title`, `snippet`, `url`
- **구현**: DuckDuckGo `https://html.duckduckgo.com/html/?q=` 에 GET 요청 → HTML 파싱
- **제한**: 상위 5개 결과 반환

#### read_calendar
- **인자**: `start_date` (ISO 날짜), `end_date` (ISO 날짜, optional)
- **반환**: `events` 배열 — 각 항목에 `title`, `start`, `end`, `location`, `description`
- **구현**: Android CalendarContract.Events ContentProvider 조회

#### write_calendar
- **인자**: `title`, `start` (ISO datetime), `end` (ISO datetime), `location` (optional), `description` (optional)
- **반환**: `event_id`, `status`
- **구현**: CalendarContract.Events에 INSERT

#### read_sms
- **인자**: `contact` (이름 또는 번호, optional), `count` (개수, default 10)
- **반환**: `messages` 배열 — 각 항목에 `address`, `body`, `date`, `type` (sent/received)
- **구현**: Telephony.Sms ContentProvider 조회

#### send_sms
- **인자**: `to` (전화번호 또는 연락처 이름), `message` (본문)
- **반환**: `status`, `to_resolved` (실제 번호)
- **구현**: SmsManager.sendTextMessage()
- **확인**: 발송 전 ConfirmationSheet 표시

#### read_call_log
- **인자**: `contact` (이름 또는 번호, optional), `count` (개수, default 10)
- **반환**: `calls` 배열 — 각 항목에 `number`, `name`, `date`, `duration`, `type` (incoming/outgoing/missed)
- **구현**: CallLog.Calls ContentProvider 조회

#### make_call
- **인자**: `number` (전화번호 또는 연락처 이름)
- **반환**: `status`, `number_resolved`
- **구현**: Intent(Intent.ACTION_CALL)
- **확인**: 발신 전 ConfirmationSheet 표시

#### read_contacts
- **인자**: `query` (이름 검색어), `count` (개수, default 10)
- **반환**: `contacts` 배열 — 각 항목에 `name`, `phone`, `email`
- **구현**: ContactsContract ContentProvider 조회

#### write_contact
- **인자**: `name`, `phone`, `email` (optional)
- **반환**: `contact_id`, `status`
- **구현**: ContactsContract에 INSERT

#### read_alarms
- **인자**: 없음
- **반환**: `alarms` 배열 — 각 항목에 `hour`, `minute`, `label`, `enabled`
- **구현**: AlarmClock provider 조회 (제한적, 앱별로 다름)

#### set_alarm
- **인자**: `hour`, `minute`, `label` (optional)
- **반환**: `status`
- **구현**: Intent(AlarmClock.ACTION_SET_ALARM)

---

## 시스템 프롬프트 설계

모델에 주입할 시스템 프롬프트에 툴 스키마를 포함:

```
You are a helpful assistant with access to the following tools.
When you need to use a tool, output a <tool_call> block with JSON.
You may chain multiple tool calls in sequence.
After receiving <tool_result>, use the information to respond to the user.

Available tools:
- search_web(query: string): Search the web and return results
- read_calendar(start_date: string, end_date?: string): Read calendar events
- write_calendar(title: string, start: string, end: string, location?: string, description?: string): Create calendar event
- read_sms(contact?: string, count?: int): Read SMS messages
- send_sms(to: string, message: string): Send SMS message
- read_call_log(contact?: string, count?: int): Read call history
- make_call(number: string): Make a phone call
- read_contacts(query: string, count?: int): Search contacts
- write_contact(name: string, phone: string, email?: string): Add contact
- read_alarms(): List alarms
- set_alarm(hour: int, minute: int, label?: string): Set alarm
```

---

## Android 권한

### 추가 필요 권한

```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.WRITE_CONTACTS" />
<uses-permission android:name="android.permission.SET_ALARM" />
```

### 기존 권한

```xml
<uses-permission android:name="android.permission.INTERNET" />        <!-- 이미 있음 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />  <!-- 이미 있음 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />     <!-- 이미 있음 -->
```

### 권한 요청 전략

- 런타임 권한은 해당 툴을 **처음 사용할 때** lazy하게 요청
- 권한 거부 시 모델에 "권한이 없어 실행할 수 없습니다"를 `<tool_result>`로 반환
- 권한 그룹별로 요청 (예: 캘린더 읽기+쓰기 한 번에)

---

## UI 설계

### 상태 표시 (ToolStatusIndicator)

모델이 툴을 실행하는 동안 채팅 영역에 표시:
- "검색 중..." (search_web)
- "캘린더 조회 중..." (read_calendar)
- "메시지 읽는 중..." (read_sms)
- 등 — 툴별 한국어 상태 메시지 + 로딩 애니메이션

### 리치 카드

#### SearchResultCard
- 제목 (클릭 시 브라우저 오픈)
- 요약 텍스트
- URL 도메인 표시

#### CalendarEventCard
- 일정 제목
- 날짜/시간
- 장소 (있으면)

#### ContactCard
- 이름
- 전화번호
- 이메일 (있으면)

#### CallLogCard
- 이름/번호
- 통화 유형 아이콘 (수신/발신/부재중)
- 날짜, 통화 시간

#### SmsCard
- 발신자/수신자
- 메시지 본문 미리보기
- 날짜

### ConfirmationSheet (바텀시트)

SMS 발송/전화 걸기 시 표시:
- 액션 설명 ("김철수에게 SMS를 보냅니다")
- 상세 내용 (메시지 본문 또는 전화번호)
- "실행" / "취소" 버튼

---

## 패키지 구조

```
com.gemma4mobile.tools/
├── ToolDefinition.kt          // 툴 스키마 데이터 클래스
├── ToolCallParser.kt          // <tool_call> 파싱
├── ToolRouter.kt              // 이름 → 실행기 매핑
├── ToolExecutor.kt            // 실행 인터페이스
├── ConfirmationGate.kt        // 위험 액션 확인 로직
├── ToolPermissionManager.kt   // 런타임 권한 요청 관리
├── SystemPromptBuilder.kt     // 툴 스키마를 시스템 프롬프트에 주입
├── executor/
│   ├── WebSearchExecutor.kt   // DuckDuckGo 스크래핑
│   ├── CalendarExecutor.kt    // 캘린더 읽기/쓰기
│   ├── SmsExecutor.kt         // SMS 읽기/보내기
│   ├── CallLogExecutor.kt     // 통화 기록/전화 걸기
│   ├── ContactsExecutor.kt    // 연락처 읽기/쓰기
│   └── AlarmExecutor.kt       // 알람 조회/설정
└── ui/
    ├── ToolStatusIndicator.kt // "검색 중..." 상태 표시
    ├── SearchResultCard.kt    // 검색 결과 리치 카드
    ├── CalendarEventCard.kt   // 일정 카드
    ├── ContactCard.kt         // 연락처 카드
    ├── CallLogCard.kt         // 통화 기록 카드
    ├── SmsCard.kt             // SMS 카드
    └── ConfirmationSheet.kt   // 위험 액션 확인 바텀시트
```

---

## 에러 처리

- **권한 거부**: `<tool_result>{"error": "permission_denied", "message": "캘린더 접근 권한이 없습니다"}</tool_result>`
- **툴 실행 실패**: `<tool_result>{"error": "execution_failed", "message": "검색에 실패했습니다"}</tool_result>`
- **파싱 실패**: 잘못된 `<tool_call>` JSON은 무시하고 텍스트로 표시
- **체이닝 초과**: 5회 초과 시 강제 종료, "요청을 처리하는 데 너무 많은 단계가 필요합니다" 메시지
- **연락처 이름→번호 해석 실패**: 동명이인이면 목록을 보여주고 선택 요청
