# Gemma4 Mobile Phase 1 — 로컬 질의응답 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gemma 4 E2B를 한국어 파인튜닝하고, 4티어로 경량화하여, Android 앱에서 온디바이스 질의응답이 가능하도록 한다.

**Architecture:** 모델 파이프라인(Python)과 Android 앱(Kotlin)의 두 트랙으로 분리. 모델 파이프라인은 E2B를 Distillation/Pruning/QLoRA로 4티어 모델을 생성하고 MediaPipe 포맷으로 변환. Android 앱은 Jetpack Compose 채팅 UI + MediaPipe LLM Inference로 스트리밍 추론.

**Tech Stack:** Python, PyTorch, HuggingFace Transformers, PEFT, MediaPipe, Kotlin, Jetpack Compose, Room, Firebase Storage

---

## 파일 구조

```
Gemma4_Mobile/
├── model/                              # 모델 파이프라인 (Python)
│   ├── requirements.txt                # Python 의존성
│   ├── config/
│   │   ├── distillation_config.yaml    # Distillation 하이퍼파라미터
│   │   ├── pruning_config.yaml         # Pruning 설정
│   │   └── qlora_config.yaml           # QLoRA 하이퍼파라미터
│   ├── data/
│   │   └── prepare_dataset.py          # 한국어 데이터셋 다운로드/전처리
│   ├── distillation/
│   │   ├── student_model.py            # Student 모델 아키텍처 정의
│   │   └── train_distillation.py       # Distillation 학습 스크립트
│   ├── pruning/
│   │   └── prune_model.py              # Layer Pruning 스크립트
│   ├── finetune/
│   │   └── train_qlora.py              # QLoRA 한국어 파인튜닝
│   ├── quantize/
│   │   └── quantize_model.py           # INT4/INT8/FP16 양자화
│   ├── convert/
│   │   └── convert_mediapipe.py        # MediaPipe .task 포맷 변환
│   └── validate/
│       └── benchmark.py                # 모델 품질/속도 벤치마크
│
├── app/                                # Android 앱 (Kotlin)
│   ├── build.gradle.kts                # 루트 Gradle
│   ├── app/
│   │   ├── build.gradle.kts            # 앱 모듈 Gradle
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/gemma4mobile/
│   │       │   │   ├── GemmaApp.kt                    # Application 클래스
│   │       │   │   ├── MainActivity.kt                 # 메인 액티비티
│   │       │   │   ├── model/
│   │       │   │   │   ├── DeviceProfiler.kt           # 디바이스 사양 감지
│   │       │   │   │   ├── ModelTier.kt                # 티어 enum + 선택 로직
│   │       │   │   │   ├── ModelDownloader.kt           # 모델 다운로드 매니저
│   │       │   │   │   └── ModelManager.kt              # 모델 라이프사이클 관리
│   │       │   │   ├── inference/
│   │       │   │   │   └── GemmaInferenceEngine.kt     # MediaPipe LLM 래퍼
│   │       │   │   ├── chat/
│   │       │   │   │   ├── ChatRepository.kt           # 대화 데이터 관리
│   │       │   │   │   └── ChatViewModel.kt            # UI 상태 관리
│   │       │   │   ├── db/
│   │       │   │   │   ├── AppDatabase.kt              # Room DB 정의
│   │       │   │   │   ├── ChatDao.kt                  # 대화 DAO
│   │       │   │   │   └── ChatEntity.kt               # 대화 엔티티
│   │       │   │   └── ui/
│   │       │   │       ├── theme/
│   │       │   │       │   └── Theme.kt                # 앱 테마
│   │       │   │       └── chat/
│   │       │   │           ├── ChatScreen.kt           # 채팅 메인 화면
│   │       │   │           ├── MessageBubble.kt        # 메시지 버블 컴포넌트
│   │       │   │           └── ModelStatusBar.kt       # 모델 상태 표시 바
│   │       │   └── res/
│   │       │       └── ...
│   │       └── test/
│   │           └── java/com/gemma4mobile/
│   │               ├── model/
│   │               │   ├── DeviceProfilerTest.kt
│   │               │   ├── ModelTierTest.kt
│   │               │   └── ModelDownloaderTest.kt
│   │               ├── inference/
│   │               │   └── GemmaInferenceEngineTest.kt
│   │               ├── chat/
│   │               │   ├── ChatRepositoryTest.kt
│   │               │   └── ChatViewModelTest.kt
│   │               └── db/
│   │                   └── ChatDaoTest.kt
│   └── gradle/
│       └── libs.versions.toml          # 버전 카탈로그
│
└── docs/
    └── superpowers/
        ├── specs/
        │   └── 2026-04-10-gemma4-mobile-design.md
        └── plans/
            └── 2026-04-10-gemma4-mobile-phase1.md
```

---

# Part A: 모델 파이프라인 (Python)

---

### Task 1: 모델 파이프라인 환경 구축

**Files:**
- Create: `model/requirements.txt`
- Create: `model/config/qlora_config.yaml`
- Create: `model/config/distillation_config.yaml`
- Create: `model/config/pruning_config.yaml`

- [ ] **Step 1: requirements.txt 작성**

```txt
torch>=2.3.0
transformers>=4.45.0
peft>=0.13.0
datasets>=3.0.0
accelerate>=1.0.0
bitsandbytes>=0.44.0
mediapipe>=0.10.18
sentencepiece>=0.2.0
protobuf>=5.28.0
pyyaml>=6.0
tqdm>=4.66.0
evaluate>=0.4.0
scipy>=1.14.0
```

- [ ] **Step 2: QLoRA 설정 파일 작성**

```yaml
# model/config/qlora_config.yaml
model:
  base_model: "google/gemma-4-e2b"
  max_seq_length: 2048

lora:
  r: 64
  lora_alpha: 128
  lora_dropout: 0.05
  target_modules:
    - "q_proj"
    - "k_proj"
    - "v_proj"
    - "o_proj"
    - "gate_proj"
    - "up_proj"
    - "down_proj"
  task_type: "CAUSAL_LM"

training:
  num_epochs: 3
  per_device_train_batch_size: 4
  gradient_accumulation_steps: 4
  learning_rate: 2.0e-4
  warmup_ratio: 0.03
  weight_decay: 0.001
  fp16: false
  bf16: true
  logging_steps: 10
  save_steps: 500
  output_dir: "./output/qlora"

quantization:
  load_in_4bit: true
  bnb_4bit_compute_dtype: "bfloat16"
  bnb_4bit_quant_type: "nf4"
  bnb_4bit_use_double_quant: true
```

- [ ] **Step 3: Distillation 설정 파일 작성**

```yaml
# model/config/distillation_config.yaml
teacher:
  model: "google/gemma-4-e2b"

student:
  hidden_size: 1024
  num_hidden_layers: 12
  num_attention_heads: 8
  intermediate_size: 4096
  vocab_size: 262144  # Gemma 4 토크나이저와 동일

training:
  num_epochs: 5
  per_device_train_batch_size: 8
  gradient_accumulation_steps: 8
  learning_rate: 5.0e-4
  warmup_ratio: 0.05
  temperature: 2.0
  alpha_ce: 0.5       # cross-entropy loss 가중치
  alpha_kd: 0.5       # KD loss 가중치
  max_seq_length: 1024
  output_dir: "./output/distillation"
```

- [ ] **Step 4: Pruning 설정 파일 작성**

```yaml
# model/config/pruning_config.yaml
model:
  base_model: "google/gemma-4-e2b"

pruning:
  target_sparsity: 0.4           # 40% 레이어 제거
  importance_metric: "taylor"     # Taylor importance scoring
  pruning_targets:
    - "attention_heads"
    - "ffn_layers"
  min_layers: 12                  # 최소 유지 레이어 수
  calibration_samples: 512        # 중요도 측정용 샘플 수

output:
  output_dir: "./output/pruning"
```

- [ ] **Step 5: 의존성 설치 확인**

Run: `cd model && pip install -r requirements.txt`
Expected: 모든 패키지 설치 성공

- [ ] **Step 6: Gemma 4 E2B 모델 접근 확인**

Run:
```bash
python -c "
from transformers import AutoTokenizer
tokenizer = AutoTokenizer.from_pretrained('google/gemma-4-e2b')
print(f'Vocab size: {tokenizer.vocab_size}')
print('Model access OK')
"
```
Expected: 토크나이저 로드 성공, vocab size 출력

- [ ] **Step 7: 커밋**

```bash
git add model/requirements.txt model/config/
git commit -m "feat(model): add pipeline environment and config files"
```

---

### Task 2: 한국어 데이터셋 준비

**Files:**
- Create: `model/data/prepare_dataset.py`

- [ ] **Step 1: 데이터셋 준비 스크립트 작성**

```python
# model/data/prepare_dataset.py
"""한국어 Q&A 데이터셋 다운로드 및 전처리."""

import argparse
from datasets import load_dataset, concatenate_datasets, Dataset


def load_koalpaca() -> Dataset:
    """KoAlpaca 데이터셋 로드 및 포맷 변환."""
    ds = load_dataset("beomi/KoAlpaca-v1.1a", split="train")
    return ds.map(lambda x: {
        "instruction": x["instruction"],
        "input": x.get("input", ""),
        "output": x["output"],
    })


def load_kullm() -> Dataset:
    """KULLM v2 데이터셋 로드 및 포맷 변환."""
    ds = load_dataset("nlpai-lab/kullm-v2", split="train")
    return ds.map(lambda x: {
        "instruction": x["instruction"],
        "input": x.get("input", ""),
        "output": x["output"],
    })


def format_for_gemma(example: dict) -> dict:
    """Gemma 채팅 포맷으로 변환."""
    user_msg = example["instruction"]
    if example["input"]:
        user_msg += f"\n\n{example['input']}"

    text = (
        f"<start_of_turn>user\n{user_msg}<end_of_turn>\n"
        f"<start_of_turn>model\n{example['output']}<end_of_turn>"
    )
    return {"text": text}


def main():
    parser = argparse.ArgumentParser(description="한국어 데이터셋 준비")
    parser.add_argument("--output_dir", type=str, default="./data/processed")
    parser.add_argument("--max_samples", type=int, default=None,
                        help="데이터셋 최대 샘플 수 (디버깅용)")
    parser.add_argument("--test_split", type=float, default=0.05)
    args = parser.parse_args()

    print("Loading KoAlpaca...")
    koalpaca = load_koalpaca()
    print(f"  KoAlpaca: {len(koalpaca)} samples")

    print("Loading KULLM v2...")
    kullm = load_kullm()
    print(f"  KULLM v2: {len(kullm)} samples")

    combined = concatenate_datasets([koalpaca, kullm])
    combined = combined.shuffle(seed=42)

    if args.max_samples:
        combined = combined.select(range(min(args.max_samples, len(combined))))

    print(f"Combined: {len(combined)} samples")

    formatted = combined.map(format_for_gemma, remove_columns=combined.column_names)

    split = formatted.train_test_split(test_size=args.test_split, seed=42)
    split["train"].save_to_disk(f"{args.output_dir}/train")
    split["test"].save_to_disk(f"{args.output_dir}/test")

    print(f"Train: {len(split['train'])} samples")
    print(f"Test:  {len(split['test'])} samples")
    print(f"Saved to {args.output_dir}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 소량 데이터로 실행 테스트**

Run:
```bash
cd model && python data/prepare_dataset.py --max_samples 100 --output_dir ./data/test_processed
```
Expected: `Train: 95 samples`, `Test: 5 samples`, `Saved to ./data/test_processed` 출력

- [ ] **Step 3: 전체 데이터셋 준비**

Run:
```bash
cd model && python data/prepare_dataset.py --output_dir ./data/processed
```
Expected: 전체 샘플 수 출력, `./data/processed/train`, `./data/processed/test` 디렉토리 생성

- [ ] **Step 4: 커밋**

```bash
git add model/data/prepare_dataset.py
git commit -m "feat(model): add Korean dataset preparation script"
```

---

### Task 3: Full/Max 티어 — QLoRA 한국어 파인튜닝

**Files:**
- Create: `model/finetune/train_qlora.py`

Full(INT8)과 Max(FP16)는 같은 모델 — E2B 원본 구조에 QLoRA만 적용. 양자화 단계에서 티어가 갈림.

- [ ] **Step 1: QLoRA 학습 스크립트 작성**

```python
# model/finetune/train_qlora.py
"""Gemma 4 E2B 한국어 QLoRA 파인튜닝."""

import argparse
import yaml
import torch
from datasets import load_from_disk
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from trl import SFTTrainer


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config/qlora_config.yaml")
    parser.add_argument("--data_dir", type=str, default="data/processed")
    args = parser.parse_args()

    cfg = load_config(args.config)

    # 양자화 설정 (4-bit로 로드하여 메모리 절약)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=cfg["quantization"]["load_in_4bit"],
        bnb_4bit_compute_dtype=getattr(torch, cfg["quantization"]["bnb_4bit_compute_dtype"]),
        bnb_4bit_quant_type=cfg["quantization"]["bnb_4bit_quant_type"],
        bnb_4bit_use_double_quant=cfg["quantization"]["bnb_4bit_use_double_quant"],
    )

    # 모델 로드
    model_name = cfg["model"]["base_model"]
    print(f"Loading model: {model_name}")

    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        quantization_config=bnb_config,
        device_map="auto",
        torch_dtype=torch.bfloat16,
    )
    model = prepare_model_for_kbit_training(model)

    # LoRA 설정
    lora_cfg = cfg["lora"]
    peft_config = LoraConfig(
        r=lora_cfg["r"],
        lora_alpha=lora_cfg["lora_alpha"],
        lora_dropout=lora_cfg["lora_dropout"],
        target_modules=lora_cfg["target_modules"],
        task_type=lora_cfg["task_type"],
        bias="none",
    )
    model = get_peft_model(model, peft_config)
    model.print_trainable_parameters()

    # 데이터셋 로드
    train_dataset = load_from_disk(f"{args.data_dir}/train")
    eval_dataset = load_from_disk(f"{args.data_dir}/test")
    print(f"Train: {len(train_dataset)}, Eval: {len(eval_dataset)}")

    # 학습 설정
    train_cfg = cfg["training"]
    training_args = TrainingArguments(
        output_dir=train_cfg["output_dir"],
        num_train_epochs=train_cfg["num_epochs"],
        per_device_train_batch_size=train_cfg["per_device_train_batch_size"],
        gradient_accumulation_steps=train_cfg["gradient_accumulation_steps"],
        learning_rate=train_cfg["learning_rate"],
        warmup_ratio=train_cfg["warmup_ratio"],
        weight_decay=train_cfg["weight_decay"],
        fp16=train_cfg["fp16"],
        bf16=train_cfg["bf16"],
        logging_steps=train_cfg["logging_steps"],
        save_steps=train_cfg["save_steps"],
        eval_strategy="steps",
        eval_steps=train_cfg["save_steps"],
        save_total_limit=3,
        report_to="none",
        dataloader_pin_memory=True,
    )

    # 학습
    trainer = SFTTrainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        processing_class=tokenizer,
        max_seq_length=cfg["model"]["max_seq_length"],
        dataset_text_field="text",
    )

    print("Starting training...")
    trainer.train()

    # LoRA 어댑터 저장
    adapter_path = f"{train_cfg['output_dir']}/final_adapter"
    model.save_pretrained(adapter_path)
    tokenizer.save_pretrained(adapter_path)
    print(f"Adapter saved to {adapter_path}")

    # LoRA 머지 후 전체 모델 저장
    print("Merging LoRA weights...")
    merged_model = model.merge_and_unload()
    merged_path = f"{train_cfg['output_dir']}/merged_model"
    merged_model.save_pretrained(merged_path)
    tokenizer.save_pretrained(merged_path)
    print(f"Merged model saved to {merged_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 소량 데이터로 학습 파이프라인 테스트**

`config/qlora_config.yaml`의 `num_epochs`를 1, `max_samples`를 50으로 변경하여 빠르게 검증:

Run:
```bash
cd model && python finetune/train_qlora.py \
  --config config/qlora_config.yaml \
  --data_dir data/test_processed
```
Expected: 학습 시작 → loss 출력 → 어댑터 저장 → 머지 완료

- [ ] **Step 3: 전체 데이터로 학습 실행**

Run:
```bash
cd model && python finetune/train_qlora.py \
  --config config/qlora_config.yaml \
  --data_dir data/processed
```
Expected: `output/qlora/merged_model/` 에 전체 모델 저장

- [ ] **Step 4: 커밋**

```bash
git add model/finetune/train_qlora.py
git commit -m "feat(model): add QLoRA Korean fine-tuning script for Full/Max tiers"
```

---

### Task 4: Standard 티어 — Layer Pruning

**Files:**
- Create: `model/pruning/prune_model.py`

- [ ] **Step 1: Pruning 스크립트 작성**

```python
# model/pruning/prune_model.py
"""Gemma 4 E2B Layer Pruning — Standard 티어용 (~1.2B)."""

import argparse
import yaml
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from datasets import load_from_disk


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def compute_layer_importance(model, calibration_data, tokenizer, num_samples: int):
    """Taylor importance scoring으로 각 레이어 중요도 계산."""
    model.eval()
    layer_importance = {}

    # 각 레이어에 그래디언트 훅 등록
    hooks = []
    activations = {}

    def make_hook(name):
        def hook_fn(module, input, output):
            if isinstance(output, tuple):
                output = output[0]
            activations[name] = output.detach()
        return hook_fn

    for name, module in model.named_modules():
        if "layers." in name and name.endswith(".self_attn"):
            hooks.append(module.register_forward_hook(make_hook(name)))
        elif "layers." in name and name.endswith(".mlp"):
            hooks.append(module.register_forward_hook(make_hook(name)))

    # 캘리브레이션 데이터로 중요도 측정
    for i, sample in enumerate(calibration_data.select(range(num_samples))):
        inputs = tokenizer(
            sample["text"],
            return_tensors="pt",
            truncation=True,
            max_length=512,
        ).to(model.device)

        with torch.no_grad():
            model(**inputs)

        for name, act in activations.items():
            score = act.abs().mean().item()
            layer_importance[name] = layer_importance.get(name, 0) + score

    for hook in hooks:
        hook.remove()

    # 평균 점수
    for name in layer_importance:
        layer_importance[name] /= num_samples

    return layer_importance


def prune_layers(model, layer_importance: dict, target_sparsity: float, min_layers: int):
    """중요도 낮은 레이어 제거."""
    # 레이어 인덱스별 총 중요도 계산
    layer_scores = {}
    for name, score in layer_importance.items():
        # "model.layers.5.self_attn" → 5
        parts = name.split(".")
        for i, p in enumerate(parts):
            if p == "layers" and i + 1 < len(parts):
                layer_idx = int(parts[i + 1])
                layer_scores[layer_idx] = layer_scores.get(layer_idx, 0) + score
                break

    total_layers = len(model.model.layers)
    num_to_remove = int(total_layers * target_sparsity)
    num_to_keep = max(total_layers - num_to_remove, min_layers)
    num_to_remove = total_layers - num_to_keep

    # 중요도 낮은 순으로 정렬
    sorted_layers = sorted(layer_scores.items(), key=lambda x: x[1])
    layers_to_remove = sorted(
        [idx for idx, _ in sorted_layers[:num_to_remove]],
        reverse=True,
    )

    print(f"Total layers: {total_layers}")
    print(f"Removing {len(layers_to_remove)} layers: {layers_to_remove}")
    print(f"Keeping {num_to_keep} layers")

    # 레이어 제거 (뒤에서부터)
    for idx in layers_to_remove:
        del model.model.layers[idx]

    # config 업데이트
    model.config.num_hidden_layers = len(model.model.layers)

    return model


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config/pruning_config.yaml")
    parser.add_argument("--model_path", type=str, default="output/qlora/merged_model",
                        help="파인튜닝된 모델 경로 (또는 원본 모델)")
    parser.add_argument("--data_dir", type=str, default="data/processed")
    parser.add_argument("--output_dir", type=str, default="output/pruning/standard_model")
    args = parser.parse_args()

    cfg = load_config(args.config)

    print(f"Loading model from {args.model_path}...")
    tokenizer = AutoTokenizer.from_pretrained(args.model_path)
    model = AutoModelForCausalLM.from_pretrained(
        args.model_path,
        device_map="auto",
        torch_dtype=torch.bfloat16,
    )

    print(f"Original params: {sum(p.numel() for p in model.parameters()) / 1e9:.2f}B")

    # 캘리브레이션 데이터 로드
    cal_data = load_from_disk(f"{args.data_dir}/train")

    # 레이어 중요도 계산
    print("Computing layer importance...")
    importance = compute_layer_importance(
        model, cal_data, tokenizer,
        num_samples=cfg["pruning"]["calibration_samples"],
    )

    # Pruning 실행
    print("Pruning layers...")
    model = prune_layers(
        model,
        importance,
        target_sparsity=cfg["pruning"]["target_sparsity"],
        min_layers=cfg["pruning"]["min_layers"],
    )

    print(f"Pruned params: {sum(p.numel() for p in model.parameters()) / 1e9:.2f}B")

    # 저장
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Pruned model saved to {args.output_dir}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Pruning 후 QLoRA 재파인튜닝**

Pruning된 모델은 성능 복구를 위해 QLoRA 재파인튜닝이 필요:

Run:
```bash
cd model && python finetune/train_qlora.py \
  --config config/qlora_config.yaml \
  --data_dir data/processed \
  --base_model output/pruning/standard_model
```
Note: `train_qlora.py`에 `--base_model` 인자를 추가하여 커스텀 모델 경로 지원 필요. `config`의 `base_model` 대신 이 경로를 우선 사용하도록 수정.

Expected: Pruned + 재파인튜닝된 모델이 `output/qlora_standard/merged_model`에 저장

- [ ] **Step 3: 커밋**

```bash
git add model/pruning/prune_model.py
git commit -m "feat(model): add layer pruning script for Standard tier"
```

---

### Task 5: Lite 티어 — Knowledge Distillation

**Files:**
- Create: `model/distillation/student_model.py`
- Create: `model/distillation/train_distillation.py`

- [ ] **Step 1: Student 모델 아키텍처 정의**

```python
# model/distillation/student_model.py
"""Lite 티어용 Student 모델 아키텍처 (~0.5B)."""

import yaml
from transformers import GemmaConfig, GemmaForCausalLM, AutoTokenizer


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def create_student_model(config_path: str, teacher_model_name: str):
    """Teacher의 토크나이저를 공유하면서 축소된 Student 모델 생성."""
    cfg = load_config(config_path)
    student_cfg = cfg["student"]

    # Teacher 토크나이저 로드 (vocab 공유)
    tokenizer = AutoTokenizer.from_pretrained(teacher_model_name)

    # Student config — Gemma 아키텍처 기반으로 축소
    config = GemmaConfig(
        vocab_size=student_cfg["vocab_size"],
        hidden_size=student_cfg["hidden_size"],
        num_hidden_layers=student_cfg["num_hidden_layers"],
        num_attention_heads=student_cfg["num_attention_heads"],
        intermediate_size=student_cfg["intermediate_size"],
        max_position_embeddings=2048,
    )

    model = GemmaForCausalLM(config)
    param_count = sum(p.numel() for p in model.parameters()) / 1e9

    print(f"Student model created:")
    print(f"  Hidden size: {student_cfg['hidden_size']}")
    print(f"  Layers: {student_cfg['num_hidden_layers']}")
    print(f"  Heads: {student_cfg['num_attention_heads']}")
    print(f"  Parameters: {param_count:.2f}B")

    return model, tokenizer


if __name__ == "__main__":
    model, tokenizer = create_student_model(
        "config/distillation_config.yaml",
        "google/gemma-4-e2b",
    )
```

- [ ] **Step 2: Student 모델 생성 테스트**

Run:
```bash
cd model && python distillation/student_model.py
```
Expected: `Parameters: ~0.5B` 출력, 모델 생성 성공

- [ ] **Step 3: Distillation 학습 스크립트 작성**

```python
# model/distillation/train_distillation.py
"""Knowledge Distillation: Teacher(E2B) → Student(0.5B) for Lite tier."""

import argparse
import yaml
import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader
from transformers import AutoModelForCausalLM, AutoTokenizer, get_linear_schedule_with_warmup
from datasets import load_from_disk
from tqdm import tqdm

from student_model import create_student_model


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def distillation_loss(student_logits, teacher_logits, labels, temperature, alpha_ce, alpha_kd):
    """KD loss = alpha_kd * KL(soft_teacher || soft_student) + alpha_ce * CE(student, labels)."""
    # KD loss (soft targets)
    soft_student = F.log_softmax(student_logits / temperature, dim=-1)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=-1)
    kd_loss = F.kl_div(soft_student, soft_teacher, reduction="batchmean") * (temperature ** 2)

    # CE loss (hard targets)
    ce_loss = F.cross_entropy(
        student_logits.view(-1, student_logits.size(-1)),
        labels.view(-1),
        ignore_index=-100,
    )

    return alpha_kd * kd_loss + alpha_ce * ce_loss


def collate_fn(batch, tokenizer, max_length):
    texts = [item["text"] for item in batch]
    encoded = tokenizer(
        texts,
        return_tensors="pt",
        padding=True,
        truncation=True,
        max_length=max_length,
    )
    encoded["labels"] = encoded["input_ids"].clone()
    return encoded


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config/distillation_config.yaml")
    parser.add_argument("--data_dir", type=str, default="data/processed")
    parser.add_argument("--output_dir", type=str, default="output/distillation/lite_model")
    args = parser.parse_args()

    cfg = load_config(args.config)
    train_cfg = cfg["training"]
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    # Teacher 로드 (frozen)
    teacher_name = cfg["teacher"]["model"]
    print(f"Loading teacher: {teacher_name}")
    teacher = AutoModelForCausalLM.from_pretrained(
        teacher_name,
        device_map="auto",
        torch_dtype=torch.bfloat16,
    )
    teacher.eval()
    for param in teacher.parameters():
        param.requires_grad = False

    # Student 생성
    print("Creating student model...")
    student, tokenizer = create_student_model(args.config, teacher_name)
    student = student.to(device).to(torch.bfloat16)

    # 데이터셋
    train_data = load_from_disk(f"{args.data_dir}/train")
    train_loader = DataLoader(
        train_data,
        batch_size=train_cfg["per_device_train_batch_size"],
        shuffle=True,
        collate_fn=lambda b: collate_fn(b, tokenizer, train_cfg["max_seq_length"]),
    )

    # 옵티마이저
    optimizer = torch.optim.AdamW(student.parameters(), lr=train_cfg["learning_rate"])
    total_steps = len(train_loader) * train_cfg["num_epochs"] // train_cfg["gradient_accumulation_steps"]
    warmup_steps = int(total_steps * train_cfg["warmup_ratio"])
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup_steps, total_steps)

    # 학습 루프
    student.train()
    global_step = 0

    for epoch in range(train_cfg["num_epochs"]):
        epoch_loss = 0
        progress = tqdm(train_loader, desc=f"Epoch {epoch + 1}")

        for step, batch in enumerate(progress):
            batch = {k: v.to(device) for k, v in batch.items()}

            # Teacher forward (no grad)
            with torch.no_grad():
                teacher_outputs = teacher(
                    input_ids=batch["input_ids"],
                    attention_mask=batch["attention_mask"],
                )

            # Student forward
            student_outputs = student(
                input_ids=batch["input_ids"],
                attention_mask=batch["attention_mask"],
            )

            loss = distillation_loss(
                student_logits=student_outputs.logits,
                teacher_logits=teacher_outputs.logits,
                labels=batch["labels"],
                temperature=train_cfg["temperature"],
                alpha_ce=train_cfg["alpha_ce"],
                alpha_kd=train_cfg["alpha_kd"],
            )

            loss = loss / train_cfg["gradient_accumulation_steps"]
            loss.backward()

            if (step + 1) % train_cfg["gradient_accumulation_steps"] == 0:
                torch.nn.utils.clip_grad_norm_(student.parameters(), 1.0)
                optimizer.step()
                scheduler.step()
                optimizer.zero_grad()
                global_step += 1

            epoch_loss += loss.item()
            progress.set_postfix(loss=f"{loss.item():.4f}")

        avg_loss = epoch_loss / len(train_loader)
        print(f"Epoch {epoch + 1} avg loss: {avg_loss:.4f}")

    # 저장
    student.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Student model saved to {args.output_dir}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Distillation 후 QLoRA 파인튜닝**

Student 모델에도 한국어 QLoRA 적용:

Run:
```bash
cd model && python finetune/train_qlora.py \
  --config config/qlora_config.yaml \
  --data_dir data/processed \
  --base_model output/distillation/lite_model
```
Expected: Distilled + 파인튜닝된 Lite 모델 저장

- [ ] **Step 5: 커밋**

```bash
git add model/distillation/
git commit -m "feat(model): add knowledge distillation for Lite tier"
```

---

### Task 6: 양자화 + MediaPipe 변환

**Files:**
- Create: `model/quantize/quantize_model.py`
- Create: `model/convert/convert_mediapipe.py`

- [ ] **Step 1: 양자화 스크립트 작성**

```python
# model/quantize/quantize_model.py
"""4티어 모델 양자화: INT4, INT8, FP16."""

import argparse
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from pathlib import Path


TIER_CONFIG = {
    "lite": {
        "source": "output/distillation/lite_qlora/merged_model",
        "dtype": "int4",
        "description": "Lite (~0.5B, INT4)",
    },
    "standard": {
        "source": "output/qlora_standard/merged_model",
        "dtype": "int8",
        "description": "Standard (~1.2B, INT8)",
    },
    "full": {
        "source": "output/qlora/merged_model",
        "dtype": "int8",
        "description": "Full (~2B, INT8)",
    },
    "max": {
        "source": "output/qlora/merged_model",
        "dtype": "fp16",
        "description": "Max (~2B, FP16)",
    },
}


def quantize_int8(model):
    """동적 INT8 양자화."""
    return torch.quantization.quantize_dynamic(
        model, {torch.nn.Linear}, dtype=torch.qint8
    )


def quantize_int4(model_path: str, output_path: str):
    """GPTQ 기반 INT4 양자화."""
    from auto_gptq import AutoGPTQForCausalLM, BaseQuantizeConfig

    quantize_config = BaseQuantizeConfig(
        bits=4,
        group_size=128,
        desc_act=False,
    )

    model = AutoGPTQForCausalLM.from_pretrained(
        model_path,
        quantize_config=quantize_config,
    )
    tokenizer = AutoTokenizer.from_pretrained(model_path)

    # 캘리브레이션 데이터 (간단한 한국어 문장들)
    examples = [
        tokenizer(text, return_tensors="pt")
        for text in [
            "안녕하세요, 오늘 날씨가 어떤가요?",
            "한국의 수도는 서울입니다.",
            "인공지능 기술이 빠르게 발전하고 있습니다.",
            "맛있는 한국 음식을 추천해주세요.",
        ]
    ]

    model.quantize(examples)
    model.save_quantized(output_path)
    tokenizer.save_pretrained(output_path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", type=str, required=True, choices=TIER_CONFIG.keys())
    parser.add_argument("--output_dir", type=str, default="output/quantized")
    args = parser.parse_args()

    tier = TIER_CONFIG[args.tier]
    output_path = f"{args.output_dir}/{args.tier}"
    Path(output_path).mkdir(parents=True, exist_ok=True)

    print(f"Quantizing {tier['description']}...")
    print(f"Source: {tier['source']}")

    if tier["dtype"] == "int4":
        quantize_int4(tier["source"], output_path)

    elif tier["dtype"] == "int8":
        model = AutoModelForCausalLM.from_pretrained(tier["source"], torch_dtype=torch.float32)
        tokenizer = AutoTokenizer.from_pretrained(tier["source"])
        model = quantize_int8(model)
        model.save_pretrained(output_path)
        tokenizer.save_pretrained(output_path)

    elif tier["dtype"] == "fp16":
        model = AutoModelForCausalLM.from_pretrained(tier["source"], torch_dtype=torch.float16)
        tokenizer = AutoTokenizer.from_pretrained(tier["source"])
        model.save_pretrained(output_path)
        tokenizer.save_pretrained(output_path)

    print(f"Quantized model saved to {output_path}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: MediaPipe 변환 스크립트 작성**

```python
# model/convert/convert_mediapipe.py
"""양자화된 모델을 MediaPipe .task 포맷으로 변환."""

import argparse
from pathlib import Path
from mediapipe.tasks.python.genai import converter


TIER_CONFIG = {
    "lite": {"source": "output/quantized/lite", "description": "Lite (INT4)"},
    "standard": {"source": "output/quantized/standard", "description": "Standard (INT8)"},
    "full": {"source": "output/quantized/full", "description": "Full (INT8)"},
    "max": {"source": "output/quantized/max", "description": "Max (FP16)"},
}


def convert_to_mediapipe(model_path: str, output_path: str, tier_name: str):
    """HuggingFace 모델을 MediaPipe .task 파일로 변환."""
    config = converter.ConversionConfig(
        input_ckpt=model_path,
        ckpt_format="huggingface",
        model_type="GEMMA",
        backend="gpu",
        output_dir=output_path,
        output_tflite_file=f"gemma4_ko_{tier_name}.task",
    )
    converter.convert_checkpoint(config)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", type=str, required=True, choices=TIER_CONFIG.keys())
    parser.add_argument("--output_dir", type=str, default="output/mediapipe")
    parser.add_argument("--all", action="store_true", help="전체 티어 변환")
    args = parser.parse_args()

    tiers = TIER_CONFIG.keys() if args.all else [args.tier]

    for tier_name in tiers:
        tier = TIER_CONFIG[tier_name]
        output_path = f"{args.output_dir}/{tier_name}"
        Path(output_path).mkdir(parents=True, exist_ok=True)

        print(f"Converting {tier['description']}...")
        convert_to_mediapipe(tier["source"], output_path, tier_name)
        print(f"Saved to {output_path}/gemma4_ko_{tier_name}.task")

    print("Done!")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 전체 파이프라인 실행 (4티어)**

```bash
# 양자화
cd model
python quantize/quantize_model.py --tier lite
python quantize/quantize_model.py --tier standard
python quantize/quantize_model.py --tier full
python quantize/quantize_model.py --tier max

# MediaPipe 변환
python convert/convert_mediapipe.py --tier lite --all
```
Expected: `output/mediapipe/` 아래에 4개의 `.task` 파일 생성

- [ ] **Step 4: 커밋**

```bash
git add model/quantize/ model/convert/
git commit -m "feat(model): add quantization and MediaPipe conversion scripts"
```

---

### Task 7: 모델 벤치마크

**Files:**
- Create: `model/validate/benchmark.py`

- [ ] **Step 1: 벤치마크 스크립트 작성**

```python
# model/validate/benchmark.py
"""4티어 모델 품질/속도 벤치마크."""

import argparse
import time
import json
from pathlib import Path
from mediapipe.tasks.python.genai import llm_inference


TIER_MODELS = {
    "lite": "output/mediapipe/lite/gemma4_ko_lite.task",
    "standard": "output/mediapipe/standard/gemma4_ko_standard.task",
    "full": "output/mediapipe/full/gemma4_ko_full.task",
    "max": "output/mediapipe/max/gemma4_ko_max.task",
}

TEST_PROMPTS = [
    "한국의 수도는 어디인가요?",
    "인공지능이란 무엇인가요? 간단히 설명해주세요.",
    "오늘 저녁 메뉴 추천해주세요.",
    "Python과 Java의 차이점을 알려주세요.",
    "서울에서 부산까지 가는 방법을 알려주세요.",
]


def benchmark_tier(tier_name: str, model_path: str) -> dict:
    """단일 티어 벤치마크: 응답 품질 + 속도."""
    print(f"\n{'=' * 50}")
    print(f"Benchmarking: {tier_name}")
    print(f"{'=' * 50}")

    model = llm_inference.LlmInference(
        llm_inference.LlmInference.Options(
            model_path=model_path,
            max_tokens=256,
        )
    )

    results = []
    total_tokens = 0
    total_time = 0

    for prompt in TEST_PROMPTS:
        formatted = f"<start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n"

        start = time.perf_counter()
        response = model.generate_response(formatted)
        elapsed = time.perf_counter() - start

        token_count = len(response.split())
        tokens_per_sec = token_count / elapsed if elapsed > 0 else 0

        total_tokens += token_count
        total_time += elapsed

        results.append({
            "prompt": prompt,
            "response": response[:200],
            "time_sec": round(elapsed, 2),
            "tokens": token_count,
            "tokens_per_sec": round(tokens_per_sec, 1),
        })

        print(f"\nQ: {prompt}")
        print(f"A: {response[:100]}...")
        print(f"   {elapsed:.2f}s, {tokens_per_sec:.1f} tok/s")

    avg_tps = total_tokens / total_time if total_time > 0 else 0

    return {
        "tier": tier_name,
        "avg_tokens_per_sec": round(avg_tps, 1),
        "total_time_sec": round(total_time, 2),
        "results": results,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", type=str, choices=TIER_MODELS.keys(),
                        help="특정 티어만 벤치마크 (미지정 시 전체)")
    parser.add_argument("--output", type=str, default="output/benchmark_results.json")
    args = parser.parse_args()

    tiers = [args.tier] if args.tier else list(TIER_MODELS.keys())
    all_results = []

    for tier_name in tiers:
        model_path = TIER_MODELS[tier_name]
        if not Path(model_path).exists():
            print(f"SKIP: {tier_name} — model not found at {model_path}")
            continue
        result = benchmark_tier(tier_name, model_path)
        all_results.append(result)

    # 결과 요약
    print(f"\n{'=' * 50}")
    print("SUMMARY")
    print(f"{'=' * 50}")
    for r in all_results:
        print(f"  {r['tier']:10s} — {r['avg_tokens_per_sec']:6.1f} tok/s")

    # JSON 저장
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2)
    print(f"\nResults saved to {args.output}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 벤치마크 실행**

Run:
```bash
cd model && python validate/benchmark.py
```
Expected: 각 티어별 tok/s, 응답 품질 출력 + JSON 저장

- [ ] **Step 3: 커밋**

```bash
git add model/validate/benchmark.py
git commit -m "feat(model): add benchmark script for 4-tier model validation"
```

---

# Part B: Android 앱 (Kotlin)

---

### Task 8: Android 프로젝트 스캐폴딩

**Files:**
- Create: `app/build.gradle.kts` (루트)
- Create: `app/app/build.gradle.kts` (앱 모듈)
- Create: `app/gradle/libs.versions.toml`
- Create: `app/settings.gradle.kts`

- [ ] **Step 1: 버전 카탈로그 작성**

```toml
# app/gradle/libs.versions.toml
[versions]
agp = "8.7.0"
kotlin = "2.1.0"
compose-bom = "2025.03.00"
mediapipe = "0.10.22"
room = "2.7.0"
hilt = "2.53.1"
coroutines = "1.9.0"
lifecycle = "2.8.7"
navigation = "2.8.5"
okhttp = "4.12.0"
junit = "4.13.2"
mockk = "1.13.13"
turbine = "1.2.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# MediaPipe
mediapipe-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipe" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Lifecycle
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Network (모델 다운로드)
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }

# Test
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.1.0-1.0.29" }
```

- [ ] **Step 2: 루트 build.gradle.kts 작성**

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: settings.gradle.kts 작성**

```kotlin
// app/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Gemma4Mobile"
include(":app")
```

- [ ] **Step 4: 앱 모듈 build.gradle.kts 작성**

```kotlin
// app/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gemma4mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gemma4mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    // MediaPipe
    implementation(libs.mediapipe.genai)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Network
    implementation(libs.okhttp)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
}
```

- [ ] **Step 5: Gradle 빌드 확인**

Run: `cd app && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add app/
git commit -m "feat(app): scaffold Android project with Compose, MediaPipe, Room, Hilt"
```

---

### Task 9: ModelTier + DeviceProfiler

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/model/ModelTier.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/model/DeviceProfiler.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/model/ModelTierTest.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/model/DeviceProfilerTest.kt`

- [ ] **Step 1: ModelTier 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/model/ModelTierTest.kt
package com.gemma4mobile.model

import org.junit.Assert.*
import org.junit.Test

class ModelTierTest {

    @Test
    fun `RAM 3GB returns null - unsupported device`() {
        val tier = ModelTier.forDevice(ramMb = 3000)
        assertNull(tier)
    }

    @Test
    fun `RAM 4GB returns LITE`() {
        val tier = ModelTier.forDevice(ramMb = 4000)
        assertEquals(ModelTier.LITE, tier)
    }

    @Test
    fun `RAM 6GB returns STANDARD`() {
        val tier = ModelTier.forDevice(ramMb = 6000)
        assertEquals(ModelTier.STANDARD, tier)
    }

    @Test
    fun `RAM 8GB returns FULL`() {
        val tier = ModelTier.forDevice(ramMb = 8000)
        assertEquals(ModelTier.FULL, tier)
    }

    @Test
    fun `RAM 12GB returns MAX`() {
        val tier = ModelTier.forDevice(ramMb = 12000)
        assertEquals(ModelTier.MAX, tier)
    }

    @Test
    fun `RAM 16GB returns MAX`() {
        val tier = ModelTier.forDevice(ramMb = 16000)
        assertEquals(ModelTier.MAX, tier)
    }

    @Test
    fun `each tier has correct model filename`() {
        assertEquals("gemma4_ko_lite.task", ModelTier.LITE.modelFilename)
        assertEquals("gemma4_ko_standard.task", ModelTier.STANDARD.modelFilename)
        assertEquals("gemma4_ko_full.task", ModelTier.FULL.modelFilename)
        assertEquals("gemma4_ko_max.task", ModelTier.MAX.modelFilename)
    }

    @Test
    fun `each tier has correct download size`() {
        assertTrue(ModelTier.LITE.downloadSizeMb < ModelTier.STANDARD.downloadSizeMb)
        assertTrue(ModelTier.STANDARD.downloadSizeMb < ModelTier.FULL.downloadSizeMb)
        assertTrue(ModelTier.FULL.downloadSizeMb < ModelTier.MAX.downloadSizeMb)
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.ModelTierTest"`
Expected: FAIL — `ModelTier` 클래스 없음

- [ ] **Step 3: ModelTier 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/model/ModelTier.kt
package com.gemma4mobile.model

enum class ModelTier(
    val modelFilename: String,
    val downloadSizeMb: Long,
    val displayName: String,
    val minRamMb: Int,
) {
    LITE(
        modelFilename = "gemma4_ko_lite.task",
        downloadSizeMb = 300,
        displayName = "Lite",
        minRamMb = 4000,
    ),
    STANDARD(
        modelFilename = "gemma4_ko_standard.task",
        downloadSizeMb = 1200,
        displayName = "Standard",
        minRamMb = 6000,
    ),
    FULL(
        modelFilename = "gemma4_ko_full.task",
        downloadSizeMb = 2000,
        displayName = "Full",
        minRamMb = 8000,
    ),
    MAX(
        modelFilename = "gemma4_ko_max.task",
        downloadSizeMb = 4000,
        displayName = "Max",
        minRamMb = 12000,
    );

    companion object {
        fun forDevice(ramMb: Int): ModelTier? {
            return entries
                .sortedByDescending { it.minRamMb }
                .firstOrNull { ramMb >= it.minRamMb }
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.ModelTierTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 5: DeviceProfiler 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/model/DeviceProfilerTest.kt
package com.gemma4mobile.model

import org.junit.Assert.*
import org.junit.Test

class DeviceProfilerTest {

    @Test
    fun `recommendedTier returns tier based on RAM`() {
        val profiler = DeviceProfiler(totalRamMb = 8000)
        assertEquals(ModelTier.FULL, profiler.recommendedTier)
    }

    @Test
    fun `recommendedTier returns null for unsupported device`() {
        val profiler = DeviceProfiler(totalRamMb = 2000)
        assertNull(profiler.recommendedTier)
    }

    @Test
    fun `availableTiers returns all tiers the device can run`() {
        val profiler = DeviceProfiler(totalRamMb = 8000)
        val available = profiler.availableTiers
        assertEquals(listOf(ModelTier.LITE, ModelTier.STANDARD, ModelTier.FULL), available)
    }

    @Test
    fun `hasEnoughStorage checks remaining disk space`() {
        val profiler = DeviceProfiler(totalRamMb = 8000, availableStorageMb = 5000)
        assertTrue(profiler.hasEnoughStorage(ModelTier.FULL))

        val lowStorage = DeviceProfiler(totalRamMb = 8000, availableStorageMb = 100)
        assertFalse(lowStorage.hasEnoughStorage(ModelTier.FULL))
    }
}
```

- [ ] **Step 6: 테스트 실행 — 실패 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.DeviceProfilerTest"`
Expected: FAIL

- [ ] **Step 7: DeviceProfiler 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/model/DeviceProfiler.kt
package com.gemma4mobile.model

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

class DeviceProfiler(
    private val totalRamMb: Int,
    private val availableStorageMb: Long = Long.MAX_VALUE,
) {
    val recommendedTier: ModelTier?
        get() = ModelTier.forDevice(totalRamMb)

    val availableTiers: List<ModelTier>
        get() = ModelTier.entries.filter { totalRamMb >= it.minRamMb }

    fun hasEnoughStorage(tier: ModelTier): Boolean {
        return availableStorageMb >= tier.downloadSizeMb
    }

    companion object {
        fun fromContext(context: Context): DeviceProfiler {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()

            val stat = StatFs(Environment.getDataDirectory().path)
            val availableStorageMb = stat.availableBytes / (1024 * 1024)

            return DeviceProfiler(totalRamMb, availableStorageMb)
        }
    }
}
```

- [ ] **Step 8: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.DeviceProfilerTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 9: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/model/ app/app/src/test/java/com/gemma4mobile/model/
git commit -m "feat(app): add ModelTier enum and DeviceProfiler"
```

---

### Task 10: Room DB — 대화 히스토리

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/db/ChatEntity.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/db/ChatDao.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/db/AppDatabase.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/db/ChatDaoTest.kt`

- [ ] **Step 1: ChatEntity 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/db/ChatEntity.kt
package com.gemma4mobile.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,       // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: ChatDao 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/db/ChatDao.kt
package com.gemma4mobile.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSession)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getSessions(): Flow<List<ChatSession>>

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Delete
    suspend fun deleteSession(session: ChatSession)
}
```

- [ ] **Step 3: AppDatabase 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/db/AppDatabase.kt
package com.gemma4mobile.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessage::class, ChatSession::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
```

- [ ] **Step 4: Room DAO 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/db/ChatDaoTest.kt
package com.gemma4mobile.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ChatDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert and retrieve messages`() = runTest {
        val session = ChatSession(id = "s1", title = "Test")
        dao.upsertSession(session)

        dao.insertMessage(ChatMessage(sessionId = "s1", role = "user", content = "안녕"))
        dao.insertMessage(ChatMessage(sessionId = "s1", role = "model", content = "안녕하세요!"))

        dao.getMessages("s1").test {
            val messages = awaitItem()
            assertEquals(2, messages.size)
            assertEquals("안녕", messages[0].content)
            assertEquals("model", messages[1].role)
            cancel()
        }
    }

    @Test
    fun `sessions ordered by updatedAt descending`() = runTest {
        dao.upsertSession(ChatSession(id = "s1", title = "Old", updatedAt = 1000))
        dao.upsertSession(ChatSession(id = "s2", title = "New", updatedAt = 2000))

        dao.getSessions().test {
            val sessions = awaitItem()
            assertEquals("s2", sessions[0].id)
            assertEquals("s1", sessions[1].id)
            cancel()
        }
    }

    @Test
    fun `delete session and its messages`() = runTest {
        val session = ChatSession(id = "s1", title = "Test")
        dao.upsertSession(session)
        dao.insertMessage(ChatMessage(sessionId = "s1", role = "user", content = "test"))

        dao.deleteMessages("s1")
        dao.deleteSession(session)

        dao.getMessages("s1").test {
            val messages = awaitItem()
            assertTrue(messages.isEmpty())
            cancel()
        }
    }
}
```

- [ ] **Step 5: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.db.ChatDaoTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 6: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/db/ app/app/src/test/java/com/gemma4mobile/db/
git commit -m "feat(app): add Room database for chat history"
```

---

### Task 11: GemmaInferenceEngine — MediaPipe LLM 래퍼

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/inference/GemmaInferenceEngine.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/inference/GemmaInferenceEngineTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/inference/GemmaInferenceEngineTest.kt
package com.gemma4mobile.inference

import org.junit.Assert.*
import org.junit.Test

class GemmaInferenceEngineTest {

    @Test
    fun `formatPrompt wraps user message in Gemma turn format`() {
        val engine = GemmaInferenceEngine()
        val formatted = engine.formatPrompt("안녕하세요")
        assertEquals(
            "<start_of_turn>user\n안녕하세요<end_of_turn>\n<start_of_turn>model\n",
            formatted,
        )
    }

    @Test
    fun `formatPrompt with history includes previous turns`() {
        val engine = GemmaInferenceEngine()
        val history = listOf(
            Turn("user", "안녕"),
            Turn("model", "안녕하세요!"),
        )
        val formatted = engine.formatPrompt("잘 지내?", history)
        val expected = """
            <start_of_turn>user
            안녕<end_of_turn>
            <start_of_turn>model
            안녕하세요!<end_of_turn>
            <start_of_turn>user
            잘 지내?<end_of_turn>
            <start_of_turn>model
        """.trimIndent() + "\n"
        assertEquals(expected, formatted)
    }

    @Test
    fun `state is UNLOADED initially`() {
        val engine = GemmaInferenceEngine()
        assertEquals(InferenceState.UNLOADED, engine.state)
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.inference.GemmaInferenceEngineTest"`
Expected: FAIL

- [ ] **Step 3: GemmaInferenceEngine 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/inference/GemmaInferenceEngine.kt
package com.gemma4mobile.inference

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

data class Turn(val role: String, val content: String)

enum class InferenceState {
    UNLOADED, LOADING, READY, GENERATING, ERROR
}

class GemmaInferenceEngine {

    private var llmInference: LlmInference? = null

    var state: InferenceState = InferenceState.UNLOADED
        private set

    fun formatPrompt(message: String, history: List<Turn> = emptyList()): String {
        val sb = StringBuilder()
        for (turn in history) {
            sb.append("<start_of_turn>${turn.role}\n${turn.content}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n$message<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    fun loadModel(modelPath: String, context: android.content.Context) {
        state = InferenceState.LOADING
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            state = InferenceState.READY
        } catch (e: Exception) {
            state = InferenceState.ERROR
            throw e
        }
    }

    fun generateStream(prompt: String): Flow<String> = callbackFlow {
        state = InferenceState.GENERATING
        val formatted = prompt // 이미 formatPrompt으로 포맷된 경우

        llmInference?.generateResponseAsync(formatted) { partialResult, done ->
            if (partialResult != null) {
                trySend(partialResult)
            }
            if (done) {
                state = InferenceState.READY
                close()
            }
        } ?: run {
            state = InferenceState.ERROR
            close(IllegalStateException("Model not loaded"))
        }

        awaitClose {
            state = InferenceState.READY
        }
    }

    fun unload() {
        llmInference?.close()
        llmInference = null
        state = InferenceState.UNLOADED
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.inference.GemmaInferenceEngineTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/inference/ app/app/src/test/java/com/gemma4mobile/inference/
git commit -m "feat(app): add GemmaInferenceEngine with MediaPipe LLM wrapper"
```

---

### Task 12: ModelDownloader — 모델 다운로드 매니저

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/model/ModelDownloader.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/model/ModelDownloaderTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/model/ModelDownloaderTest.kt
package com.gemma4mobile.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ModelDownloaderTest {

    @Test
    fun `modelExists returns false when file does not exist`() {
        val downloader = ModelDownloader(baseDir = "/tmp/nonexistent")
        assertFalse(downloader.modelExists(ModelTier.LITE))
    }

    @Test
    fun `getModelPath returns correct path`() {
        val downloader = ModelDownloader(baseDir = "/data/models")
        assertEquals(
            "/data/models/gemma4_ko_lite.task",
            downloader.getModelPath(ModelTier.LITE),
        )
    }

    @Test
    fun `downloadUrl returns correct URL for tier`() {
        val downloader = ModelDownloader(
            baseDir = "/data/models",
            baseUrl = "https://storage.example.com/models",
        )
        assertEquals(
            "https://storage.example.com/models/gemma4_ko_full.task",
            downloader.downloadUrl(ModelTier.FULL),
        )
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.ModelDownloaderTest"`
Expected: FAIL

- [ ] **Step 3: ModelDownloader 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/model/ModelDownloader.kt
package com.gemma4mobile.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class DownloadState {
    data class Progress(val percent: Int) : DownloadState()
    data class Complete(val path: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(
    private val baseDir: String,
    private val baseUrl: String = "https://storage.googleapis.com/gemma4mobile/models",
) {
    private val client = OkHttpClient()

    fun modelExists(tier: ModelTier): Boolean {
        return File(getModelPath(tier)).exists()
    }

    fun getModelPath(tier: ModelTier): String {
        return "$baseDir/${tier.modelFilename}"
    }

    fun downloadUrl(tier: ModelTier): String {
        return "$baseUrl/${tier.modelFilename}"
    }

    fun download(tier: ModelTier): Flow<DownloadState> = flow {
        val url = downloadUrl(tier)
        val destFile = File(getModelPath(tier))
        destFile.parentFile?.mkdirs()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            emit(DownloadState.Error("Download failed: ${response.code}"))
            return@flow
        }

        val body = response.body ?: run {
            emit(DownloadState.Error("Empty response body"))
            return@flow
        }

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val percent = (downloadedBytes * 100 / totalBytes).toInt()
                        emit(DownloadState.Progress(percent))
                    }
                }
            }
        }

        emit(DownloadState.Complete(destFile.absolutePath))
    }.flowOn(Dispatchers.IO)
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.model.ModelDownloaderTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/model/ModelDownloader.kt app/app/src/test/java/com/gemma4mobile/model/
git commit -m "feat(app): add ModelDownloader with progress tracking"
```

---

### Task 13: ModelManager — 모델 라이프사이클 관리

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/model/ModelManager.kt`

- [ ] **Step 1: ModelManager 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/model/ModelManager.kt
package com.gemma4mobile.model

import android.content.Context
import com.gemma4mobile.inference.GemmaInferenceEngine
import com.gemma4mobile.inference.InferenceState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val profiler = DeviceProfiler.fromContext(context)
    private val downloader = ModelDownloader(
        baseDir = context.filesDir.resolve("models").absolutePath,
    )
    val engine = GemmaInferenceEngine()

    private val _currentTier = MutableStateFlow<ModelTier?>(null)
    val currentTier: StateFlow<ModelTier?> = _currentTier

    val recommendedTier: ModelTier?
        get() = profiler.recommendedTier

    val availableTiers: List<ModelTier>
        get() = profiler.availableTiers

    fun isModelDownloaded(tier: ModelTier): Boolean {
        return downloader.modelExists(tier)
    }

    fun downloadModel(tier: ModelTier): Flow<DownloadState> {
        return downloader.download(tier)
    }

    fun loadModel(tier: ModelTier) {
        engine.unload()
        val path = downloader.getModelPath(tier)
        engine.loadModel(path, context)
        _currentTier.value = tier
    }

    fun unloadModel() {
        engine.unload()
        _currentTier.value = null
    }

    val inferenceState: InferenceState
        get() = engine.state
}
```

- [ ] **Step 2: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/model/ModelManager.kt
git commit -m "feat(app): add ModelManager for model lifecycle management"
```

---

### Task 14: ChatRepository + ChatViewModel

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/chat/ChatRepository.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt`
- Test: `app/app/src/test/java/com/gemma4mobile/chat/ChatViewModelTest.kt`

- [ ] **Step 1: ChatRepository 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/chat/ChatRepository.kt
package com.gemma4mobile.chat

import com.gemma4mobile.db.ChatDao
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
) {
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessages(sessionId)
    }

    fun getSessions(): Flow<List<ChatSession>> {
        return chatDao.getSessions()
    }

    suspend fun createSession(title: String = "새 대화"): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun addMessage(sessionId: String, role: String, content: String) {
        chatDao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
            )
        )
        chatDao.upsertSession(
            ChatSession(
                id = sessionId,
                title = if (role == "user") content.take(30) else "",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteSession(session: ChatSession) {
        chatDao.deleteMessages(session.id)
        chatDao.deleteSession(session)
    }
}
```

- [ ] **Step 2: ChatViewModel 구현**

```kotlin
// app/app/src/main/java/com/gemma4mobile/chat/ChatViewModel.kt
package com.gemma4mobile.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.inference.InferenceState
import com.gemma4mobile.inference.Turn
import com.gemma4mobile.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val modelTier: String = "",
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        viewModelScope.launch {
            val session = repository.createSession()
            currentSessionId = session.id
            _uiState.update {
                it.copy(modelTier = modelManager.currentTier.value?.displayName ?: "")
            }

            repository.getMessages(session.id).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        val sessionId = currentSessionId ?: return
        if (modelManager.inferenceState != InferenceState.READY) return

        viewModelScope.launch {
            // 사용자 메시지 저장
            repository.addMessage(sessionId, "user", text)

            // 대화 히스토리를 Turn으로 변환
            val history = _uiState.value.messages.map { Turn(it.role, it.content) }

            // 프롬프트 포맷팅
            val prompt = modelManager.engine.formatPrompt(text, history)

            // 스트리밍 생성
            _uiState.update { it.copy(isGenerating = true, streamingText = "") }

            val fullResponse = StringBuilder()
            modelManager.engine.generateStream(prompt).collect { token ->
                fullResponse.append(token)
                _uiState.update { it.copy(streamingText = fullResponse.toString()) }
            }

            // 응답 저장
            repository.addMessage(sessionId, "model", fullResponse.toString())
            _uiState.update { it.copy(isGenerating = false, streamingText = "") }
        }
    }
}
```

- [ ] **Step 3: ViewModel 테스트 작성**

```kotlin
// app/app/src/test/java/com/gemma4mobile/chat/ChatViewModelTest.kt
package com.gemma4mobile.chat

import com.gemma4mobile.db.ChatDao
import com.gemma4mobile.db.ChatMessage
import com.gemma4mobile.db.ChatSession
import com.gemma4mobile.model.ModelManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var chatDao: ChatDao
    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatDao = mockk(relaxed = true)
        every { chatDao.getMessages(any()) } returns flowOf(emptyList())
        every { chatDao.getSessions() } returns flowOf(emptyList())
        repository = ChatRepository(chatDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty messages`() = runTest {
        // ModelManager는 Android Context 필요 — 단위 테스트에서는 Repository 레벨만 검증
        val messages = listOf(
            ChatMessage(id = 1, sessionId = "s1", role = "user", content = "안녕"),
            ChatMessage(id = 2, sessionId = "s1", role = "model", content = "안녕하세요!"),
        )
        every { chatDao.getMessages("s1") } returns flowOf(messages)

        repository.getMessages("s1").collect { result ->
            assertEquals(2, result.size)
            assertEquals("안녕", result[0].content)
        }
    }

    @Test
    fun `addMessage inserts message and updates session`() = runTest {
        coEvery { chatDao.insertMessage(any()) } just Runs
        coEvery { chatDao.upsertSession(any()) } just Runs

        repository.addMessage("s1", "user", "테스트 메시지")

        coVerify { chatDao.insertMessage(match { it.content == "테스트 메시지" }) }
        coVerify { chatDao.upsertSession(match { it.id == "s1" }) }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

Run: `cd app && ./gradlew test --tests "com.gemma4mobile.chat.ChatViewModelTest"`
Expected: ALL TESTS PASSED

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/chat/ app/app/src/test/java/com/gemma4mobile/chat/
git commit -m "feat(app): add ChatRepository and ChatViewModel"
```

---

### Task 15: Chat UI — Jetpack Compose

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/theme/Theme.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/MessageBubble.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/ModelStatusBar.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 앱 테마 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/ui/theme/Theme.kt
package com.gemma4mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF81C995),
    tertiary = Color(0xFFF28B82),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF188038),
    tertiary = Color(0xFFC5221F),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF202124),
    onSurface = Color(0xFF202124),
)

@Composable
fun Gemma4MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
```

- [ ] **Step 2: MessageBubble 컴포넌트 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/ui/chat/MessageBubble.kt
package com.gemma4mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = content,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
```

- [ ] **Step 3: ModelStatusBar 컴포넌트 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/ui/chat/ModelStatusBar.kt
package com.gemma4mobile.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelStatusBar(
    tierName: String,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Gemma 4 · $tierName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "생성 중...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 4: ChatScreen 메인 화면 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/ui/chat/ChatScreen.kt
package com.gemma4mobile.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gemma4mobile.chat.ChatUiState
import com.gemma4mobile.chat.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatScreenContent(
        uiState = uiState,
        onSendMessage = viewModel::sendMessage,
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 새 메시지 시 자동 스크롤
    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                uiState.messages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 모델 상태
        ModelStatusBar(
            tierName = uiState.modelTier,
            isGenerating = uiState.isGenerating,
        )

        // 메시지 목록
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(uiState.messages) { message ->
                MessageBubble(
                    content = message.content,
                    isUser = message.role == "user",
                )
            }

            // 스트리밍 중인 응답
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    MessageBubble(
                        content = uiState.streamingText,
                        isUser = false,
                    )
                }
            }
        }

        // 에러 표시
        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // 입력창
        Surface(
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    maxLines = 4,
                    enabled = !uiState.isGenerating,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !uiState.isGenerating,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/
git commit -m "feat(app): add Chat UI with Compose (ChatScreen, MessageBubble, ModelStatusBar)"
```

---

### Task 16: Application + MainActivity + Hilt 모듈

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/GemmaApp.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/di/AppModule.kt`
- Create: `app/app/src/main/java/com/gemma4mobile/MainActivity.kt`

- [ ] **Step 1: Hilt Application 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/GemmaApp.kt
package com.gemma4mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GemmaApp : Application()
```

- [ ] **Step 2: Hilt DI 모듈 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/di/AppModule.kt
package com.gemma4mobile.di

import android.content.Context
import androidx.room.Room
import com.gemma4mobile.db.AppDatabase
import com.gemma4mobile.db.ChatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gemma4mobile.db",
        ).build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao {
        return db.chatDao()
    }
}
```

- [ ] **Step 3: MainActivity 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/MainActivity.kt
package com.gemma4mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gemma4mobile.ui.chat.ChatScreen
import com.gemma4mobile.ui.theme.Gemma4MobileTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4MobileTheme {
                ChatScreen()
            }
        }
    }
}
```

- [ ] **Step 4: AndroidManifest.xml 업데이트**

`app/app/src/main/AndroidManifest.xml`에 인터넷 권한과 Application 클래스 등록:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".GemmaApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Gemma4"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: 빌드 확인**

Run: `cd app && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/GemmaApp.kt \
       app/app/src/main/java/com/gemma4mobile/di/ \
       app/app/src/main/java/com/gemma4mobile/MainActivity.kt \
       app/app/src/main/AndroidManifest.xml
git commit -m "feat(app): add Hilt DI, Application, and MainActivity"
```

---

### Task 17: 모델 다운로드 + 온보딩 플로우

**Files:**
- Create: `app/app/src/main/java/com/gemma4mobile/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/app/src/main/java/com/gemma4mobile/MainActivity.kt`

- [ ] **Step 1: OnboardingScreen 작성**

```kotlin
// app/app/src/main/java/com/gemma4mobile/ui/onboarding/OnboardingScreen.kt
package com.gemma4mobile.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gemma4mobile.model.DownloadState
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.model.ModelTier
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    modelManager: ModelManager,
    onModelReady: () -> Unit,
) {
    val recommended = modelManager.recommendedTier
    val available = modelManager.availableTiers
    var selectedTier by remember { mutableStateOf(recommended) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Gemma 4 Mobile",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "온디바이스 AI 어시스턴트",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (recommended == null) {
            Text(
                text = "이 디바이스는 최소 사양(RAM 4GB)을 충족하지 않습니다.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            return
        }

        // 티어 선택
        Text("모델 선택", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        available.forEach { tier ->
            val isSelected = tier == selectedTier
            val alreadyDownloaded = modelManager.isModelDownloaded(tier)

            OutlinedCard(
                onClick = { if (!isDownloading) selectedTier = tier },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = { selectedTier = tier })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tier.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${tier.downloadSizeMb}MB" +
                                if (alreadyDownloaded) " (다운로드 완료)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tier == recommended) {
                        Spacer(Modifier.weight(1f))
                        AssistChip(
                            onClick = {},
                            label = { Text("추천") },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 다운로드 진행
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("다운로드 중... $downloadProgress%")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        // 시작 버튼
        Button(
            onClick = {
                val tier = selectedTier ?: return@Button
                if (modelManager.isModelDownloaded(tier)) {
                    modelManager.loadModel(tier)
                    onModelReady()
                } else {
                    isDownloading = true
                    error = null
                    scope.launch {
                        modelManager.downloadModel(tier).collect { state ->
                            when (state) {
                                is DownloadState.Progress -> downloadProgress = state.percent
                                is DownloadState.Complete -> {
                                    isDownloading = false
                                    modelManager.loadModel(tier)
                                    onModelReady()
                                }
                                is DownloadState.Error -> {
                                    isDownloading = false
                                    error = state.message
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedTier != null && !isDownloading,
        ) {
            Text(
                if (selectedTier != null && modelManager.isModelDownloaded(selectedTier!!)) {
                    "시작하기"
                } else {
                    "다운로드 후 시작"
                }
            )
        }
    }
}
```

- [ ] **Step 2: MainActivity 수정 — 온보딩 → 채팅 플로우**

```kotlin
// app/app/src/main/java/com/gemma4mobile/MainActivity.kt
package com.gemma4mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.gemma4mobile.model.ModelManager
import com.gemma4mobile.ui.chat.ChatScreen
import com.gemma4mobile.ui.onboarding.OnboardingScreen
import com.gemma4mobile.ui.theme.Gemma4MobileTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4MobileTheme {
                var modelReady by remember { mutableStateOf(false) }

                if (modelReady) {
                    ChatScreen()
                } else {
                    OnboardingScreen(
                        modelManager = modelManager,
                        onModelReady = { modelReady = true },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelManager.unloadModel()
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd app && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add app/app/src/main/java/com/gemma4mobile/ui/onboarding/ \
       app/app/src/main/java/com/gemma4mobile/MainActivity.kt
git commit -m "feat(app): add onboarding screen with model tier selection and download"
```

---

### Task 18: 통합 빌드 + 최종 확인

- [ ] **Step 1: 전체 테스트 실행**

Run: `cd app && ./gradlew test`
Expected: ALL TESTS PASSED

- [ ] **Step 2: APK 빌드**

Run: `cd app && ./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL, APK 생성

- [ ] **Step 3: git 초기화 + 최종 커밋**

```bash
cd /Users/dgsw36/Desktop/01_프로젝트-개발/AI/Gemma4_Mobile
git init
git add .
git commit -m "feat: Gemma4 Mobile Phase 1 — on-device Korean Q&A with Gemma 4 E2B"
```

---

## 실행 순서 요약

```
Part A (모델 파이프라인):
  Task 1: 환경 구축
  Task 2: 데이터셋 준비
  Task 3: Full/Max QLoRA 파인튜닝      ─┐
  Task 4: Standard Pruning + 재파인튜닝  ├─ 병렬 가능 (GPU 여러 개 시)
  Task 5: Lite Distillation + 파인튜닝  ─┘
  Task 6: 양자화 + MediaPipe 변환
  Task 7: 벤치마크

Part B (Android 앱):                    ← Part A와 병렬 진행 가능
  Task 8:  프로젝트 스캐폴딩
  Task 9:  ModelTier + DeviceProfiler
  Task 10: Room DB
  Task 11: GemmaInferenceEngine
  Task 12: ModelDownloader
  Task 13: ModelManager
  Task 14: ChatRepository + ViewModel
  Task 15: Chat UI
  Task 16: Application + DI
  Task 17: 온보딩 플로우

통합:
  Task 18: 통합 빌드 + 최종 확인
```
