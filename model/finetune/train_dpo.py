# model/finetune/train_dpo.py
"""DPO (Direct Preference Optimization) 학습 — SFT 후 선호도 정렬."""

import argparse
import yaml
import torch
from datasets import load_from_disk
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from peft import LoraConfig
from trl import DPOTrainer, DPOConfig


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config/dpo_config.yaml")
    parser.add_argument("--data_dir", type=str, default="data/dpo_processed")
    parser.add_argument("--base_model", type=str, default=None,
                        help="SFT 완료된 모델 경로 (미지정 시 config 사용)")
    args = parser.parse_args()

    cfg = load_config(args.config)
    model_path = args.base_model or cfg["model"]["base_model"]

    # 양자화 설정
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=cfg["quantization"]["load_in_4bit"],
        bnb_4bit_compute_dtype=getattr(torch, cfg["quantization"]["bnb_4bit_compute_dtype"]),
        bnb_4bit_quant_type=cfg["quantization"]["bnb_4bit_quant_type"],
        bnb_4bit_use_double_quant=cfg["quantization"]["bnb_4bit_use_double_quant"],
    )

    # 모델 로드
    print(f"Loading SFT model: {model_path}")
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "left"  # DPO는 left padding 필요

    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        quantization_config=bnb_config,
        device_map="auto",
        torch_dtype=torch.bfloat16,
    )

    # Reference 모델 (DPO에서 KL divergence 계산용)
    # QLoRA DPO에서는 ref_model=None으로 설정하면 base model을 자동 사용
    ref_model = None

    # LoRA 설정 (DPO용 — SFT보다 작은 rank)
    lora_cfg = cfg["lora"]
    peft_config = LoraConfig(
        r=lora_cfg["r"],
        lora_alpha=lora_cfg["lora_alpha"],
        lora_dropout=lora_cfg["lora_dropout"],
        target_modules=lora_cfg["target_modules"],
        task_type="CAUSAL_LM",
        bias="none",
    )

    # 데이터셋 로드
    train_dataset = load_from_disk(f"{args.data_dir}/train")
    eval_dataset = load_from_disk(f"{args.data_dir}/test")
    print(f"Train: {len(train_dataset)} pairs, Eval: {len(eval_dataset)} pairs")

    # DPO 학습 설정
    train_cfg = cfg["training"]
    dpo_cfg = cfg["dpo"]

    training_args = DPOConfig(
        output_dir=train_cfg["output_dir"],
        num_train_epochs=train_cfg["num_epochs"],
        per_device_train_batch_size=train_cfg["per_device_train_batch_size"],
        gradient_accumulation_steps=train_cfg["gradient_accumulation_steps"],
        learning_rate=train_cfg["learning_rate"],
        warmup_ratio=train_cfg["warmup_ratio"],
        bf16=train_cfg["bf16"],
        logging_steps=train_cfg["logging_steps"],
        save_steps=train_cfg["save_steps"],
        eval_strategy="steps",
        eval_steps=train_cfg["save_steps"],
        save_total_limit=2,
        report_to="none",
        beta=dpo_cfg["beta"],
        loss_type=dpo_cfg["loss_type"],
        max_prompt_length=dpo_cfg["max_prompt_length"],
        max_completion_length=dpo_cfg["max_completion_length"],
        remove_unused_columns=False,
    )

    # DPO Trainer
    trainer = DPOTrainer(
        model=model,
        ref_model=ref_model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        processing_class=tokenizer,
        peft_config=peft_config,
    )

    print("Starting DPO training...")
    trainer.train()

    # 저장
    adapter_path = f"{train_cfg['output_dir']}/final_adapter"
    trainer.save_model(adapter_path)
    tokenizer.save_pretrained(adapter_path)
    print(f"DPO adapter saved to {adapter_path}")

    # 머지
    print("Merging DPO weights...")
    merged_model = trainer.model.merge_and_unload()
    merged_path = f"{train_cfg['output_dir']}/merged_model"
    merged_model.save_pretrained(merged_path)
    tokenizer.save_pretrained(merged_path)
    print(f"Merged model saved to {merged_path}")


if __name__ == "__main__":
    main()
