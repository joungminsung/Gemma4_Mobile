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
