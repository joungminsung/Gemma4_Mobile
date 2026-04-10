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

    for name in layer_importance:
        layer_importance[name] /= num_samples

    return layer_importance


def prune_layers(model, layer_importance: dict, target_sparsity: float, min_layers: int):
    """중요도 낮은 레이어 제거."""
    layer_scores = {}
    for name, score in layer_importance.items():
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

    sorted_layers = sorted(layer_scores.items(), key=lambda x: x[1])
    layers_to_remove = sorted(
        [idx for idx, _ in sorted_layers[:num_to_remove]],
        reverse=True,
    )

    print(f"Total layers: {total_layers}")
    print(f"Removing {len(layers_to_remove)} layers: {layers_to_remove}")
    print(f"Keeping {num_to_keep} layers")

    for idx in layers_to_remove:
        del model.model.layers[idx]

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

    cal_data = load_from_disk(f"{args.data_dir}/train")

    print("Computing layer importance...")
    importance = compute_layer_importance(
        model, cal_data, tokenizer,
        num_samples=cfg["pruning"]["calibration_samples"],
    )

    print("Pruning layers...")
    model = prune_layers(
        model,
        importance,
        target_sparsity=cfg["pruning"]["target_sparsity"],
        min_layers=cfg["pruning"]["min_layers"],
    )

    print(f"Pruned params: {sum(p.numel() for p in model.parameters()) / 1e9:.2f}B")

    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Pruned model saved to {args.output_dir}")


if __name__ == "__main__":
    main()
