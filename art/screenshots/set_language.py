"""Make one of the emulator's added languages the system language, by dragging it to the top in Settings.

    python set_language.py "ქართული (საქართველო)"

The language must already be in Settings → System → Languages (add it there once, by hand or with ui.py).
The emulator's -change-locale flag doesn't work on Google Play system images, and they can't run adb root.
"""
import sys
import time

from ui import find, sh, tap

label = sys.argv[1]
sh("am start -a android.settings.LOCALE_SETTINGS")
time.sleep(2)
row = find(label)
if not row:
    raise SystemExit(f"'{label}' isn't in the language list")
handles = [n for n in __import__("ui").nodes() if n["id"] == "com.android.settings:id/dragHandle"]
_, y1, _, y2 = row["bounds"]
handle = min(handles, key=lambda n: abs((n["bounds"][1] + n["bounds"][3]) / 2 - (y1 + y2) / 2))
top = min(handles, key=lambda n: n["bounds"][1])
hx = (handle["bounds"][0] + handle["bounds"][2]) // 2
hy = (handle["bounds"][1] + handle["bounds"][3]) // 2
if handle is not top:
    sh(f"input draganddrop {hx} {hy} {hx} {top['bounds'][1] - 50} 1500")
    time.sleep(1.5)
    if not tap("android:id/button1"):  # "Change system language?"
        raise SystemExit("no confirmation dialog")
    time.sleep(4)
print("system locale:", sh("getprop persist.sys.locale").strip())
sh("input keyevent KEYCODE_HOME")
