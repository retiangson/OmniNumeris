"""Run the pinned host-only formula recognizer on the checked-in math corpus."""

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
from statistics import median
from time import perf_counter


MODEL_DIGESTS = {
    "config.json": "ea32742b976ba34711042cac4e46206f114067949448c2bd8dec60a44d3de1fb",
    "inference.json": "2c8fc31a94d461a7325fe06e8cbaa5db6c5658c490f74d3d6116defe8c859d6f",
    "inference.pdiparams": "b6392296d16e2a9f414c0a751d7ccbd1bd9d8272b68aab72df1d3875f35a7489",
    "inference.yml": "08b21c2d255041008266b8fb947758e9731eafff191568af21da5a4b9c9dd5a0",
}


def recognize(model, image_path):
    start = perf_counter()
    result, = model.predict(str(image_path), batch_size=1)
    return result["rec_formula"], (perf_counter() - start) * 1000


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("model", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    for filename, expected_digest in MODEL_DIGESTS.items():
        with (args.model / filename).open("rb") as file:
            if hashlib.file_digest(file, "sha256").hexdigest() != expected_digest:
                raise SystemExit(f"Model digest mismatch for {filename}")

    os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
    os.environ["HF_HUB_OFFLINE"] = "1"
    from paddleocr import FormulaRecognition

    model = FormulaRecognition(
        model_name="PP-FormulaNet-S",
        model_dir=str(args.model.resolve()),
        device="cpu",
        cpu_threads=4,
        enable_mkldnn=False,
    )

    with (args.corpus / "labels.csv").open(encoding="utf-8-sig", newline="") as labels:
        fixtures = list(csv.DictReader(labels))
    if not fixtures:
        raise SystemExit("No labelled fixtures")

    with args.output.open("w", encoding="utf-8", newline="\n") as output:
        for fixture in fixtures:
            image_path = args.corpus / fixture["file"]
            recognize(model, image_path)
            measured = [recognize(model, image_path) for _ in range(3)]
            latex = measured[0][0]
            record = {
                "fixture": fixture["id"],
                "source": fixture["source"],
                "expected": fixture["canonical_expression"],
                "recognized_latex": latex,
                "stable": all(text == latex for text, _ in measured),
                "warm_median_ms": round(median(ms for _, ms in measured), 2),
            }
            output.write(json.dumps(record, ensure_ascii=False) + "\n")
            output.flush()


if __name__ == "__main__":
    main()
