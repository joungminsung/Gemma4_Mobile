# model/distillation/train_distillation.py
"""Advanced Knowledge Distillation: Logit KD + Attention Transfer + Hidden State Matching."""

import argparse
import yaml
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader
from transformers import AutoModelForCausalLM, AutoTokenizer, get_linear_schedule_with_warmup
from datasets import load_from_disk
from tqdm import tqdm
from pathlib import Path

from student_model import create_student_model


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


# ─── Projection Layers ───

class HiddenStateProjector(nn.Module):
    """Student hidden → Teacher hidden 차원 변환."""

    def __init__(self, student_dim: int, teacher_dim: int):
        super().__init__()
        self.proj = nn.Linear(student_dim, teacher_dim, bias=False)

    def forward(self, x):
        return self.proj(x)


class AttentionProjector(nn.Module):
    """Student attention heads → Teacher attention heads 차원 변환."""

    def __init__(self, student_heads: int, teacher_heads: int):
        super().__init__()
        if student_heads != teacher_heads:
            self.proj = nn.Linear(student_heads, teacher_heads, bias=False)
        else:
            self.proj = nn.Identity()

    def forward(self, x):
        # x: (batch, heads, seq, seq) → permute → project → permute back
        if isinstance(self.proj, nn.Identity):
            return x
        b, h, s1, s2 = x.shape
        x = x.permute(0, 2, 3, 1)  # (b, s1, s2, h)
        x = self.proj(x)
        x = x.permute(0, 3, 1, 2)  # (b, h', s1, s2)
        return x


# ─── Loss Functions ───

def logit_distillation_loss(student_logits, teacher_logits, labels, temperature, alpha_ce, alpha_kd):
    """Standard KD: KL divergence on soft logits + CE on hard labels."""
    soft_student = F.log_softmax(student_logits / temperature, dim=-1)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=-1)
    kd_loss = F.kl_div(soft_student, soft_teacher, reduction="batchmean") * (temperature ** 2)

    ce_loss = F.cross_entropy(
        student_logits.view(-1, student_logits.size(-1)),
        labels.view(-1),
        ignore_index=-100,
    )

    return alpha_kd * kd_loss + alpha_ce * ce_loss


def hidden_state_loss(student_hidden, teacher_hidden, projector, mask=None):
    """Hidden state matching: MSE between projected student and teacher hidden states."""
    projected = projector(student_hidden)

    if mask is not None:
        mask = mask.unsqueeze(-1).float()
        projected = projected * mask
        teacher_hidden = teacher_hidden * mask

    return F.mse_loss(projected, teacher_hidden)


def attention_transfer_loss(student_attn, teacher_attn, projector):
    """Attention transfer: MSE between attention weight distributions."""
    # Normalize attention maps
    student_norm = F.normalize(student_attn.pow(2).mean(dim=1), p=2, dim=-1)
    teacher_norm = F.normalize(teacher_attn.pow(2).mean(dim=1), p=2, dim=-1)

    # Project if different number of heads
    if student_attn.size(1) != teacher_attn.size(1):
        student_attn_proj = projector(student_attn)
    else:
        student_attn_proj = student_attn

    student_norm = F.normalize(student_attn_proj.pow(2).mean(dim=1), p=2, dim=-1)
    teacher_norm = F.normalize(teacher_attn.pow(2).mean(dim=1), p=2, dim=-1)

    return F.mse_loss(student_norm, teacher_norm)


# ─── Hook Utilities ───

class ActivationCollector:
    """Forward hook으로 hidden states와 attention weights 수집."""

    def __init__(self):
        self.hidden_states = {}
        self.attention_weights = {}
        self.hooks = []

    def register_hooks(self, model, layer_indices, prefix=""):
        for idx in layer_indices:
            layer = model.model.layers[idx]

            # Hidden state hook
            def make_hidden_hook(layer_idx):
                def hook(module, input, output):
                    if isinstance(output, tuple):
                        self.hidden_states[f"{prefix}_{layer_idx}"] = output[0].detach()
                    else:
                        self.hidden_states[f"{prefix}_{layer_idx}"] = output.detach()
                return hook

            self.hooks.append(
                layer.register_forward_hook(make_hidden_hook(idx))
            )

            # Attention hook
            def make_attn_hook(layer_idx):
                def hook(module, input, output):
                    if isinstance(output, tuple) and len(output) > 1 and output[1] is not None:
                        self.attention_weights[f"{prefix}_{layer_idx}"] = output[1].detach()
                return hook

            self.hooks.append(
                layer.self_attn.register_forward_hook(make_attn_hook(idx))
            )

    def clear(self):
        self.hidden_states.clear()
        self.attention_weights.clear()

    def remove_hooks(self):
        for hook in self.hooks:
            hook.remove()
        self.hooks.clear()


# ─── Collate ───

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


# ─── Main ───

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config/distillation_advanced_config.yaml")
    parser.add_argument("--data_dir", type=str, default="data/processed")
    parser.add_argument("--output_dir", type=str, default=None)
    args = parser.parse_args()

    cfg = load_config(args.config)
    loss_cfg = cfg["loss"]
    train_cfg = cfg["training"]
    output_dir = args.output_dir or train_cfg["output_dir"]
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    # Teacher 로드
    teacher_name = cfg["teacher"]["model"]
    print(f"Loading teacher: {teacher_name}")
    teacher = AutoModelForCausalLM.from_pretrained(
        teacher_name,
        device_map="auto",
        torch_dtype=torch.bfloat16,
        attn_implementation="eager",  # attention weights 추출을 위해
        output_attentions=True,
    )
    teacher.eval()
    for param in teacher.parameters():
        param.requires_grad = False

    teacher_hidden_size = teacher.config.hidden_size
    teacher_num_heads = teacher.config.num_attention_heads

    # Student 생성
    print("Creating student model...")
    student, tokenizer = create_student_model(args.config, teacher_name)
    student = student.to(device).to(torch.bfloat16)

    student_hidden_size = cfg["student"]["hidden_size"]
    student_num_heads = cfg["student"]["num_attention_heads"]

    # Layer 매핑
    layer_mapping = {int(k): int(v) for k, v in cfg["layer_mapping"].items()}
    student_layers = sorted(layer_mapping.keys())
    teacher_layers = [layer_mapping[s] for s in student_layers]

    print(f"Layer mapping: {layer_mapping}")

    # Projection 레이어
    hidden_projector = HiddenStateProjector(student_hidden_size, teacher_hidden_size).to(device).to(torch.bfloat16)
    attn_projector = AttentionProjector(student_num_heads, teacher_num_heads).to(device).to(torch.bfloat16)

    # Activation collectors
    teacher_collector = ActivationCollector()
    student_collector = ActivationCollector()
    teacher_collector.register_hooks(teacher, teacher_layers, prefix="teacher")
    student_collector.register_hooks(student, student_layers, prefix="student")

    # 데이터셋
    train_data = load_from_disk(f"{args.data_dir}/train")
    train_loader = DataLoader(
        train_data,
        batch_size=train_cfg["per_device_train_batch_size"],
        shuffle=True,
        collate_fn=lambda b: collate_fn(b, tokenizer, train_cfg["max_seq_length"]),
    )

    # 옵티마이저 (student + projectors)
    all_params = (
        list(student.parameters()) +
        list(hidden_projector.parameters()) +
        list(attn_projector.parameters())
    )
    optimizer = torch.optim.AdamW(all_params, lr=train_cfg["learning_rate"])
    total_steps = len(train_loader) * train_cfg["num_epochs"] // train_cfg["gradient_accumulation_steps"]
    warmup_steps = int(total_steps * train_cfg["warmup_ratio"])
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup_steps, total_steps)

    # 학습
    student.train()
    hidden_projector.train()
    attn_projector.train()
    global_step = 0

    Path(output_dir).mkdir(parents=True, exist_ok=True)

    for epoch in range(train_cfg["num_epochs"]):
        epoch_losses = {"total": 0, "logit": 0, "hidden": 0, "attn": 0}
        progress = tqdm(train_loader, desc=f"Epoch {epoch + 1}/{train_cfg['num_epochs']}")

        for step, batch in enumerate(progress):
            batch = {k: v.to(device) for k, v in batch.items()}

            # Teacher forward
            teacher_collector.clear()
            with torch.no_grad():
                teacher_outputs = teacher(
                    input_ids=batch["input_ids"],
                    attention_mask=batch["attention_mask"],
                    output_attentions=True,
                )

            # Student forward
            student_collector.clear()
            student_outputs = student(
                input_ids=batch["input_ids"],
                attention_mask=batch["attention_mask"],
                output_attentions=True,
            )

            # 1. Logit distillation loss
            logit_loss = logit_distillation_loss(
                student_outputs.logits,
                teacher_outputs.logits,
                batch["labels"],
                temperature=loss_cfg["temperature"],
                alpha_ce=loss_cfg["alpha_ce"],
                alpha_kd=loss_cfg["alpha_kd"],
            )

            # 2. Hidden state matching loss
            h_loss = torch.tensor(0.0, device=device)
            h_count = 0
            for s_idx, t_idx in layer_mapping.items():
                s_key = f"student_{s_idx}"
                t_key = f"teacher_{t_idx}"
                if s_key in student_collector.hidden_states and t_key in teacher_collector.hidden_states:
                    h_loss += hidden_state_loss(
                        student_collector.hidden_states[s_key],
                        teacher_collector.hidden_states[t_key],
                        hidden_projector,
                        mask=batch["attention_mask"],
                    )
                    h_count += 1
            if h_count > 0:
                h_loss = h_loss / h_count

            # 3. Attention transfer loss
            a_loss = torch.tensor(0.0, device=device)
            a_count = 0
            for s_idx, t_idx in layer_mapping.items():
                s_key = f"student_{s_idx}"
                t_key = f"teacher_{t_idx}"
                if s_key in student_collector.attention_weights and t_key in teacher_collector.attention_weights:
                    a_loss += attention_transfer_loss(
                        student_collector.attention_weights[s_key],
                        teacher_collector.attention_weights[t_key],
                        attn_projector,
                    )
                    a_count += 1
            if a_count > 0:
                a_loss = a_loss / a_count

            # Total loss
            total_loss = logit_loss + loss_cfg["alpha_hidden"] * h_loss + loss_cfg["alpha_attn"] * a_loss
            total_loss = total_loss / train_cfg["gradient_accumulation_steps"]
            total_loss.backward()

            if (step + 1) % train_cfg["gradient_accumulation_steps"] == 0:
                torch.nn.utils.clip_grad_norm_(all_params, 1.0)
                optimizer.step()
                scheduler.step()
                optimizer.zero_grad()
                global_step += 1

                # 주기적 저장
                if global_step % train_cfg.get("save_every_n_steps", 1000) == 0:
                    save_path = f"{output_dir}/checkpoint-{global_step}"
                    student.save_pretrained(save_path)
                    tokenizer.save_pretrained(save_path)
                    print(f"\n  Checkpoint saved: {save_path}")

            epoch_losses["total"] += total_loss.item()
            epoch_losses["logit"] += logit_loss.item()
            epoch_losses["hidden"] += h_loss.item()
            epoch_losses["attn"] += a_loss.item()

            progress.set_postfix(
                total=f"{total_loss.item():.4f}",
                logit=f"{logit_loss.item():.4f}",
                hidden=f"{h_loss.item():.4f}",
                attn=f"{a_loss.item():.4f}",
            )

        n = len(train_loader)
        print(f"Epoch {epoch + 1} — "
              f"total: {epoch_losses['total']/n:.4f}, "
              f"logit: {epoch_losses['logit']/n:.4f}, "
              f"hidden: {epoch_losses['hidden']/n:.4f}, "
              f"attn: {epoch_losses['attn']/n:.4f}")

    # Cleanup hooks
    teacher_collector.remove_hooks()
    student_collector.remove_hooks()

    # 최종 저장
    final_path = f"{output_dir}/lite_model"
    student.save_pretrained(final_path)
    tokenizer.save_pretrained(final_path)
    print(f"\nFinal student model saved to {final_path}")


if __name__ == "__main__":
    main()
