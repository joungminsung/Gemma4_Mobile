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
    soft_student = F.log_softmax(student_logits / temperature, dim=-1)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=-1)
    kd_loss = F.kl_div(soft_student, soft_teacher, reduction="batchmean") * (temperature ** 2)

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

    print("Creating student model...")
    student, tokenizer = create_student_model(args.config, teacher_name)
    student = student.to(device).to(torch.bfloat16)

    train_data = load_from_disk(f"{args.data_dir}/train")
    train_loader = DataLoader(
        train_data,
        batch_size=train_cfg["per_device_train_batch_size"],
        shuffle=True,
        collate_fn=lambda b: collate_fn(b, tokenizer, train_cfg["max_seq_length"]),
    )

    optimizer = torch.optim.AdamW(student.parameters(), lr=train_cfg["learning_rate"])
    total_steps = len(train_loader) * train_cfg["num_epochs"] // train_cfg["gradient_accumulation_steps"]
    warmup_steps = int(total_steps * train_cfg["warmup_ratio"])
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup_steps, total_steps)

    student.train()
    global_step = 0

    for epoch in range(train_cfg["num_epochs"]):
        epoch_loss = 0
        progress = tqdm(train_loader, desc=f"Epoch {epoch + 1}")

        for step, batch in enumerate(progress):
            batch = {k: v.to(device) for k, v in batch.items()}

            with torch.no_grad():
                teacher_outputs = teacher(
                    input_ids=batch["input_ids"],
                    attention_mask=batch["attention_mask"],
                )

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

    student.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Student model saved to {args.output_dir}")


if __name__ == "__main__":
    main()
