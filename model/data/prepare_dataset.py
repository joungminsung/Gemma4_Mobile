# model/data/prepare_dataset.py
"""한국어 Q&A 데이터셋 다운로드, 전처리, 품질 필터링."""

import argparse
import hashlib
from datasets import load_dataset, concatenate_datasets, Dataset


# ─── 데이터셋 로더 ───

def load_koalpaca() -> Dataset:
    """KoAlpaca v1.1a — 한국어 지시-응답."""
    ds = load_dataset("beomi/KoAlpaca-v1.1a", split="train")
    return ds.map(lambda x: {
        "instruction": x["instruction"],
        "input": x.get("input", ""),
        "output": x["output"],
        "source": "koalpaca",
    })


def load_kullm() -> Dataset:
    """KULLM v2 — 고려대 한국어 대화."""
    ds = load_dataset("nlpai-lab/kullm-v2", split="train")
    return ds.map(lambda x: {
        "instruction": x["instruction"],
        "input": x.get("input", ""),
        "output": x["output"],
        "source": "kullm",
    })


def load_ko_lima() -> Dataset:
    """ko-lima — 고품질 한국어 지시문 (소량 고품질)."""
    try:
        ds = load_dataset("changpt/ko-lima-vicuna", split="train")
        return ds.map(lambda x: {
            "instruction": x["instruction"],
            "input": "",
            "output": x["output"],
            "source": "ko_lima",
        })
    except Exception as e:
        print(f"  WARN: ko-lima 로드 실패, 건너뜀: {e}")
        return None


def load_open_orca_ko() -> Dataset:
    """Open-Orca-Ko — 추론 능력 강화용."""
    try:
        ds = load_dataset("kyujinpy/Open-Orca-KO", split="train")
        return ds.map(lambda x: {
            "instruction": x.get("question", x.get("instruction", "")),
            "input": "",
            "output": x.get("response", x.get("output", "")),
            "source": "open_orca_ko",
        })
    except Exception as e:
        print(f"  WARN: Open-Orca-Ko 로드 실패, 건너뜀: {e}")
        return None


def load_korquad() -> Dataset:
    """KorQuAD v1 — 한국어 독해 (질문-답변 변환)."""
    try:
        ds = load_dataset("squad_kor_v1", split="train")
        return ds.map(lambda x: {
            "instruction": x["question"],
            "input": x["context"][:500],  # 컨텍스트가 길면 잘라냄
            "output": x["answers"]["text"][0] if x["answers"]["text"] else "",
            "source": "korquad",
        })
    except Exception as e:
        print(f"  WARN: KorQuAD 로드 실패, 건너뜀: {e}")
        return None


def load_kor_sharegpt() -> Dataset:
    """Korean ShareGPT — 자연스러운 멀티턴 대화."""
    try:
        ds = load_dataset("nayohan/KOR-ShareGPT-Formatting", split="train")
        results = []
        for x in ds:
            convs = x.get("conversations", [])
            for i in range(0, len(convs) - 1, 2):
                if convs[i].get("from") == "human" and convs[i + 1].get("from") == "gpt":
                    results.append({
                        "instruction": convs[i]["value"],
                        "input": "",
                        "output": convs[i + 1]["value"],
                        "source": "kor_sharegpt",
                    })
        return Dataset.from_list(results)
    except Exception as e:
        print(f"  WARN: Kor-ShareGPT 로드 실패, 건너뜀: {e}")
        return None


# ─── 품질 필터링 ───

def filter_quality(dataset: Dataset, min_length: int = 10, max_length: int = 2048) -> Dataset:
    """품질 필터링: 너무 짧거나 긴 샘플 제거, 빈 응답 제거."""

    def is_valid(example):
        instruction = example["instruction"].strip()
        output = example["output"].strip()

        # 빈 필드 제거
        if not instruction or not output:
            return False

        # 너무 짧은 응답 제거
        if len(output) < min_length:
            return False

        # 너무 긴 텍스트 제거 (토크나이저 max_length 초과 방지)
        if len(instruction) + len(output) > max_length * 4:  # 한국어는 char당 ~1 토큰
            return False

        return True

    before = len(dataset)
    dataset = dataset.filter(is_valid)
    after = len(dataset)
    print(f"  Quality filter: {before} → {after} ({before - after} removed)")
    return dataset


def deduplicate(dataset: Dataset) -> Dataset:
    """instruction 기반 중복 제거."""
    seen = set()
    indices = []

    for i, example in enumerate(dataset):
        key = hashlib.md5(example["instruction"].encode()).hexdigest()
        if key not in seen:
            seen.add(key)
            indices.append(i)

    before = len(dataset)
    dataset = dataset.select(indices)
    print(f"  Deduplication: {before} → {len(dataset)} ({before - len(dataset)} duplicates)")
    return dataset


# ─── 포맷 변환 ───

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


# ─── 메인 ───

def main():
    parser = argparse.ArgumentParser(description="한국어 데이터셋 준비 (확장판)")
    parser.add_argument("--output_dir", type=str, default="./data/processed")
    parser.add_argument("--max_samples", type=int, default=None,
                        help="데이터셋 최대 샘플 수 (디버깅용)")
    parser.add_argument("--test_split", type=float, default=0.05)
    parser.add_argument("--min_length", type=int, default=10,
                        help="응답 최소 길이 (문자)")
    parser.add_argument("--max_length", type=int, default=2048,
                        help="텍스트 최대 길이 (토큰 기준 근사)")
    args = parser.parse_args()

    # 데이터셋 로더 목록
    loaders = [
        ("KoAlpaca", load_koalpaca),
        ("KULLM v2", load_kullm),
        ("ko-lima", load_ko_lima),
        ("Open-Orca-Ko", load_open_orca_ko),
        ("KorQuAD", load_korquad),
        ("Kor-ShareGPT", load_kor_sharegpt),
    ]

    datasets = []
    for name, loader in loaders:
        print(f"Loading {name}...")
        ds = loader()
        if ds is not None:
            print(f"  {name}: {len(ds)} samples")
            datasets.append(ds)

    # 합치기
    combined = concatenate_datasets(datasets)
    print(f"\nTotal combined: {len(combined)} samples")

    # 품질 필터링
    print("\nApplying quality filters...")
    combined = filter_quality(combined, min_length=args.min_length, max_length=args.max_length)

    # 중복 제거
    print("Removing duplicates...")
    combined = deduplicate(combined)

    # 셔플
    combined = combined.shuffle(seed=42)

    if args.max_samples:
        combined = combined.select(range(min(args.max_samples, len(combined))))

    print(f"\nFinal dataset: {len(combined)} samples")

    # 소스별 분포 출력
    source_counts = {}
    for example in combined:
        src = example.get("source", "unknown")
        source_counts[src] = source_counts.get(src, 0) + 1
    print("\nDataset distribution:")
    for src, count in sorted(source_counts.items(), key=lambda x: -x[1]):
        print(f"  {src:20s}: {count:>6d} ({count / len(combined) * 100:.1f}%)")

    # Gemma 포맷 변환
    formatted = combined.map(format_for_gemma, remove_columns=combined.column_names)

    # Train/Test 분리
    split = formatted.train_test_split(test_size=args.test_split, seed=42)
    split["train"].save_to_disk(f"{args.output_dir}/train")
    split["test"].save_to_disk(f"{args.output_dir}/test")

    print(f"\nTrain: {len(split['train'])} samples")
    print(f"Test:  {len(split['test'])} samples")
    print(f"Saved to {args.output_dir}")


if __name__ == "__main__":
    main()
