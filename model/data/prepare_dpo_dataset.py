# model/data/prepare_dpo_dataset.py
"""DPO용 선호도 데이터셋 준비 — chosen/rejected 쌍 생성."""

import argparse
import json
from datasets import load_dataset, Dataset
from pathlib import Path


def load_orca_dpo_ko() -> Dataset:
    """한국어 DPO 데이터셋 로드."""
    try:
        ds = load_dataset("kyujinpy/orca_dpo_pairs_ko", split="train")
        return ds.map(lambda x: {
            "prompt": x.get("question", x.get("prompt", "")),
            "chosen": x.get("chosen", ""),
            "rejected": x.get("rejected", ""),
        })
    except Exception as e:
        print(f"  WARN: orca_dpo_pairs_ko 로드 실패: {e}")
        return None


def load_ultrafeedback_ko() -> Dataset:
    """UltraFeedback 한국어 번역 데이터."""
    try:
        ds = load_dataset("heegyu/UltraFeedback-feedback-ko", split="train")
        results = []
        for x in ds:
            if x.get("chosen") and x.get("rejected"):
                results.append({
                    "prompt": x.get("instruction", x.get("prompt", "")),
                    "chosen": x["chosen"],
                    "rejected": x["rejected"],
                })
        return Dataset.from_list(results) if results else None
    except Exception as e:
        print(f"  WARN: UltraFeedback-ko 로드 실패: {e}")
        return None


def format_for_gemma_dpo(example: dict) -> dict:
    """Gemma 포맷으로 DPO 데이터 변환."""
    prompt = (
        f"<start_of_turn>user\n{example['prompt']}<end_of_turn>\n"
        f"<start_of_turn>model\n"
    )
    chosen = f"{example['chosen']}<end_of_turn>"
    rejected = f"{example['rejected']}<end_of_turn>"

    return {
        "prompt": prompt,
        "chosen": chosen,
        "rejected": rejected,
    }


def filter_dpo_quality(dataset: Dataset) -> Dataset:
    """DPO 데이터 품질 필터링."""
    def is_valid(x):
        if not x["prompt"].strip() or not x["chosen"].strip() or not x["rejected"].strip():
            return False
        if x["chosen"].strip() == x["rejected"].strip():
            return False
        if len(x["chosen"]) < 10 or len(x["rejected"]) < 10:
            return False
        return True

    before = len(dataset)
    dataset = dataset.filter(is_valid)
    print(f"  DPO quality filter: {before} → {len(dataset)}")
    return dataset


def main():
    parser = argparse.ArgumentParser(description="DPO 데이터셋 준비")
    parser.add_argument("--output_dir", type=str, default="./data/dpo_processed")
    parser.add_argument("--max_samples", type=int, default=None)
    parser.add_argument("--test_split", type=float, default=0.05)
    args = parser.parse_args()

    datasets = []

    print("Loading Orca DPO Ko...")
    ds = load_orca_dpo_ko()
    if ds:
        print(f"  Loaded: {len(ds)} pairs")
        datasets.append(ds)

    print("Loading UltraFeedback Ko...")
    ds = load_ultrafeedback_ko()
    if ds:
        print(f"  Loaded: {len(ds)} pairs")
        datasets.append(ds)

    if not datasets:
        print("ERROR: 사용 가능한 DPO 데이터셋이 없습니다.")
        return

    from datasets import concatenate_datasets
    combined = concatenate_datasets(datasets)
    print(f"\nTotal: {len(combined)} pairs")

    combined = filter_dpo_quality(combined)
    combined = combined.shuffle(seed=42)

    if args.max_samples:
        combined = combined.select(range(min(args.max_samples, len(combined))))

    formatted = combined.map(format_for_gemma_dpo, remove_columns=combined.column_names)

    split = formatted.train_test_split(test_size=args.test_split, seed=42)
    split["train"].save_to_disk(f"{args.output_dir}/train")
    split["test"].save_to_disk(f"{args.output_dir}/test")

    print(f"\nTrain: {len(split['train'])} pairs")
    print(f"Test:  {len(split['test'])} pairs")
    print(f"Saved to {args.output_dir}")


if __name__ == "__main__":
    main()
