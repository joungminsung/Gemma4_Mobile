# model/validate/benchmark.py
"""4티어 모델 품질/속도 벤치마크."""

import argparse
import time
import json
from pathlib import Path
from mediapipe.tasks.python.genai import llm_inference


TIER_MODELS = {
    "lite": "output/mediapipe/lite/gemma4_ko_lite.task",
    "standard": "output/mediapipe/standard/gemma4_ko_standard.task",
    "full": "output/mediapipe/full/gemma4_ko_full.task",
    "max": "output/mediapipe/max/gemma4_ko_max.task",
}

TEST_PROMPTS = [
    "한국의 수도는 어디인가요?",
    "인공지능이란 무엇인가요? 간단히 설명해주세요.",
    "오늘 저녁 메뉴 추천해주세요.",
    "Python과 Java의 차이점을 알려주세요.",
    "서울에서 부산까지 가는 방법을 알려주세요.",
]


def benchmark_tier(tier_name: str, model_path: str) -> dict:
    """단일 티어 벤치마크: 응답 품질 + 속도."""
    print(f"\n{'=' * 50}")
    print(f"Benchmarking: {tier_name}")
    print(f"{'=' * 50}")

    model = llm_inference.LlmInference(
        llm_inference.LlmInference.Options(
            model_path=model_path,
            max_tokens=256,
        )
    )

    results = []
    total_tokens = 0
    total_time = 0

    for prompt in TEST_PROMPTS:
        formatted = f"<start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n"

        start = time.perf_counter()
        response = model.generate_response(formatted)
        elapsed = time.perf_counter() - start

        token_count = len(response.split())
        tokens_per_sec = token_count / elapsed if elapsed > 0 else 0

        total_tokens += token_count
        total_time += elapsed

        results.append({
            "prompt": prompt,
            "response": response[:200],
            "time_sec": round(elapsed, 2),
            "tokens": token_count,
            "tokens_per_sec": round(tokens_per_sec, 1),
        })

        print(f"\nQ: {prompt}")
        print(f"A: {response[:100]}...")
        print(f"   {elapsed:.2f}s, {tokens_per_sec:.1f} tok/s")

    avg_tps = total_tokens / total_time if total_time > 0 else 0

    return {
        "tier": tier_name,
        "avg_tokens_per_sec": round(avg_tps, 1),
        "total_time_sec": round(total_time, 2),
        "results": results,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", type=str, choices=TIER_MODELS.keys(),
                        help="특정 티어만 벤치마크 (미지정 시 전체)")
    parser.add_argument("--output", type=str, default="output/benchmark_results.json")
    args = parser.parse_args()

    tiers = [args.tier] if args.tier else list(TIER_MODELS.keys())
    all_results = []

    for tier_name in tiers:
        model_path = TIER_MODELS[tier_name]
        if not Path(model_path).exists():
            print(f"SKIP: {tier_name} — model not found at {model_path}")
            continue
        result = benchmark_tier(tier_name, model_path)
        all_results.append(result)

    print(f"\n{'=' * 50}")
    print("SUMMARY")
    print(f"{'=' * 50}")
    for r in all_results:
        print(f"  {r['tier']:10s} — {r['avg_tokens_per_sec']:6.1f} tok/s")

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2)
    print(f"\nResults saved to {args.output}")


if __name__ == "__main__":
    main()
