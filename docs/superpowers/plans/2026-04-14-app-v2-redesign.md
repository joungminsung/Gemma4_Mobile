# App v2 전면 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gemma4 Mobile 앱을 ChatGPT 스타일 다크 테마 + 마크다운 렌더링 + 세션관리 + 설정 + 음성입력 + 측면버튼 호출까지 전면 개선한다.

**Architecture:** 기존 앱 구조를 유지하면서 UI 레이어 전면 교체, 설정(DataStore), 세션 드로어, 마크다운 렌더링, 음성입력(SpeechRecognizer), 어시스턴트 호출(AssistActivity)을 추가한다.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Room, Hilt, DataStore, multiplatform-markdown-renderer, SpeechRecognizer API, LiteRT-LM

---

## Task 1: 의존성 추가 + 테마 전면 교체

**Files:**
- Modify: `app/gradle/libs.versions.toml`
- Modify: `app/app/build.gradle.kts`
- Rewrite: `app/app/src/main/java/com/gemma4mobile/ui/theme/Theme.kt`

- [ ] **Step 1: libs.versions.toml에 의존성 추가**

`[versions]` 섹션에 추가:
```toml
datastore = "1.1.0"
markdown = "0.28.0"
```

`[libraries]` 섹션에 추가:
```toml
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
markdown-renderer = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-m3", version.ref = "markdown" }
```

- [ ] **Step 2: build.gradle.kts에 의존성 추가**

```kotlin
implementation(libs.datastore.preferences)
implementation(libs.markdown.renderer)
```

- [ ] **Step 3: Theme.kt 전면 재작성**

ChatGPT 스타일 색상 체계 적용:
- 다크: 배경 `#212121`, 사용자버블 `#2f2f2f`, AI영역 `#343541`, 사이드바 `#171717`
- 라이트: 배경 `#ffffff`, 사용자버블 `#f7f7f8`, AI영역 `#f7f7f8`, 사이드바 `#f9f9f9`
- 커스텀 색상을 `ColorScheme` extension properties로 정의:
  - `ColorScheme.userBubble`, `ColorScheme.aiBackground`, `ColorScheme.sidebarBackground`
  - `ColorScheme.userIcon` (#5436DA), `ColorScheme.aiIcon` (#19c37d)
- 테마 설정값을 받아 다크/라이트/시스템 전환 지원

- [ ] **Step 4: 빌드 확인**

Run: `app/gradlew -p app assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add app/gradle/libs.versions.toml app/app/build.gradle.kts app/app/src/main/java/com/gemma4mobile/ui/theme/
git commit -m "feat(app): redesign theme with ChatGPT-style dark/light colors"
```

---

## Task 2: 설정 인프라 (DataStore + SettingsRepository)

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/settings/SettingsRepository.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/settings/SystemPromptPresets.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/di/AppModule.kt`

- [ ] **Step 1: SettingsRepository 구현**

DataStore<Preferences> 기반. 관리하는 설정값:
```kotlin
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,          // DARK, LIGHT, SYSTEM
    val systemPrompt: String = "",
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val sttLanguage: String = "ko-KR",
    val autoSendVoice: Boolean = false,
    val assistButtonEnabled: Boolean = false,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }
```

- `val settingsFlow: Flow<AppSettings>` 로 노출
- 각 설정별 `suspend fun updateXxx(value)` 메서드

- [ ] **Step 2: SystemPromptPresets 정의**

```kotlin
object SystemPromptPresets {
    val presets = listOf(
        Preset("기본", ""),
        Preset("친근한 친구", "너는 친근한 친구처럼 반말로 대화해. 이모지도 적절히 사용해."),
        Preset("전문 비서", "당신은 전문적인 비서입니다. 정확하고 간결하게 답변합니다."),
        Preset("코딩 도우미", "You are a coding assistant. Provide code examples with explanations. Use Korean for explanations."),
    )
    data class Preset(val name: String, val prompt: String)
}
```

- [ ] **Step 3: AppModule에 DataStore 제공 추가**

```kotlin
@Provides @Singleton
fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
```

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/settings/ app/app/src/main/java/com/gemma4mobile/di/
git commit -m "feat(app): add SettingsRepository with DataStore + system prompt presets"
```

---

## Task 3: DB 스키마 확장 + 세션 관리 강화

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/db/ChatEntity.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/db/ChatDao.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/db/AppDatabase.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/chat/ChatRepository.kt`

- [ ] **Step 1: ChatSession에 systemPrompt 필드 추가**

```kotlin
@Entity(tableName = "sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: ChatDao에 검색 + 이름변경 쿼리 추가**

```kotlin
@Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
fun searchSessions(query: String): Flow<List<ChatSession>>

@Query("SELECT * FROM messages WHERE sessionId IN (SELECT id FROM sessions WHERE title LIKE '%' || :query || '%') OR content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
fun searchMessages(query: String): Flow<List<ChatMessage>>

@Query("UPDATE sessions SET title = :title WHERE id = :sessionId")
suspend fun renameSession(sessionId: String, title: String)
```

- [ ] **Step 3: AppDatabase 버전 2 마이그레이션**

```kotlin
@Database(entities = [ChatMessage::class, ChatSession::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
```

Migration 1→2: `ALTER TABLE sessions ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''`

- [ ] **Step 4: ChatRepository에 세션 관리 메서드 추가**

- `renameSession(sessionId, title)`, `searchSessions(query)`
- `createSession(title, systemPrompt)` 에 systemPrompt 파라미터 추가
- 날짜별 그룹핑 유틸: `groupSessionsByDate(sessions)` → `Map<String, List<ChatSession>>` (오늘/어제/이번 주/이전)

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/db/ app/app/src/main/java/com/gemma4mobile/chat/
git commit -m "feat(app): extend DB schema with systemPrompt, search, session management"
```

---

## Task 4: 마크다운 렌더링 컴포넌트

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/MarkdownContent.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/CodeBlock.kt`

- [ ] **Step 1: MarkdownContent 컴포넌트**

`com.mikepenz:multiplatform-markdown-renderer-m3` 라이브러리의 `Markdown` 컴포저블을 래핑:

```kotlin
@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    Markdown(
        content = text,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = Color(0xFF1a1a1a),
            codeText = Color(0xFF81C995),
            linkText = Color(0xFF58a6ff),
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineSmall,
            h2 = MaterialTheme.typography.titleLarge,
            h3 = MaterialTheme.typography.titleMedium,
            body1 = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        ),
        modifier = modifier,
    )
}
```

- [ ] **Step 2: CodeBlock 컴포넌트**

코드블록 전용 — 상단에 언어명 + 복사 버튼:

```kotlin
@Composable
fun CodeBlock(code: String, language: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF0d0d0d))) {
        // Header: 언어명 + 복사 버튼
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2d2d2d)).padding(8.dp, 4.dp)) {
            Text(language, color = Color(0xFF8e8ea0), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            // 복사 아이콘 버튼 → ClipboardManager.setText
        }
        // Code body
        SelectionContainer {
            Text(code, fontFamily = FontFamily.Monospace, color = Color(0xFFd1d5db),
                 modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()))
        }
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/chat/MarkdownContent.kt app/app/src/main/java/com/gemma4mobile/ui/chat/CodeBlock.kt
git commit -m "feat(app): add markdown rendering with code block + copy button"
```

---

## Task 5: 하이브리드 채팅 UI (사용자 버블 + AI 풀와이드)

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/UserMessageBubble.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/AiResponseCard.kt`
- Rewrite: `app/app/src/main/java/com/gemma4mobile/ui/chat/ChatScreen.kt`
- Rewrite: `app/app/src/main/java/com/gemma4mobile/ui/chat/ModelStatusBar.kt`
- Delete: `app/app/src/main/java/com/gemma4mobile/ui/chat/MessageBubble.kt`

- [ ] **Step 1: UserMessageBubble**

오른쪽 정렬 버블. 배경 `userBubble` 색상, 라운드 16dp(우하단 4dp), 최대폭 80%.

- [ ] **Step 2: AiResponseCard**

풀 와이드. 좌측에 초록 원형 아이콘(✦), 우측에 `MarkdownContent`로 AI 응답 렌더링. 하단에 복사/공유 아이콘 버튼. 스트리밍 중에는 마크다운 미적용(plain text), 완료 후 마크다운 렌더링.

- [ ] **Step 3: ChatScreen 재작성**

- `ModalNavigationDrawer`로 감싸서 사이드 드로어 연동 (드로어 내용은 Task 6에서)
- 상단: 햄버거 메뉴 + 세션 제목 + 새 대화(✎) 버튼
- 중앙: `LazyColumn`에 `UserMessageBubble` / `AiResponseCard` 교차 배치
- 하단: 입력창 (라운드 24dp, `#2f2f2f` 배경, `#40414f` 테두리) + 전송/마이크 버튼
  - 텍스트 비어있으면 마이크 버튼, 입력 있으면 전송 버튼
- 빈 화면 (메시지 없을 때): 중앙에 "✦ Gemma 4" 로고 + "무엇이든 물어보세요"

- [ ] **Step 4: ModelStatusBar 제거 또는 상단 바에 통합**

모델 정보를 상단 바 subtitle로 표시: "Gemma 4 · Lite"
생성 중 상태는 입력창 위에 "생성 중..." 인디케이터로 표시.

- [ ] **Step 5: 이전 MessageBubble.kt 삭제**

- [ ] **Step 6: 빌드 확인 + 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/chat/
git commit -m "feat(app): hybrid chat UI — user bubble + AI fullwide markdown"
```

---

## Task 6: 사이드 드로어 (세션 목록)

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/drawer/SessionDrawer.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/drawer/SessionItem.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/drawer/SessionSearch.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt`

- [ ] **Step 1: SessionDrawer**

`ModalDrawerSheet` 안에:
- 상단: 앱 로고 "Gemma 4" + 새 대화 버튼(✎)
- 검색 입력창 (`SessionSearch`)
- 날짜별 그룹핑된 세션 리스트 (`LazyColumn`)
- 하단: "⚙ 설정" 버튼 → 설정 화면으로 네비게이션
- 배경색: `#171717` (다크)

- [ ] **Step 2: SessionItem**

개별 세션 항목:
- 현재 세션 하이라이트 (배경 약간 밝게)
- 클릭 → 해당 세션 로드
- 롱프레스 → `DropdownMenu`로 "이름 변경" / "삭제" 표시

- [ ] **Step 3: SessionSearch**

`OutlinedTextField`로 검색어 입력 → `ChatRepository.searchSessions()` 호출 → 결과 필터링

- [ ] **Step 4: ChatViewModel 확장**

- `sessions: StateFlow<Map<String, List<ChatSession>>>` — 날짜별 그룹핑된 세션 목록
- `switchSession(sessionId)` — 세션 전환
- `createNewSession()` — 새 세션 생성 + 전환
- `renameSession(sessionId, title)`, `deleteSession(sessionId)`
- `searchQuery: MutableStateFlow<String>` — 검색어

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/drawer/ app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt
git commit -m "feat(app): add session drawer with search, grouping, rename, delete"
```

---

## Task 7: 설정 화면

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/settings/SettingsViewModel.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/SettingsScreen.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/ModelSettingsSection.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/PromptSettingsSection.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/GenerationSettingsSection.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/VoiceSettingsSection.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/settings/AssistSettingsSection.kt`

- [ ] **Step 1: SettingsViewModel**

`@HiltViewModel`. `SettingsRepository`를 주입받아 각 설정값을 `StateFlow`로 노출. 각 설정별 update 메서드.

- [ ] **Step 2: SettingsScreen**

`LazyColumn` 기반. 각 섹션을 카드 형태로 나열:
- 모델 관리, 시스템 프롬프트, 생성 파라미터, 테마, 대화 내보내기, 음성 입력, 빠른 호출
- 상단에 "← 설정" 뒤로가기 버튼
- ChatGPT 설정 화면과 유사한 스타일 (어두운 배경 + 구분선)

- [ ] **Step 3: ModelSettingsSection**

- 현재 모델 티어 표시 (칩)
- 티어 변경 다이얼로그 (4개 티어 라디오 버튼)
- 모델 삭제 버튼 + 확인 다이얼로그
- 디스크 사용량 표시

- [ ] **Step 4: PromptSettingsSection**

- 프리셋 드롭다운 (기본/친근한 친구/전문 비서/코딩 도우미)
- 커스텀 입력 `TextField` (여러 줄)
- 저장 버튼

- [ ] **Step 5: GenerationSettingsSection**

- Temperature `Slider` (0.0~2.0) + 현재 값 텍스트
- Top-P `Slider` (0.0~1.0)
- Max Tokens `Slider` (64~2048)
- 각 파라미터 아래 1줄 설명

- [ ] **Step 6: 나머지 섹션**

- 테마: 라디오 버튼 3개 (다크/라이트/시스템)
- 대화 내보내기: "현재 대화 복사" + "공유" 버튼
- 음성 입력: STT 언어 드롭다운 + 자동전송 토글
- 빠른 호출: 활성화 토글 + 설정 방법 안내 텍스트

- [ ] **Step 7: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/settings/ app/app/src/main/java/com/gemma4mobile/ui/settings/
git commit -m "feat(app): add full settings screen with all 7 sections"
```

---

## Task 8: 음성 입력 (STT)

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/VoiceInputButton.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt`
- Modify: `app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AndroidManifest에 권한 추가**

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 2: VoiceInputButton 구현**

```kotlin
@Composable
fun VoiceInputButton(
    onResult: (String) -> Unit,
    onListening: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Android SpeechRecognizer API 사용
    // 버튼 클릭 → 런타임 권한 요청 → SpeechRecognizer 시작
    // 인식 중: 아이콘 애니메이션 (펄스)
    // onPartialResults → onResult로 실시간 전달
    // onResults → 최종 결과 전달
}
```

주요 구현:
- `SpeechRecognizer.createSpeechRecognizer(context)`
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` + `LANGUAGE_MODEL_FREE_FORM`
- `RecognitionListener` 콜백으로 부분/최종 결과 처리
- STT 언어는 `SettingsRepository`에서 가져옴

- [ ] **Step 3: ChatScreen 입력창에 통합**

입력창 오른쪽:
- 텍스트 비어있으면 → `VoiceInputButton` 표시
- 텍스트 있으면 → 전송 `IconButton` 표시
- 자동전송 설정 ON이면 → 음성 인식 완료 시 `sendMessage()` 자동 호출

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/chat/VoiceInputButton.kt app/app/src/main/AndroidManifest.xml
git commit -m "feat(app): add voice input with SpeechRecognizer + auto-send"
```

---

## Task 9: 측면버튼 / 어시스턴트 호출

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/AssistActivity.kt`
- Modify: `app/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: AssistActivity 구현**

```kotlin
@AndroidEntryPoint
class AssistActivity : ComponentActivity() {
    @Inject lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 이미 모델이 로드되어 있으면 → 바로 채팅 화면
        // 아니면 → 모델 로드 후 채팅 화면
        // 음성 입력 자동 활성화
        setContent {
            Gemma4MobileTheme {
                // ChatScreen(autoVoice = true)
                // 또는 기존 MainActivity로 인텐트 전달 후 finish
            }
        }
    }
}
```

구현 방식: AssistActivity는 thin launcher — `MainActivity`에 `ACTION_ASSIST` extra를 붙여 startActivity 후 자기는 finish. MainActivity에서 extra 확인 후 새 대화 + 음성 입력 자동 시작.

- [ ] **Step 2: AndroidManifest 추가**

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

- [ ] **Step 3: 설정 화면 안내 텍스트**

AssistSettingsSection에서: "기기 설정 → 앱 → 기본 앱 → 디지털 어시스턴트 앱에서 'Gemma4'를 선택하세요"

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/AssistActivity.kt app/app/src/main/AndroidManifest.xml
git commit -m "feat(app): add AssistActivity for side button quick launch"
```

---

## Task 10: 네비게이션 통합 + ChatViewModel 완성 + MainActivity 수정

**Files:**
- Modify: `app/app/src/main/java/com/gemma4mobile/MainActivity.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/inference/GemmaInferenceEngine.kt`

- [ ] **Step 1: ChatViewModel 완성**

- 시스템 프롬프트 적용: `SettingsRepository`에서 systemPrompt 가져와 `Engine`의 conversation에 적용
- 생성 파라미터 적용: temperature, topP, maxTokens를 `SamplerConfig`에 전달
- 대화 내보내기: `exportChat()` → 전체 대화를 텍스트로 변환 → 클립보드 복사 또는 공유 인텐트

- [ ] **Step 2: GemmaInferenceEngine에 시스템 프롬프트 + 파라미터 지원 추가**

```kotlin
fun resetConversation(systemPrompt: String? = null, samplerConfig: SamplerConfig? = null) {
    conversation?.close()
    val config = ConversationConfig(
        systemInstruction = systemPrompt?.let { Contents.of(it) },
        samplerConfig = samplerConfig ?: SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
    )
    conversation = engine?.createConversation(config)
}
```

- [ ] **Step 3: MainActivity 수정**

- 온보딩 완료 후 → `ChatScreen` (드로어 포함)
- `ACTION_ASSIST` 인텐트 extra 감지 → 새 대화 + 음성 자동 시작
- 설정 화면 네비게이션: 드로어의 "설정" → `SettingsScreen` 표시 (NavHost 또는 단순 상태 전환)

- [ ] **Step 4: 전체 빌드 + 테스트**

Run: `app/gradlew -p app assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add -A
git commit -m "feat(app): integrate navigation, settings, system prompt, voice assist"
```

---

## Task 11: 최종 빌드 + 디바이스 설치 + 테스트

- [ ] **Step 1: 전체 빌드**

```bash
export JAVA_HOME=/Users/dgsw36/Library/Java/JavaVirtualMachines/jbr-17.0.11/Contents/Home
app/gradlew -p app assembleDebug --no-daemon
```

- [ ] **Step 2: 디바이스 설치**

```bash
adb install -r app/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: 테스트 체크리스트**

- [ ] 앱 실행 → 온보딩 → 개발자 모드 → 모델 로드
- [ ] 채팅 전송 → AI 응답 마크다운 렌더링 확인
- [ ] 코드블록 복사 버튼 동작
- [ ] 사이드 드로어 열기 → 세션 목록 표시
- [ ] 새 대화 생성 → 세션 전환
- [ ] 세션 롱프레스 → 이름 변경/삭제
- [ ] 드로어 검색 동작
- [ ] 설정 화면 진입 → 각 설정 변경 → 반영 확인
- [ ] 마이크 버튼 → 음성 인식 → 텍스트 입력
- [ ] 다크/라이트 테마 전환

- [ ] **Step 4: 최종 커밋 + push**

```bash
git add -A
git commit -m "feat(app): complete v2 redesign — ChatGPT-style UI, markdown, settings, voice, assist"
git push origin master
```

---

## 실행 순서 요약

```
Task 1:  테마 + 의존성 (기반)
Task 2:  설정 인프라 (DataStore)
Task 3:  DB 확장 + 세션 관리
Task 4:  마크다운 렌더링 컴포넌트
Task 5:  하이브리드 채팅 UI (핵심)
Task 6:  사이드 드로어
Task 7:  설정 화면
Task 8:  음성 입력
Task 9:  측면버튼 호출
Task 10: 전체 통합
Task 11: 빌드 + 테스트
```

Task 1~4는 독립적으로 병렬 가능. Task 5는 1,4 완료 후. Task 6은 3,5 완료 후. Task 7은 2 완료 후. Task 8~9는 독립. Task 10은 전체 통합.
