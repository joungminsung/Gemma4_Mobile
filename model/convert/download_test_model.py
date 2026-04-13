"""테스트용 Gemma 모델 다운로드 + MediaPipe 변환.

튜닝 없이 기본 Gemma 모델을 MediaPipe .task 파일로 변환하여
앱 테스트에 사용할 수 있게 합니다.

사용법:
    python model/convert/download_test_model.py

변환된 파일은 model/output/test_model/ 에 저장됩니다.
이 파일을 안드로이드 기기에 push하면 앱에서 바로 테스트 가능합니다.

    adb push model/output/test_model/gemma4_ko_lite.task /data/local/tmp/gemma4mobile/
"""

import argparse
import subprocess
import sys
from pathlib import Path


def check_kaggle_cli():
    """Kaggle CLI 설치 확인."""
    try:
        subprocess.run(["kaggle", "--version"], capture_output=True, check=True)
        return True
    except (FileNotFoundError, subprocess.CalledProcessError):
        return False


def download_from_kaggle(output_dir: str):
    """Kaggle에서 MediaPipe용 Gemma 모델 다운로드."""
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    print("Kaggle에서 Gemma 모델 다운로드 중...")
    print("(google/gemma/mediaPipe 변형)")

    # Gemma 2B IT (instruction-tuned) MediaPipe 버전
    subprocess.run([
        "kaggle", "models", "instances", "versions", "download",
        "google/gemma/tfLite/gemma3-1b-it-int4",
        "-p", output_dir,
    ], check=True)

    print(f"다운로드 완료: {output_dir}")


def download_from_huggingface(output_dir: str):
    """HuggingFace에서 모델 다운로드 + MediaPipe 변환."""
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    try:
        from mediapipe.tasks.python.genai import converter
    except ImportError:
        print("ERROR: mediapipe가 설치되어 있지 않습니다.")
        print("  pip install mediapipe")
        sys.exit(1)

    print("HuggingFace에서 Gemma 모델 다운로드 + 변환 중...")
    print("(이 과정은 수 분 소요됩니다)")

    config = converter.ConversionConfig(
        input_ckpt="google/gemma-3-1b-it",
        ckpt_format="huggingface",
        model_type="GEMMA",
        backend="gpu",
        output_dir=output_dir,
        output_tflite_file="gemma4_ko_lite.task",
    )

    converter.convert_checkpoint(config)
    print(f"변환 완료: {output_dir}/gemma4_ko_lite.task")


def main():
    parser = argparse.ArgumentParser(description="테스트용 Gemma 모델 준비")
    parser.add_argument("--output_dir", type=str, default="output/test_model")
    parser.add_argument("--source", type=str, default="huggingface",
                        choices=["kaggle", "huggingface"],
                        help="다운로드 소스 (기본: huggingface)")
    args = parser.parse_args()

    if args.source == "kaggle":
        if not check_kaggle_cli():
            print("ERROR: Kaggle CLI가 설치되어 있지 않습니다.")
            print("  pip install kaggle")
            print("  ~/.kaggle/kaggle.json에 API 키 설정 필요")
            sys.exit(1)
        download_from_kaggle(args.output_dir)
    else:
        download_from_huggingface(args.output_dir)

    print("\n=== 앱 테스트 방법 ===")
    print(f"1. 기기에 모델 push:")
    print(f"   adb shell mkdir -p /data/local/tmp/gemma4mobile")
    print(f"   adb push {args.output_dir}/gemma4_ko_lite.task /data/local/tmp/gemma4mobile/")
    print(f"")
    print(f"2. 앱을 debug 모드로 빌드하여 실행")
    print(f"   → 온보딩 화면에서 '개발자 모드' 토글 → 로컬 모델 로드")


if __name__ == "__main__":
    main()
