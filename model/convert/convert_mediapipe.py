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
