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

    tokenizer = AutoTokenizer.from_pretrained(teacher_model_name)

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
