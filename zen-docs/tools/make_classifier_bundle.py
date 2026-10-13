#!/usr/bin/env python3
"""Builds the classifier model bundle that ZenPipe downloads (spec E1, `ZenClassifierModel`).

Usage: python3 zen-docs/tools/make_classifier_bundle.py <android-bundle-dir> <out.zip>

<android-bundle-dir> is the YoutubeManager output (`dist/android`) with encoder_int8.onnx,
tokenizer.bin, vocab_map.json and heads.json. The zip is deterministic (fixed order and
timestamps) and contains the license notice of the app (res/raw/zen_classifier_notices.txt) as
NOTICE. Prints SHA-256 and size for ZenClassifierModel.DOWNLOAD_SHA256 / DOWNLOAD_BYTES; upload
the zip as release asset of the tag `model-<VERSION>` (e.g. model-e5s-1). Never add golden.json
(real video titles).
"""
import hashlib
import sys
import zipfile
from pathlib import Path

FILES = ["encoder_int8.onnx", "tokenizer.bin", "vocab_map.json", "heads.json"]
NOTICE = Path(__file__).resolve().parents[2] / "app/src/main/res/raw/zen_classifier_notices.txt"


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    missing = [f for f in FILES if not (src / f).is_file()]
    if missing:
        sys.exit(f"missing in {src}: {missing}")
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in FILES:
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, (src / name).read_bytes())
        info = zipfile.ZipInfo("NOTICE", date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, NOTICE.read_bytes())
    data = out.read_bytes()
    print(f"{out}: {len(data)} bytes")
    print(f"DOWNLOAD_SHA256 = \"{hashlib.sha256(data).hexdigest()}\"")
    print(f"DOWNLOAD_BYTES = {len(data)}L")


if __name__ == "__main__":
    main()
