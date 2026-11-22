"""Put the raw screens from capture.py on the store artwork: 1080x1920 PNGs, one per screen and language.

    python frame.py              # every language that has raw/<locale>/
    python frame.py ka-GE        # one language

Writes ../../fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png … 5.png, the folder F-Droid
reads and the files to upload to Google Play. Needs Playwright with the installed Chrome
(uv run --with playwright --with pillow python frame.py), and internet for the Russian and Georgian fonts.

background.jpg is a 5400x1920 panorama made with Codex in the style of vladmas.com. Each screenshot shows
the next fifth of it, so the wave runs on from one screenshot to the next in the store.
"""
import html
import io
import json
import mimetypes
import sys
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
FASTLANE = HERE.parent.parent / "fastlane" / "metadata" / "android"
CONTENT = json.loads((HERE / "content.json").read_text(encoding="utf-8"))
SCREENS = ["shade", "list", "setup", "undismissable", "pair"]   # in store order
# where the phone sits: "top" shows the top of the screen under the caption; "bottom" hangs the phone from
# the top edge and shows the lower part of the screen, for content that sits low (the swipe-proof card)
LAYOUTS = ["top", "top", "top", "bottom", "top"]

FONTS = {
    # the site's typeface has Latin only, so Russian gets a close monospace and Georgian a clean sans
    "en-US": ("'0xProto'", ""),
    "ru-RU": ("'JetBrains Mono'", "family=JetBrains+Mono:wght@400;700"),
    "ka-GE": ("'Noto Sans Georgian'", "family=Noto+Sans+Georgian:wght@400;600"),
}

GRAIN = ("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'>"
         "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/>"
         "<feColorMatrix values='0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 0'/></filter>"
         "<rect width='100%' height='100%' filter='url(%23n)'/></svg>")

PAGE = """<!doctype html>
<html><head><meta charset="utf-8">
{google}
<style>
  @font-face {{ font-family: "0xProto"; src: url("/fonts/0xProto-Regular.woff2") format("woff2"); }}
  * {{ box-sizing: border-box; margin: 0; }}
  html, body {{ width: 1080px; height: 1920px; overflow: hidden; background: #04060b; }}
  body {{
    position: relative;
    font-family: {font}, ui-monospace, monospace;
    color: #dce5ff;
    -webkit-font-smoothing: antialiased;
  }}
  .bg {{
    position: absolute; inset: 0;
    background: url("/background.jpg") {bg_x}px 0 / 5400px 1920px no-repeat;
  }}
  /* the accent's haze, and the site's film grain */
  .bg::before {{
    content: ""; position: absolute; inset: 0;
    background: radial-gradient(70% 40% at {haze}% {haze_y}%, rgba(124, 92, 235, 0.22), transparent 70%);
  }}
  .bg::after {{
    content: ""; position: absolute; inset: 0; opacity: 0.07; mix-blend-mode: overlay;
    background-image: url("{grain}");
  }}
  .caption {{ position: absolute; left: 88px; right: 88px; }}
  .top .caption {{ top: 132px; }}
  .bottom .caption {{ bottom: 150px; }}
  .kicker {{ font-size: 30px; color: #ab92ff; letter-spacing: 0.01em; }}
  h1 {{
    margin-top: 26px;
    font-size: {title_size}px; font-weight: 400; line-height: 1.12; letter-spacing: {tracking};
    text-wrap: balance;
  }}
  p {{ margin-top: 28px; font-size: 32px; line-height: 1.5; color: #8e9ab3; text-wrap: pretty; }}

  .phone {{
    position: absolute; left: 50%; width: 812px; margin-left: -406px;
    padding: 16px; border-radius: 76px;
    background: #0b0e16;
    box-shadow:
      inset 0 0 0 2px rgba(255, 255, 255, 0.1),
      0 60px 120px -30px rgba(0, 0, 0, 0.9),
      0 0 120px -10px rgba(124, 92, 235, 0.42);
  }}
  .top .phone {{ top: {phone_top}px; }}
  .bottom .phone {{ bottom: {phone_bottom}px; }}
  .phone img {{ display: block; width: 780px; height: auto; border-radius: 60px; }}
</style></head>
<body class="{layout}">
  <div class="bg"></div>
  <div class="phone"><img src="/raw/{locale}/{screen}.png" alt=""></div>
  <div class="caption">
    <div class="kicker">{kicker}</div>
    <h1>{title}</h1>
    <p>{text}</p>
  </div>
</body></html>
"""


def page_html(locale: str, index: int) -> str:
    caption = CONTENT[locale]["captions"][index]
    family, google = FONTS[locale]
    layout = LAYOUTS[index]
    return PAGE.format(
        google=f'<link rel="stylesheet" href="https://fonts.googleapis.com/css2?{google}&display=block">' if google else "",
        font=family,
        title_size={"en-US": 70, "ru-RU": 62, "ka-GE": 58}[locale],
        tracking={"en-US": "-0.045em", "ru-RU": "-0.04em", "ka-GE": "-0.01em"}[locale],
        bg_x=-1080 * index,
        haze=[80, 25, 75, 30, 70][index],
        haze_y=72 if layout == "top" else 30,
        grain=GRAIN,
        layout=layout,
        phone_top=640,
        phone_bottom=740,
        locale=locale,
        screen=SCREENS[index],
        kicker=html.escape(caption["kicker"]),
        title=html.escape(caption["title"]),
        text=html.escape(caption["text"]),
    )


def serve(route):
    path = route.request.url.split("frame.test/", 1)[1].split("?")[0]
    if path.startswith("shot/"):
        locale, index = path.split("/")[1:3]
        route.fulfill(status=200, body=page_html(locale, int(index)), content_type="text/html; charset=utf-8")
        return
    file = HERE / path
    if not file.is_file():
        route.fulfill(status=404, body="not found")
        return
    route.fulfill(status=200, body=file.read_bytes(), content_type=mimetypes.guess_type(file.name)[0] or "application/octet-stream")


locales = sys.argv[1:] or [p.name for p in sorted((HERE / "raw").iterdir()) if p.is_dir()]
with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome")
    page = browser.new_page(viewport={"width": 1080, "height": 1920})
    page.route("http://frame.test/**", serve)
    for locale in locales:
        target = FASTLANE / locale / "images" / "phoneScreenshots"
        target.mkdir(parents=True, exist_ok=True)
        for index in range(len(SCREENS)):
            page.goto(f"http://frame.test/shot/{locale}/{index}", wait_until="networkidle")
            page.evaluate("document.fonts.ready")
            png = page.screenshot(type="png")
            # Google Play wants 24-bit PNGs without an alpha channel
            Image.open(io.BytesIO(png)).convert("RGB").save(target / f"{index + 1}.png", optimize=True)
            print(locale, index + 1, SCREENS[index])
    browser.close()
