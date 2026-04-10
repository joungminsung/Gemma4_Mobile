# Gemma4 Mobile - 설계 문서

## 1. 프로젝트 개요

**Gemma4 Mobile** — Gemma 4 E2B 기반 안드로이드 온디바이스 AI 어시스턴트

- **1차 목표**: 독립 앱 + 로컬 한국어 질의응답
- **2차 목표**: 오버레이/접근성 서비스로 확장 (안드로이드 전체에서 사용)
- **추후 기능**: 말투 다듬기, 자동완성, 음성 자동전송, 검색 어시스턴트 (온라인 시 인터넷 검색 + 답변)

### 기술 스택

- **언어**: Kotlin (네이티브 Android) — AI 런타임 성능 최우선
- **UI**: Jetpack Compose
- **추론 런타임**: MediaPipe LLM Inference API
- **라이선스**: Apache 2.0

### 타겟

- Android 먼저, 이후 iOS 확장
- 한국어 최우선 (한국어 파인튜닝 필수)

---

## 2. 모델 경량화 파이프라인

### 2-1. 파이프라인 흐름

```
Gemma 4 E2B (원본)
  → Knowledge Distillation (핵심 지식 압축)
  → Layer Pruning (불필요 레이어 제거)
  → 한국어 QLoRA 파인튜닝
  → 양자화 (최종 단계)
  → MediaPipe 포맷 변환
```

### 2-2. Knowledge Distillation (지식 증류)

- **Teacher**: Gemma 4 E2B 원본 (또는 더 큰 Gemma 4 26B/31B)
- **Student**: E2B 아키텍처 기반으로 레이어/히든 사이즈를 줄인 커스텀 모델
- **학습 데이터**: 한국어 위주 코퍼스 (나무위키, 뉴스, 대화체, Q&A 데이터셋)
- Lite 티어용 모델은 여기서 대폭 축소

### 2-3. Layer Pruning (구조적 가지치기)

- Attention head, FFN 레이어 중 기여도 낮은 부분 제거
- Standard 티어는 이 단계까지 적용

### 2-4. 한국어 QLoRA 파인튜닝

- 4개 티어 모델 각각에 한국어 QLoRA 적용
- 학습 목표: 한국어 질의응답, 대화체 자연스러움
- 데이터셋: KoAlpaca, KULLM, 직접 구축한 한국어 Q&A 등

### 2-5. 최종 양자화 및 모델 티어

| 티어 | 경량화 후 파라미터 | 양자화 | 디스크 용량 | 타겟 디바이스 |
|------|-------------------|--------|------------|--------------|
| **Lite** | ~0.5B (Distillation) | INT4 | ~300MB | RAM 4GB+ |
| **Standard** | ~1.2B (Pruning) | INT8 | ~1.2GB | RAM 6GB+ |
| **Full** | ~2B (QLoRA) | INT8 | ~2GB | RAM 8GB+ |
| **Max** | ~2B (QLoRA) | FP16 | ~4GB | RAM 12GB+ |

### 2-6. MediaPipe 포맷 변환

- 튜닝 완료된 모델을 MediaPipe TFLite 포맷으로 변환
- 각 티어별 `.task` 파일 생성

---

## 3. 앱 아키텍처 (1차 — 로컬 질의응답)

### 3-1. 구조

```
┌──────────────────────────────────────┐
│     Chat UI (Jetpack Compose)        │
│  ┌───────────┐  ┌──────────────┐    │
│  │  입력/출력  │  │ 대화 히스토리  │    │
│  └─────┬─────┘  └──────┬───────┘    │
│        │               │            │
│  ┌─────▼───────────────▼─────────┐  │
│  │      AI Service Layer         │  │
│  │  - 세션 관리                   │  │
│  │  - 컨텍스트 윈도우 관리         │  │
│  │  - 스트리밍 응답 처리           │  │
│  └─────────────┬─────────────────┘  │
│                │                     │
│  ┌─────────────▼─────────────────┐  │
│  │      Model Manager            │  │
│  │  - 디바이스 사양 감지           │  │
│  │  - 티어별 모델 자동 선택        │  │
│  │  - MediaPipe LLM Runtime      │  │
│  └───────────────────────────────┘  │
└──────────────────────────────────────┘
```

### 3-2. 핵심 컴포넌트

| 컴포넌트 | 역할 |
|----------|------|
| **Model Manager** | 앱 최초 실행 시 RAM/칩셋 감지 → 적합한 티어 모델 로드 |
| **AI Service** | MediaPipe LLM Inference 호출, 스트리밍 토큰 출력, 대화 컨텍스트 유지 |
| **Chat UI** | Jetpack Compose 기반 채팅 인터페이스, 실시간 스트리밍 표시 |
| **Storage** | Room DB로 대화 히스토리 로컬 저장 |

### 3-3. 모델 배포 방식

- 앱 설치 시 모델 미포함 (APK 크기 최소화)
- 최초 실행 시 디바이스 사양 감지 → 적합한 티어 모델 다운로드
- 모델 파일은 앱 내부 저장소에 보관

### 3-4. 확장 포인트

2차 기능 추가를 위한 확장 구조:
- AI Service Layer에 기능 모듈(말투 다듬기, 자동완성 등)을 플러그인 방식으로 추가
- 오버레이 전환 시 Chat UI를 AccessibilityService + WindowManager로 교체

---

## 4. 개발 환경 및 도구

| 영역 | 도구 |
|------|------|
| **파인튜닝** | Python, HuggingFace Transformers, PEFT(QLoRA), 한국어 데이터셋 |
| **Distillation/Pruning** | PyTorch, 커스텀 스크립트 |
| **모델 변환** | MediaPipe Model Maker → `.task` 포맷 |
| **앱 개발** | Android Studio, Kotlin, Jetpack Compose |
| **추론 런타임** | MediaPipe LLM Inference API |
| **로컬 DB** | Room |
| **모델 호스팅** | Firebase Storage 또는 GCS (모델 다운로드 서빙) |

---

## 5. 개발 단계

### Phase 1 — 로컬 질의응답 (1차 목표)
- Gemma 4 E2B 한국어 파인튜닝 + 4티어 모델 생성
- Kotlin 독립 앱 + 채팅 UI
- MediaPipe 추론 + 스트리밍 응답
- 디바이스별 모델 자동 선택 + 다운로드

### Phase 2 — 오버레이 확장
- AccessibilityService + WindowManager 기반 플로팅 UI
- 안드로이드 전체에서 AI 접근 가능

### Phase 3 — 추가 기능
- 말투 다듬기 (텍스트 리라이팅)
- 자동완성 (타이핑 중 제안)
- 음성 자동전송 (STT → 메시지)
- 검색 어시스턴트 (온라인 시 인터넷 검색 + 답변)
