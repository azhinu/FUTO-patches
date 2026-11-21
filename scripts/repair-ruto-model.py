#!/usr/bin/env python3
"""Repair the missing embedding-mixing flag in the inspected RUTO Russian GGUF.

Only the exact, inspected model is accepted. Tensor data is copied unchanged.
The input and an existing output are never overwritten.
"""
import argparse
import hashlib
import pathlib
import struct

MODEL_SHA256 = "f0d3766d918e42d1eff29c81e3617737b9ed59400c94c0b7c2bfb93e00a1efac"
# These boundaries belong only to the hash-pinned GGUF v2 model above.
DATA_OFFSET = 1456384
ALIGNMENT = 32
OLD_FEATURES = b"xbu_char_autocorrect_v1"
NEW_FEATURES = OLD_FEATURES + b" char_embed_mixing_v1"


def repair(source, destination):
    original = source.read_bytes()
    if hashlib.sha256(original).hexdigest() != MODEL_SHA256:
        raise ValueError("Unsupported model: expected the inspected RUTO Russian 65M Q8_0 GGUF")
    if destination.exists():
        raise ValueError("Output already exists; choose a new path")
    if original[:8] != b"GGUF\x02\x00\x00\x00":
        raise ValueError("Unexpected GGUF header")
    before = struct.pack("<Q", len(OLD_FEATURES)) + OLD_FEATURES
    after = struct.pack("<Q", len(NEW_FEATURES)) + NEW_FEATURES
    header = original[:DATA_OFFSET]
    if header.count(before) != 1:
        raise ValueError("Missing or ambiguous model feature metadata")
    header = header.replace(before, after)
    header += b"\0" * (-len(header) % ALIGNMENT)
    payload = original[DATA_OFFSET:]
    result = header + payload
    assert result[len(header):] == payload
    with destination.open("xb") as output:
        output.write(result)
    print("Repaired " + str(destination))
    print("Tensor payload unchanged: SHA-256 " + hashlib.sha256(payload).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=pathlib.Path)
    parser.add_argument("destination", type=pathlib.Path)
    args = parser.parse_args()
    try:
        repair(args.source, args.destination)
    except (OSError, ValueError) as error:
        parser.exit(1, "Model repair failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
