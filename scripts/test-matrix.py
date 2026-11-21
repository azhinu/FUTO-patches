#!/usr/bin/env python3
"""Exercise real Morphe patch combinations and fail-fast guards."""
import argparse
import io
import os
import wave
import json
import pathlib
import re
import subprocess
import zipfile
import hashlib
from targets import package_name, ime_metadata_path

ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk",type=pathlib.Path,help="Original FUTO Play Store or RUTO unstable APK")
args=parser.parse_args()
APK=args.apk.resolve()
if not APK.is_file():
    parser.error("Original APK does not exist: "+str(APK))
CLI=pathlib.Path(os.environ.get("MORPHE_CLI","/opt/morphe/morphe-cli.jar"))
BUNDLE=ROOT/("build/dist/futo-enhancements-"+(ROOT/"VERSION").read_text().strip()+".mpp")
AAPT=pathlib.Path("/opt/android/android-16/aapt2")
OUT=ROOT/"build/matrix"
OUT.mkdir(parents=True,exist_ok=True)
NAMES=["Dynamic suggestion bar","Remote speech backends","Android IME layout subtypes","System spell checker", "Spacebar long press duration", "Character backspace after swipe"]
subprocess.run(["/opt/kotlinc/bin/kotlinc","-Xskip-metadata-version-check","-jvm-target","17","-cp",str(CLI),str(ROOT/"scripts/Inspect.kt"),"-d",str(ROOT/"build/inspect.jar")],check=True)
badging=subprocess.check_output([str(AAPT),"dump","badging",str(APK)],text=True)
try:
    original_package=package_name(badging)
except ValueError as error:
    parser.error(str(error))
original_version=re.search(r"versionName='([^']+)'",badging.splitlines()[0]).group(1)
with zipfile.ZipFile(APK) as original:
    preserved={entry:hashlib.sha256(original.read(entry)).digest() for entry in original.namelist()
               if not entry.endswith("/") and entry.startswith(("lib/","assets/"))}

def manifest_fixture(destination,old,new):
    if len(old.encode())!=len(new.encode()) or len(old.encode("utf-16le"))!=len(new.encode("utf-16le")):
        raise ValueError("Manifest fixture strings must have identical encoded lengths")
    replacements=0
    with zipfile.ZipFile(APK) as source,zipfile.ZipFile(destination,"w") as target:
        for entry in source.infolist():
            content=source.read(entry.filename)
            if entry.filename=="AndroidManifest.xml":
                for encoding in ["utf-8","utf-16le"]:
                    before,after=old.encode(encoding),new.encode(encoding)
                    replacements+=content.count(before)
                    content=content.replace(before,after)
            target.writestr(entry,content)
    if replacements==0:
        raise AssertionError("Manifest fixture did not change its target string")

results=[]
for label,indexes in [("dynamic",[0]),("remote",[1]),("subtypes",[2]),("spell",[3]),("space",[4]),("swipe-backspace",[5]),("key-controls",[4,5]),("resources",[2,3]),("resources-reversed",[3,2]),("all",[0,1,2,3,4,5])]:
    output=OUT/(label+".apk")
    command=["java","-jar",str(CLI),"patch",str(APK),"-p",str(BUNDLE),"--exclusive","--validate","--bytecode-mode","FULL","--unsigned","-o",str(output),"-r",str(OUT/(label+".json"))]
    for index in indexes: command += ["-e",NAMES[index]]
    with (OUT/(label+".log")).open("w") as log: subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True)
    report=json.loads((OUT/(label+".json")).read_text())
    assert report["success"] and not report["failedPatches"] and set(report["appliedPatches"])=={NAMES[i] for i in indexes},label
    with zipfile.ZipFile(output) as archive:
        for entry,digest in preserved.items():
            assert hashlib.sha256(archive.read(entry)).digest()==digest,(label,"Changed native library or asset",entry)
        assert ("assets/futo-enhancements-test-en.wav" in archive.namelist()) == (1 in indexes),label
        if 1 in indexes:
            for language in ["en","ru"]:
                with wave.open(io.BytesIO(archive.read("assets/futo-enhancements-test-"+language+".wav")),"rb") as audio:
                    assert (audio.getnchannels(),audio.getsampwidth(),audio.getframerate())==(1,2,16000),label
                    assert audio.getnframes()>16000,label
    metadata=subprocess.check_output([str(AAPT),"dump","xmltree","--file",ime_metadata_path(output),str(output)],text=True)
    assert re.search(r"suppressesSpellChecker.*="+("false" if 3 in indexes else "true"),metadata),label
    assert re.search(r"overridesImplicitlyEnabledSubtype.*="+("false" if 2 in indexes else "true"),metadata),label
    permissions=subprocess.check_output([str(AAPT),"dump","permissions",str(output)],text=True)
    assert ("android.permission.INTERNET" in permissions) == (1 in indexes),label
    assert "android.permission.RECORD_AUDIO" in permissions,label
    badging=subprocess.check_output([str(AAPT),"dump","badging",str(output)],text=True)
    assert package_name(badging)==original_package,label
    if 0 in indexes or 2 in indexes:
        dump=subprocess.check_output(["java","-cp",str(ROOT/"build/inspect.jar")+":"+str(CLI),"InspectKt",str(output),"/LatinIME;","dump"],text=True)
        block=dump.split("METHOD onStartInputView(",1)[1].split("\nMETHOD ",1)[0]
        lines=block.splitlines()
        for i,line in enumerate(lines):
            if " RETURN_VOID " in line:
                previous="\n".join(lines[max(0,i-3):i])
                if 0 in indexes: assert "Dynamic;->start" in previous,label
                if 2 in indexes: assert "SubtypeSync;->start" in previous,label
    results.append({"case":label,"status":"passed","patches":[NAMES[i] for i in indexes]})
    output.unlink()
    print("Passed "+label,flush=True)

# The CLI filters other packages even with --force; exercise the explicit guard directly.
subprocess.run(["/opt/kotlinc/bin/kotlinc","-Xskip-metadata-version-check","-jvm-target","17","-cp",str(CLI)+":"+str(BUNDLE),str(ROOT/"scripts/GuardTest.kt"),"-d",str(ROOT/"build/guard.jar")],check=True)
unsupported=OUT/"unsupported-package.apk"
manifest_fixture(unsupported,original_package,original_package.replace("org.futo.","org.test."))
subprocess.run(["java","-cp",str(ROOT/"build/guard.jar")+":"+str(BUNDLE)+":"+str(CLI),"GuardTestKt",str(unsupported)],check=True,cwd=ROOT)
unsupported.unlink()
results.append({"case":"direct-unsupported-package-preflight","status":"passed"})


# Synthetic metadata fixtures: identical DEX, release names outside the tested baseline.
# These prove there is no version whitelist, not compatibility with real untested releases.
versions=[re.sub(r"[0-9]",digit,original_version) for digit in ["0","9"]]
if len(set(versions+[original_version]))!=3:
    raise SystemExit("Version fixtures require a baseline version with mixed digits")
for version in versions:
    fixture=OUT/("version-"+version+".apk")
    manifest_fixture(fixture,original_version,version)
    badging=subprocess.check_output([str(AAPT),"dump","badging",str(fixture)],text=True)
    assert "versionName='"+version+"'" in badging
    output=OUT/("version-output-"+version+".apk")
    command=["java","-jar",str(CLI),"patch",str(fixture),"-p",str(BUNDLE),"--exclusive","--validate","--bytecode-mode","FULL","--unsigned","-o",str(output),"-r",str(output.with_suffix(".json"))]
    for name in NAMES: command+=["-e",name]
    run=subprocess.run(command,capture_output=True,text=True)
    report=json.loads(output.with_suffix(".json").read_text())
    assert run.returncode==0 and report["success"] and not report["failedPatches"],run.stdout+run.stderr
    results.append({"case":"unlisted-version-"+version,"status":"passed","fixture":"Synthetic metadata; baseline DEX"})
    output.unlink(); fixture.unlink()
    print("Passed unlisted version "+version,flush=True)

fixture=OUT/"missing-timer-method.apk"
with zipfile.ZipFile(APK) as source,zipfile.ZipFile(fixture,"w") as target:
    for entry in source.infolist():
        content=source.read(entry.filename)
        if re.fullmatch(r"classes[0-9]*\.dex",entry.filename):
            content=content.replace(b"startLongPressTimer",b"startLongPressTimeX")
        target.writestr(entry,content)
output=OUT/"missing-timer-output.apk"
command=["java","-jar",str(CLI),"patch",str(fixture),"-p",str(BUNDLE),"--exclusive","-e",NAMES[4],"--unsigned","-o",str(output)]
run=subprocess.run(command,capture_output=True,text=True)
assert run.returncode!=0 and "Target method missing or ambiguous" in run.stdout+run.stderr and not output.exists(),run.stdout+run.stderr
fixture.unlink()
results.append({"case":"missing-timer-method-no-output","status":"passed"})
(ROOT/"build/matrix-results.json").write_text(json.dumps(results,indent=2))
print("Passed "+str(len(results))+" Morphe integration cases",flush=True)
