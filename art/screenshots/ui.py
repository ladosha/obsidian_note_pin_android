"""Small adb helpers for driving the emulator's UI: list what's on screen, tap by text or id.

    python ui.py dump                 # text, resource-id and bounds of every node on screen
    python ui.py tap "Add a language" # tap the node with that exact text (or resource-id)
"""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
NODE = re.compile(r'<node [^>]*?text="([^"]*)" resource-id="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def sh(command: str) -> str:
    return subprocess.run([ADB, "shell", command], capture_output=True).stdout.decode("utf-8", "replace")


def nodes() -> list[dict]:
    sh("uiautomator dump /sdcard/ui.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
    return [
        {"text": t, "id": i, "desc": d, "bounds": (int(a), int(b), int(c), int(e))}
        for t, i, d, a, b, c, e in NODE.findall(xml)
    ]


def find(key: str, tries: int = 6) -> dict | None:
    for _ in range(tries):
        for n in nodes():
            if key in (n["text"], n["id"], n["desc"]):
                return n
        time.sleep(1)
    return None


def tap(key: str) -> bool:
    n = find(key)
    if not n:
        return False
    x1, y1, x2, y2 = n["bounds"]
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    return True


if __name__ == "__main__":
    if sys.argv[1] == "dump":
        for n in nodes():
            if n["text"] or n["id"] or n["desc"]:
                print(n["bounds"], repr(n["text"]), n["id"], repr(n["desc"]) if n["desc"] else "")
    elif sys.argv[1] == "tap":
        print("tapped" if tap(sys.argv[2]) else "NOT FOUND")
