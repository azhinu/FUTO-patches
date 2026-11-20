#!/usr/bin/env python3
"""Apply explicitly selected patches and publish an APK only after report validation."""
import argparse
import json
import os
import pathlib
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
NAMES = ["Dynamic suggestion bar", "Remote speech backends", "Android IME layout subtypes", "System spell checker", "Spacebar long press duration", "Character backspace after swipe"]
parser = argparse.ArgumentParser()
parser.add_argument("apk", type=pathlib.Path)
parser.add_argument("output", type=pathlib.Path)
parser.add_argument("--bundle", type=pathlib.Path)
parser.add_argument("--patch", choices=NAMES, action="append")
parser.add_argument("--unsigned", action="store_true")
args = parser.parse_args()
bundle = args.bundle or ROOT / ("build/dist/futo-enhancements-" + (ROOT / "VERSION").read_text().strip() + ".mpp")
selected = set(args.patch or NAMES)
if args.output.exists():
    raise SystemExit("Output already exists; choose a new path")
badging = subprocess.check_output(["/opt/android/android-16/aapt2", "dump", "badging", str(args.apk)], text=True)
if not all(value in badging.splitlines()[0] for value in ["name='org.futo.inputmethod.latin.playstore'"]):
    raise SystemExit("Unsupported target: expected org.futo.inputmethod.latin.playstore")
args.output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix="futo-apply-", dir=args.output.parent) as temp:
    staged, report = pathlib.Path(temp) / "patched.apk", pathlib.Path(temp) / "result.json"
    command = ["java", "-jar", os.environ.get("MORPHE_CLI", "/opt/morphe/morphe-cli.jar"), "patch", str(args.apk), "-p", str(bundle), "--exclusive", "--validate", "--bytecode-mode", "FULL", "-o", str(staged), "-r", str(report)]
    for name in sorted(selected):
        command += ["-e", name]
    if args.unsigned:
        command.append("--unsigned")
    subprocess.run(command, check=True)
    result = json.loads(report.read_text())
    if not result.get("success") or result.get("failedPatches") or set(result.get("appliedPatches", [])) != selected:
        raise SystemExit("Patching report did not confirm every requested patch; no APK published")
    with zipfile.ZipFile(staged) as apk:
        if "AndroidManifest.xml" not in apk.namelist():
            raise SystemExit("Patched APK is incomplete")
    permissions = subprocess.check_output(["/opt/android/android-16/aapt2", "dump", "permissions", str(staged)], text=True)
    if "Remote speech backends" in selected and "android.permission.INTERNET" not in permissions:
        raise SystemExit("Patched APK is missing INTERNET; no APK published")
    if "android.permission.RECORD_AUDIO" not in permissions:
        raise SystemExit("Patched APK lost RECORD_AUDIO; no APK published")
    result["outputPath"] = str(args.output.resolve())
    staged.replace(args.output)
    args.output.with_suffix(".result.json").write_text(json.dumps(result, indent=2))
print("Built " + str(args.output))
