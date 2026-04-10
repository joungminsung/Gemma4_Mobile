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
