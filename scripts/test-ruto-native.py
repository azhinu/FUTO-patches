#!/usr/bin/env python3
"""Replay the RUTO Russian native crash in the synthetic Android editor.

Coordinates are specific to Pixel 8 at 1080x2400 with Gboard Russian ЙЦУКЕН v3.
The test selects the requested IME. Select its Russian layout before running.
"""
import argparse
import json
import subprocess
import time

HARNESS = "futo.enhancements.test"
POINTS = [(443, 1864), (541, 1864), (541, 2016), (248, 1864), (443, 1704), (634, 2016)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="org.futo.inputmethod.latin.unstable",
                        choices=["org.futo.inputmethod.latin.unstable", "org.futo.inputmethod.latin.playstore"])
    parser.add_argument("--letters", type=int, choices=range(1, 7), default=2)
    parser.add_argument("--cold-start", action="store_true")
    args = parser.parse_args()

    def adb(*command, required=True):
        result = subprocess.run(["adb", "-s", args.serial, *command], capture_output=True, text=True)
        if required:
            result.check_returncode()
        return result.stdout.strip()

    def broadcast(command):
        adb("shell", "am", "broadcast", "-a", HARNESS + ".COMMAND", "-p", HARNESS, "--es", "command", command)

    if "1080x2400" not in adb("shell", "wm", "size"):
        parser.error("Unsupported display size for the recorded Russian touch replay")
    if args.cold_start:
        adb("shell", "am", "force-stop", args.package)
    adb("shell", "ime", "set", args.package + "/org.futo.inputmethod.latin.LatinIME")
    started = adb("shell", "date", "+%m-%d\\ %H:%M:%S.000").replace("\\", "")
    adb("shell", "am", "start", "-n", HARNESS + "/.MainActivity")
    broadcast("clear")
    broadcast("show")
    time.sleep(4)
    before = adb("shell", "pidof", args.package)
    if not before:
        parser.exit(1, "FAIL: Target IME is not running\n")
    for x, y in POINTS[:args.letters]:
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(0.15)
    time.sleep(3)
    broadcast("snapshot")
    logs = adb("logcat", "-d", "-T", started, "-s", "FutoPatchTest:I")
    snapshots = [json.loads(line.split("Snapshot ", 1)[1]) for line in logs.splitlines() if "Snapshot {" in line]
    actual = snapshots[-1]["text"] if snapshots else None
    expected = "Привет"[:args.letters]
    crash = adb("logcat", "-d", "-b", "crash", "-T", started)
    after = adb("shell", "pidof", args.package, required=False)
    native_crash = "DecodePromptAndMixes" in crash and args.package in crash
    print("Expected text:", expected, "Actual text:", actual)
    print("PID before:", before, "PID after:", after or "Not running")
    if native_crash:
        parser.exit(1, "FAIL: Native DecodePromptAndMixes crash\n")
    if actual != expected or before != after:
        parser.exit(1, "FAIL: Replay text mismatch or IME process restart\n")
    print("PASS: Expected Russian text, stable process, no DecodePromptAndMixes crash")


if __name__ == "__main__":
    main()
