#!/usr/bin/env python3
"""Package the patch bundle and an explicit, source-only repository snapshot."""
import hashlib
import pathlib
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
version = (root / "VERSION").read_text().strip()
dist = root / "build/dist"
bundle = dist / ("futo-enhancements-" + version + ".mpp")
archive = dist / ("futo-enhancements-" + version + "-source.zip")
if not bundle.is_file():
    raise SystemExit("Build the patch bundle before packaging a release")
with zipfile.ZipFile(bundle) as patch:
    required = {"META-INF/MANIFEST.MF", "classes.dex", "enhancements.mpe"}
    if not required.issubset(patch.namelist()):
        raise SystemExit("Patch bundle is incomplete")

files = {
    "README.md", "CHANGELOG.md", "CONTRIBUTING.md", "THIRD_PARTY.md", "VERSION", "WTFPL",
    "Dockerfile", "compose.yaml", "Makefile", ".gitignore", ".dockerignore",
    ".editorconfig", ".gitattributes",
}
directories = {"patches", "extensions", "scripts", "tests", "docs", ".github"}
blocked = {".apk", ".apks", ".apkm", ".xapk", ".jar", ".dex", ".class",
           ".jks", ".keystore", ".key", ".pem", ".pyc", ".log", ".zip", ".mpp"}
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if path.is_symlink() or not path.is_file():
            continue
        if relative.as_posix() not in files and relative.parts[0] not in directories:
            continue
        if "__pycache__" in relative.parts or path.suffix in blocked or path.name.startswith(".env"):
            continue
        output.write(path, pathlib.Path("futo-keyboard-enhancements") / relative)
lines = []
for path in [bundle, archive]:
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    lines.append(digest + "  " + path.name)
(dist / "SHA256SUMS").write_text("\n".join(lines) + "\n")
print("Packaged " + str(archive))
