"""Shared target and compiled-resource inspection for the APK tools."""
import re
import subprocess

AAPT = "/opt/android/android-16/aapt2"
PACKAGES = {"org.futo.inputmethod.latin.playstore", "org.futo.inputmethod.latin.unstable"}


def package_name(badging):
    match = re.search(r"^package: name='([^']+)'", badging)
    if not match or match.group(1) not in PACKAGES:
        raise ValueError("Unsupported target: expected FUTO Play Store or RUTO unstable")
    return match.group(1)


def ime_metadata_path(apk):
    # RUTO stores xml/method under res/aY.xml. Resolve its resource name, not a path guess.
    resources = subprocess.check_output([AAPT, "dump", "resources", str(apk)], text=True)
    matches = re.findall(r"resource 0x[0-9a-f]+ xml/method\n\s+\(\) \(file\) (\S+) type=XML", resources)
    if len(matches) != 1:
        raise ValueError("Missing or ambiguous compiled IME metadata resource")
    return matches[0]
