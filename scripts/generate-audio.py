#!/usr/bin/env python3
"""Generate synthetic speech fixtures locally; no downloaded or recorded audio."""
import array
import pathlib
import subprocess
import sys
import tempfile
import wave

root = pathlib.Path(__file__).resolve().parents[1]
output = root / "build/test-audio"
output.mkdir(parents=True, exist_ok=True)
phrases = {
    "en": "The quick brown fox jumps over the lazy dog.",
    "ru": "Это проверка распознавания русской речи.",
}
with tempfile.TemporaryDirectory(prefix="futo-speech-") as temporary:
    for language, phrase in phrases.items():
        source = pathlib.Path(temporary) / (language + ".wav")
        subprocess.run(["espeak-ng", "-v", language, "-s", "150", "-w", str(source), phrase], check=True)
        with wave.open(str(source), "rb") as audio:
            if audio.getnchannels() != 1 or audio.getsampwidth() != 2:
                raise SystemExit("Unexpected synthesized audio format")
            rate = audio.getframerate()
            samples = array.array("h", audio.readframes(audio.getnframes()))
        if sys.byteorder != "little":
            samples.byteswap()
        converted = array.array("h")
        for index in range(len(samples) * 16000 // rate):
            position = index * rate / 16000
            left = int(position)
            fraction = position - left
            right = min(left + 1, len(samples) - 1)
            converted.append(round(samples[left] * (1 - fraction) + samples[right] * fraction))
        if sys.byteorder != "little":
            converted.byteswap()
        with wave.open(str(output / (language + ".wav")), "wb") as audio:
            audio.setnchannels(1)
            audio.setsampwidth(2)
            audio.setframerate(16000)
            audio.writeframes(converted.tobytes())
print("Generated synthetic English and Russian speech fixtures")
