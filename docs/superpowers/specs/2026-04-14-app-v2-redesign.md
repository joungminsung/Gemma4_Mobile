# Gemma4 Mobile App v2 — 전면 개선 설계

## 1. 색상 테마

### 다크 모드 (기본)
- 배경: `#212121`
- 사용자 버블: `#2f2f2f`
- AI 응답 영역: `#343541` (풀 와이드 배경)
- 사이드바: `#171717`
- 텍스트: `#ececec` (기본), `#d1d5db` (AI), `#8e8ea0` (플레이스홀더)
- 액센트: `#19c37d` (AI 아이콘), `#5436DA` (사용자 아이콘)
- 입력창: `#2f2f2f` 배경, `#40414f` 테두리, 라운드 24dp

### 라이트 모드
- 배경: `#ffffff`
- 사용자 버블: `#f7f7f8`
- AI 응답 영역: `#f7f7f8`
- 사이드바: `#f9f9f9`
- 텍스트: `#202124` (기본), `#374151` (AI)
- 액센트: 동일

---

## 2. 채팅 레이아웃 (하이브리드)

### 사용자 메시지 (버블 스타일)
- 오른쪽 정렬
- 배경: `#2f2f2f`, 라운드 16dp (우하단만 4dp)
- 좌측에 사용자 아바타(이니셜 원형)
- 최대 폭: 화면의 80%

### AI 응답 (풀 와이드 스타일)
- 전체 폭, 좌측에 AI 아이콘 (초록색 원 + ✦)
- 배경: 없음 (기본 배경색과 동일) 또는 약간 다른 톤 `#2f2f2f`
- 마크다운 렌더링 적용
- 하단에 복사/공유 버튼

---

## 3. 마크다운 렌더링

AI 응답에 적용할 마크다운 요소:

| 요소 | 렌더링 방식 |
|------|------------|
| **볼드** | 흰색 + 굵게 |
| *이탤릭* | 기울임 |
| `인라인 코드` | `#2f2f2f` 배경, 모노스페이스 |
| 코드블록 | 어두운 배경 + 구문 하이라이팅 + 복사 버튼 + 언어 표시 |
| 리스트 (-, 1.) | 들여쓰기 + 불릿/번호 |
| 테이블 | 구분선 있는 테이블 |
| > 인용문 | 좌측 테두리 + 배경 |
| 링크 | 액센트 색상 + 밑줄 + 클릭 시 브라우저 |
| 헤딩 (##, ###) | 크기별 구분, 굵게 |

구현: Jetpack Compose용 마크다운 라이브러리 (`com.mikepenz:multiplatform-markdown-renderer` 또는 커스텀 파서)

---

## 4. 네비게이션 — 사이드 드로어

### 드로어 구조
```
┌─────────────────────────┐
│ [앱 로고] Gemma 4    [✎] │  ← 새 대화 버튼
│─────────────────────────│
│ 🔍 대화 검색              │
│─────────────────────────│
│ 오늘                      │
│  ● 한국의 수도에 대해      │  ← 현재 세션 하이라이트
│  ○ Python 학습 질문       │
│  ○ 저녁 메뉴 추천         │
│─────────────────────────│
│ 어제                      │
│  ○ React vs Vue 비교     │
│  ○ 운동 루틴 만들기       │
│─────────────────────────│
│ 이번 주                   │
│  ○ ...                   │
│─────────────────────────│
│                           │
│ ⚙ 설정                   │
└─────────────────────────┘
```

### 인터랙션
- 왼쪽 가장자리에서 스와이프 또는 상단 햄버거 메뉴로 열기
- 세션 롱프레스 → 이름 변경 / 삭제 컨텍스트 메뉴
- 새 대화 버튼(✎) → 즉시 새 세션 생성 + 채팅 화면으로

---

## 5. 설정 화면

### 5-1. 모델 관리
- 현재 로드된 모델 티어 표시
- 티어 변경 (Lite/Standard/Full/Max)
- 모델 삭제 / 재다운로드
- 디스크 사용량 표시

### 5-2. 시스템 프롬프트
- 텍스트 입력으로 AI 역할/성격 설정
- 프리셋: "기본", "친근한 친구", "전문 비서", "코딩 도우미"
- 커스텀 프롬프트 저장

### 5-3. 생성 파라미터
- Temperature 슬라이더 (0.0 ~ 2.0, 기본 0.8)
- Top-P 슬라이더 (0.0 ~ 1.0, 기본 0.95)
- Max Tokens 슬라이더 (64 ~ 2048, 기본 1024)
- 각 파라미터 옆 설명 텍스트

### 5-4. 테마
- 다크 / 라이트 / 시스템 따르기

### 5-5. 대화 내보내기
- 현재 대화 텍스트 복사
- 공유 인텐트 (다른 앱으로 공유)

### 5-6. 음성 입력
- STT 언어 선택 (한국어/영어/자동)
- 자동전송 토글 (음성 인식 완료 시 자동 전송)

### 5-7. 빠른 호출
- 측면버튼 호출 활성화/비활성화
- 설정 방법 안내 (기기 설정 → 디지털 어시스턴트 앱 변경)

설정값 저장: `DataStore<Preferences>`

---

## 6. 세션 관리

### Room DB 스키마 (기존 유지 + 확장)
- `sessions` 테이블: id, title, createdAt, updatedAt, systemPrompt
- `messages` 테이블: id, sessionId, role, content, timestamp

### 기능
- 대화 생성 / 삭제 / 이름 변경
- 날짜별 자동 그룹핑 (오늘, 어제, 이번 주, 이전)
- 대화 검색 (세션 제목 + 메시지 내용)

---

## 7. 음성 입력

### 마이크 버튼
- 입력창 오른쪽, 전송 버튼 옆에 배치
- 텍스트 입력 중이면 전송 버튼만 표시, 비어있으면 마이크 표시

### STT 동작
- Android SpeechRecognizer API 사용
- 인식 중 입력창에 실시간 텍스트 표시
- 인식 완료 시: 자동전송 ON이면 바로 전송, OFF면 입력창에 채움

### 권한
- `android.permission.RECORD_AUDIO` 런타임 권한 요청

---

## 8. 측면버튼 / 빠른 호출

### 구현 방식
- `android.app.role.ASSISTANT` 역할 요청
- 디바이스의 디지털 어시스턴트로 등록
- 측면버튼 길게 누르면 앱이 실행됨

### AssistActivity
- 별도 액티비티로 구현
- 열리면 즉시 새 대화 시작 + 음성 입력 자동 활성화
- 또는 기존 MainActivity를 포그라운드로 가져오기

### AndroidManifest 추가
```xml
<activity android:name=".AssistActivity"
    android:exported="true"
    android:theme="@style/Theme.Gemma4Mobile">
    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

---

## 9. 파일 구조 변경

```
app/app/src/main/java/com/gemma4mobile/
├── GemmaApp.kt
├── MainActivity.kt
├── AssistActivity.kt                      # 신규: 측면버튼 호출용
├── model/
│   ├── ModelTier.kt
│   ├── DeviceProfiler.kt
│   ├── ModelDownloader.kt
│   └── ModelManager.kt
├── inference/
│   └── GemmaInferenceEngine.kt
├── chat/
│   ├── ChatRepository.kt
│   └── ChatViewModel.kt                   # 수정: 시스템 프롬프트, 음성 입력 지원
├── settings/
│   ├── SettingsRepository.kt              # 신규: DataStore 기반 설정 관리
│   ├── SettingsViewModel.kt               # 신규
│   └── SystemPromptPresets.kt             # 신규: 프리셋 목록
├── db/
│   ├── AppDatabase.kt                     # 수정: migration 추가
│   ├── ChatDao.kt                         # 수정: 검색 쿼리 추가
│   └── ChatEntity.kt                     # 수정: systemPrompt 필드 추가
├── di/
│   └── AppModule.kt
└── ui/
    ├── theme/
    │   └── Theme.kt                       # 수정: 다크/라이트 전면 재설계
    ├── chat/
    │   ├── ChatScreen.kt                  # 수정: 하이브리드 레이아웃
    │   ├── UserMessageBubble.kt           # 신규: 사용자 버블
    │   ├── AiResponseCard.kt             # 신규: AI 풀와이드 + 마크다운
    │   ├── MarkdownRenderer.kt           # 신규: 마크다운 렌더링
    │   ├── CodeBlock.kt                  # 신규: 코드블록 + 구문하이라이팅
    │   ├── ModelStatusBar.kt             # 수정: 디자인 개선
    │   └── VoiceInputButton.kt           # 신규: 마이크 버튼 + STT
    ├── drawer/
    │   ├── SessionDrawer.kt              # 신규: 사이드 드로어
    │   ├── SessionItem.kt               # 신규: 세션 항목
    │   └── SessionSearch.kt             # 신규: 검색
    ├── settings/
    │   ├── SettingsScreen.kt             # 신규
    │   ├── ModelSettingsSection.kt       # 신규
    │   ├── PromptSettingsSection.kt      # 신규
    │   ├── GenerationSettingsSection.kt  # 신규
    │   ├── VoiceSettingsSection.kt       # 신규
    │   └── AssistSettingsSection.kt      # 신규
    └── onboarding/
        └── OnboardingScreen.kt           # 기존 유지
```

---

## 10. 추가 의존성

```toml
# libs.versions.toml 추가
datastore = "1.1.0"
markdown = "0.28.0"  # com.mikepenz:multiplatform-markdown-renderer

[libraries]
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
markdown-renderer = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-m3", version.ref = "markdown" }
```
