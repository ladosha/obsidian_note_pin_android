"""Capture the store screenshots' raw screens on a running emulator, in one language.

    python capture.py ru-RU

Boot the emulator in that language first, for example
    emulator -avd <avd> -change-locale ru-RU -no-snapshot
with the app (and Obsidian, so Setup's Obsidian card is green) installed.

Saves raw/<locale>/{shade,list,setup,undismissable,pair}.png: full 1080x2400 screens that frame.py then
puts on the store artwork. The demo notes and button labels come from content.json.
"""
import base64
import json
import re
import subprocess
import sys
import time
import urllib.parse
from datetime import date, datetime, timedelta
from pathlib import Path

PKG = "com.vladmas.notepinobsidian"
HERE = Path(__file__).resolve().parent
ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")

locale = sys.argv[1]
content = json.loads((HERE / "content.json").read_text(encoding="utf-8"))[locale]
out = HERE / "raw" / locale
out.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True) -> str:
    result = subprocess.run([ADB, *args], capture_output=True, check=check)
    return result.stdout.decode("utf-8", "replace")


def sh(command: str) -> str:
    return adb("shell", command)


def shot(name: str) -> None:
    png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
    (out / f"{name}.png").write_bytes(png)
    print("  saved", name)


def screen_nodes() -> list[tuple[str, tuple[int, int, int, int]]]:
    """(text, bounds) of every node on screen with text."""
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    nodes = []
    for text, x1, y1, x2, y2 in re.findall(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if text:
            nodes.append((text.replace("&apos;", "'").replace("&quot;", '"').replace("&amp;", "&"), (int(x1), int(y1), int(x2), int(y2))))
    return nodes


def find(label: str, scroll: bool = True, tries: int = 8):
    """Bounds of the node whose text is `label`, scrolling down to it if needed."""
    for _ in range(tries):
        for text, bounds in screen_nodes():
            if text == label:
                return bounds
        if not scroll:
            break
        sh("input swipe 540 1700 540 900 400")
        time.sleep(0.8)
    raise SystemExit(f"'{label}' not found on screen")


def tap(label: str, scroll: bool = True) -> None:
    x1, y1, x2, y2 = find(label, scroll)
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")


def bring_to(label: str, top: int) -> None:
    """Scrolls so that `label` sits about `top` px from the top of the screen."""
    _, y1, _, _ = find(label)
    distance = y1 - top
    if abs(distance) > 20:
        sh(f"input swipe 540 1900 540 {1900 - distance} 1500")  # slow, so it doesn't fling
        time.sleep(1)


def demo_status_bar() -> None:
    sh("settings put global sysui_demo_allowed 1")
    for extras in (
        "command enter",
        "command clock -e hhmm 0930",
        "command battery -e level 100 -e plugged false",
        "command network -e wifi show -e level 4 -e fully true",
        "command network -e mobile show -e datatype none -e level 4 -e fully true",
    ):
        sh(f"am broadcast -a com.android.systemui.demo -e {extras}")


def status_icons(visible: bool) -> None:
    """Notification icons in the status bar. Hidden until the pins exist, so a system download or update
    can't put its icon on the Setup and pairing screens."""
    sh(f"am broadcast -a com.android.systemui.demo -e command notifications -e visible {str(visible).lower()}")


def clear_other_notifications() -> None:
    """The emulator's own notifications (Play Protect, keyboard) would show in the shade and the status bar."""
    sh("cmd statusbar expand-notifications")
    time.sleep(2)
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    m = re.search(r'resource-id="com\.android\.systemui:id/dismiss_text"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
        time.sleep(1)
    sh("cmd statusbar collapse")


def separate_notifications() -> None:
    """Turns on the app's "Separate notifications" setting, so each pinned note gets a card in its own color."""
    prefs = HERE / "raw" / "settings.xml"
    prefs.write_text("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                     '<map>\n    <boolean name="separate_pins" value="true" />\n</map>\n', encoding="utf-8")
    adb("push", str(prefs), "/data/local/tmp/np-settings.xml")
    sh("chmod 644 /data/local/tmp/np-settings.xml")
    sh(f"run-as {PKG} mkdir -p shared_prefs")
    sh(f"run-as {PKG} cp /data/local/tmp/np-settings.xml shared_prefs/settings.xml")
    prefs.unlink()


def snapshot_link(items: list[dict], token: str) -> str:
    snapshot = {
        "v": 1, "req": "demo", "kind": "snapshot", "vault": content["vault"],
        "sentAt": datetime.now().isoformat(), "settings": {"allDayTime": "09:00"}, "items": items,
    }
    data = base64.urlsafe_b64encode(json.dumps(snapshot, ensure_ascii=False).encode("utf-8")).decode().rstrip("=")
    query = urllib.parse.urlencode({"v": "1", "req": "demo", "vault": content["vault"], "token": token, "data": data})
    return f"vladmas-note-pin://sync?{query}"


def send(link: str) -> None:
    sh(f"am start -a android.intent.action.VIEW -d '{link}'")


def item(key: str, color: str, kind: str, at: str | None, repeat: str = "none") -> dict:
    note = content["notes"][key]
    return {
        "id": f"demo-{key}", "path": f"{note['title']}.md", "title": note["title"], "preview": note["preview"],
        "subtitle": note.get("subtitle"), "color": color, "type": kind, "at": at, "repeat": repeat,
    }


def next_weekday(today: date, weekday: int) -> date:
    return today + timedelta(days=(weekday - today.weekday()) % 7 or 7)


# --- the phone ---------------------------------------------------------------
print(f"[{locale}] preparing the phone")
labels = content["labels"]
sh(f"pm clear {PKG}")
sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
sh(f"appops set {PKG} SCHEDULE_EXACT_ALARM allow")
sh(f"dumpsys deviceidle whitelist +{PKG}")
sh(f"appops set --uid {PKG} SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS allow")
sh("settings put global default_device_name 'Pixel 9'")
sh("cmd uimode night yes")
sh("cmd overlay enable com.android.internal.systemui.navbar.gestural")
separate_notifications()
clear_other_notifications()
demo_status_bar()
time.sleep(2)

now = datetime.strptime(sh("date +%Y-%m-%dT%H:%M:%S").strip(), "%Y-%m-%dT%H:%M:%S")
today = now.date()
items = [
    item("groceries", "green", "statusbar", None),
    item("packing", "orange", "statusbar", None),
    item("focus", "purple", "statusbar", None),
    item("plants", "green", "time", f"{today}T19:00", "daily"),
    item("dentist", "red", "time", f"{next_weekday(today, 0)}T10:00"),
    item("review", "blue", "time", f"{next_weekday(today, 4)}T17:00", "weekly"),
    item("birthday", "yellow", "allday", f"{today + timedelta(days=18)}", "yearly"),
]
token = "demo-token-" + locale

# --- 3 and 4: Setup, on first launch -------------------------------------------
print(f"[{locale}] setup")
status_icons(False)
sh(f"am start -W -n {PKG}/.MainActivity")
time.sleep(3)
shot("setup")
tap(labels["post_test"])
time.sleep(9)  # let the test notification's pop-up go away
bring_to(labels["undismissable"], 260)
shot("undismissable")
tap(labels["remove"])
time.sleep(1)
tap(labels["continue"])
time.sleep(2)

# --- 5: a vault asks to connect ------------------------------------------------
print(f"[{locale}] pairing")
send(snapshot_link(items, token))
time.sleep(3)
shot("pair")
tap(labels["allow"], scroll=False)
time.sleep(3)

# --- 2: the main list ----------------------------------------------------------
print(f"[{locale}] list")
status_icons(True)
sh(f"am start -W -n {PKG}/.MainActivity")
time.sleep(3)
shot("list")

# --- 1: the notification shade, with a reminder that just went off --------------
print(f"[{locale}] shade")
fire = (now + timedelta(minutes=2)).replace(second=0)
now_again = datetime.strptime(sh("date +%Y-%m-%dT%H:%M:%S").strip(), "%Y-%m-%dT%H:%M:%S")
if (fire - now_again).total_seconds() < 45:
    fire += timedelta(minutes=1)
standup = item("standup", "blue", "time", fire.strftime("%Y-%m-%dT%H:%M"))
send(snapshot_link(items + [standup], token))
sh("input keyevent KEYCODE_HOME")
wait = (fire - now_again).total_seconds() + 12  # the alarm, then its pop-up collapses
print(f"  waiting {wait:.0f} s for the reminder at {fire:%H:%M}")
time.sleep(wait)
sh("cmd statusbar expand-notifications")
time.sleep(2.5)
shot("shade")
sh("cmd statusbar collapse")
print(f"[{locale}] done")
