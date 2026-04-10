# model/validate/evaluate_korean.py
"""한국어 모델 정량 평가 — KoBEST, Perplexity, 생성 품질."""

import argparse
import json
import math
import torch
from pathlib import Path
from transformers import AutoModelForCausalLM, AutoTokenizer
from datasets import load_dataset


# ─── Perplexity 측정 ───

def evaluate_perplexity(model, tokenizer, dataset, max_samples=200, max_length=512):
    """한국어 텍스트에 대한 Perplexity 측정."""
    model.eval()
    total_loss = 0
    total_tokens = 0

    for i, sample in enumerate(dataset.select(range(min(max_samples, len(dataset))))):
        text = sample["text"]
        inputs = tokenizer(
            text,
            return_tensors="pt",
            truncation=True,
            max_length=max_length,
        ).to(model.device)

        with torch.no_grad():
            outputs = model(**inputs, labels=inputs["input_ids"])
            total_loss += outputs.loss.item() * inputs["input_ids"].size(1)
            total_tokens += inputs["input_ids"].size(1)

    avg_loss = total_loss / total_tokens
    perplexity = math.exp(avg_loss)
    return perplexity


# ─── KoBEST 벤치마크 ───

def evaluate_kobest_boolq(model, tokenizer, max_samples=500):
    """KoBEST BoolQ — 한국어 예/아니오 질문 정확도."""
    try:
        ds = load_dataset("skt/ko-boolq", split="test")
    except Exception:
        try:
            ds = load_dataset("HAERAE-HUB/KoBEST_BoolQ", split="test")
        except Exception as e:
            print(f"  WARN: KoBEST BoolQ 로드 실패: {e}")
            return None

    correct = 0
    total = 0

    for sample in ds.select(range(min(max_samples, len(ds)))):
        question = sample.get("question", sample.get("paragraph", ""))
        label = sample.get("label", sample.get("answer", 0))

        prompt = (
            f"<start_of_turn>user\n"
            f"다음 질문에 '예' 또는 '아니오'로만 답하세요.\n\n"
            f"{question}\n"
            f"<end_of_turn>\n"
            f"<start_of_turn>model\n"
        )

        inputs = tokenizer(prompt, return_tensors="pt", truncation=True, max_length=512)
        inputs = {k: v.to(model.device) for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=10,
                do_sample=False,
            )

        response = tokenizer.decode(outputs[0][inputs["input_ids"].size(1):], skip_special_tokens=True)
        response = response.strip().lower()

        # 예/아니오 판정
        predicted = None
        if "예" in response or "네" in response or "맞" in response or "yes" in response:
            predicted = 1
        elif "아니" in response or "no" in response:
            predicted = 0

        if predicted is not None:
            if predicted == label:
                correct += 1
            total += 1

    accuracy = correct / total if total > 0 else 0
    return {"accuracy": round(accuracy, 4), "total": total, "correct": correct}


def evaluate_kobest_sentineg(model, tokenizer, max_samples=500):
    """KoBEST SentiNeg — 한국어 감성 분석 (부정 표현 이해)."""
    try:
        ds = load_dataset("skt/ko-sentineg", split="test")
    except Exception:
        try:
            ds = load_dataset("HAERAE-HUB/KoBEST_SentiNeg", split="test")
        except Exception as e:
            print(f"  WARN: KoBEST SentiNeg 로드 실패: {e}")
            return None

    correct = 0
    total = 0

    for sample in ds.select(range(min(max_samples, len(ds)))):
        sentence = sample.get("sentence", sample.get("data", ""))
        label = sample.get("label", 0)  # 0=부정, 1=긍정

        prompt = (
            f"<start_of_turn>user\n"
            f"다음 문장의 감정이 긍정이면 '긍정', 부정이면 '부정'이라고만 답하세요.\n\n"
            f"\"{sentence}\"\n"
            f"<end_of_turn>\n"
            f"<start_of_turn>model\n"
        )

        inputs = tokenizer(prompt, return_tensors="pt", truncation=True, max_length=512)
        inputs = {k: v.to(model.device) for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=10,
                do_sample=False,
            )

        response = tokenizer.decode(outputs[0][inputs["input_ids"].size(1):], skip_special_tokens=True)
        response = response.strip()

        predicted = None
        if "긍정" in response or "positive" in response.lower():
            predicted = 1
        elif "부정" in response or "negative" in response.lower():
            predicted = 0

        if predicted is not None:
            if predicted == label:
                correct += 1
            total += 1

    accuracy = correct / total if total > 0 else 0
    return {"accuracy": round(accuracy, 4), "total": total, "correct": correct}


# ─── 생성 품질 평가 ───

def evaluate_generation_quality(model, tokenizer, max_new_tokens=256):
    """한국어 생성 품질 — 다양한 카테고리의 프롬프트로 평가."""
    test_prompts = {
        "사실 질문": "대한민국의 수도와 인구를 알려주세요.",
        "설명 요청": "양자역학이란 무엇인지 중학생도 이해할 수 있게 설명해주세요.",
        "추론": "철수가 사과 3개를 가지고 있었는데 영희에게 1개를 주고, 가게에서 2개를 더 샀습니다. 철수가 가진 사과는 몇 개인가요?",
        "창작": "봄에 대한 짧은 시를 한 편 써주세요.",
        "코드": "Python으로 피보나치 수열을 구하는 함수를 작성해주세요.",
        "대화체": "요즘 스트레스를 많이 받는데, 어떻게 하면 좋을까요?",
        "한국 문화": "한국의 설날 전통에 대해 알려주세요.",
    }

    results = []

    for category, prompt in test_prompts.items():
        formatted = (
            f"<start_of_turn>user\n{prompt}<end_of_turn>\n"
            f"<start_of_turn>model\n"
        )

        inputs = tokenizer(formatted, return_tensors="pt", truncation=True, max_length=512)
        inputs = {k: v.to(model.device) for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                do_sample=True,
                temperature=0.7,
                top_p=0.9,
                repetition_penalty=1.1,
            )

        response = tokenizer.decode(outputs[0][inputs["input_ids"].size(1):], skip_special_tokens=True)

        # 기본 품질 메트릭
        response_length = len(response)
        is_korean = sum(1 for c in response if '\uac00' <= c <= '\ud7a3') / max(len(response), 1)
        has_repetition = any(
            response[i:i+20] == response[i+20:i+40]
            for i in range(0, max(len(response) - 40, 0), 10)
        )

        results.append({
            "category": category,
            "prompt": prompt,
            "response": response[:500],
            "length": response_length,
            "korean_ratio": round(is_korean, 2),
            "has_repetition": has_repetition,
        })

        print(f"\n[{category}]")
        print(f"Q: {prompt}")
        print(f"A: {response[:200]}...")
        print(f"   길이: {response_length}, 한국어 비율: {is_korean:.0%}, 반복: {'있음' if has_repetition else '없음'}")

    return results


# ─── 메인 ───

def main():
    parser = argparse.ArgumentParser(description="한국어 모델 정량 평가")
    parser.add_argument("--model_path", type=str, required=True,
                        help="평가할 모델 경로")
    parser.add_argument("--output", type=str, default="output/eval_results.json")
    parser.add_argument("--device", type=str, default="auto")
    parser.add_argument("--max_eval_samples", type=int, default=500)
    parser.add_argument("--skip_kobest", action="store_true", help="KoBEST 평가 건너뛰기")
    args = parser.parse_args()

    print(f"Loading model: {args.model_path}")
    tokenizer = AutoTokenizer.from_pretrained(args.model_path)
    model = AutoModelForCausalLM.from_pretrained(
        args.model_path,
        device_map=args.device,
        torch_dtype=torch.bfloat16,
    )

    results = {"model_path": args.model_path}

    # 1. Perplexity
    print("\n" + "=" * 50)
    print("1. Perplexity 측정")
    print("=" * 50)
    try:
        eval_data = load_dataset("heegyu/namuwiki-extracted", split="train", streaming=True)
        # 스트리밍 데이터셋에서 일부만 추출
        samples = []
        for i, sample in enumerate(eval_data):
            if i >= args.max_eval_samples:
                break
            samples.append({"text": sample["text"][:1000]})
        from datasets import Dataset as HFDataset
        eval_ds = HFDataset.from_list(samples)
        ppl = evaluate_perplexity(model, tokenizer, eval_ds)
        results["perplexity"] = round(ppl, 2)
        print(f"Perplexity: {ppl:.2f}")
    except Exception as e:
        print(f"Perplexity 측정 실패: {e}")
        results["perplexity"] = None

    # 2. KoBEST
    if not args.skip_kobest:
        print("\n" + "=" * 50)
        print("2. KoBEST 벤치마크")
        print("=" * 50)

        print("\n[BoolQ]")
        boolq = evaluate_kobest_boolq(model, tokenizer, args.max_eval_samples)
        results["kobest_boolq"] = boolq
        if boolq:
            print(f"  Accuracy: {boolq['accuracy']:.2%} ({boolq['correct']}/{boolq['total']})")

        print("\n[SentiNeg]")
        sentineg = evaluate_kobest_sentineg(model, tokenizer, args.max_eval_samples)
        results["kobest_sentineg"] = sentineg
        if sentineg:
            print(f"  Accuracy: {sentineg['accuracy']:.2%} ({sentineg['correct']}/{sentineg['total']})")

    # 3. 생성 품질
    print("\n" + "=" * 50)
    print("3. 생성 품질 평가")
    print("=" * 50)
    gen_results = evaluate_generation_quality(model, tokenizer)
    results["generation_quality"] = gen_results

    # 요약
    avg_korean_ratio = sum(r["korean_ratio"] for r in gen_results) / len(gen_results)
    repetition_count = sum(1 for r in gen_results if r["has_repetition"])

    results["summary"] = {
        "perplexity": results.get("perplexity"),
        "kobest_boolq_acc": boolq["accuracy"] if boolq else None,
        "kobest_sentineg_acc": sentineg["accuracy"] if sentineg else None,
        "avg_korean_ratio": round(avg_korean_ratio, 2),
        "repetition_issues": repetition_count,
    }

    print("\n" + "=" * 50)
    print("SUMMARY")
    print("=" * 50)
    for k, v in results["summary"].items():
        print(f"  {k:25s}: {v}")

    # 저장
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\nResults saved to {args.output}")


if __name__ == "__main__":
    main()
