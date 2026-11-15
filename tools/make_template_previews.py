"""Makes the template gallery's preview images (roadmap M1.4): assets/templates/previews/<scene name>.png.

Each sample listed in assets/templates/gallery.json is played in the real game runtime for a short smoke test; the runtime saves its
own last frame (GDXUNITY_SCREENSHOT, TD-054), which is cropped to 16:9 and scaled to 400 by 225. Nothing is drawn by hand, so a preview
shows exactly what Play shows after about two seconds with no keys pressed.

Build the runtime first, then run from the repository root:

    ./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses
    python tools/make_template_previews.py [scene name ...]

Needs a display and Pillow. With scene names (for example racing-3d), only those previews are made.
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
CATALOG = ASSETS / "templates" / "gallery.json"
PREVIEWS = ASSETS / "templates" / "previews"
LIBRARIES = ROOT / "engine-desktop" / "build" / "install" / "Forgeroom" / "lib"
MAIN_CLASS = "io.github.ldev22live.gdxunity.engine.desktop.DesktopLauncher"
SCRIPTS = ROOT / "engine-desktop" / "build" / "classes" / "java" / "gameScripts"
# About two seconds at 60 frames per second: past the first frame, before most games end on their own
FRAMES = 120
WIDTH, HEIGHT = 400, 225
# A game that does not finish its smoke test in this time is reported, not waited for
TIMEOUT_SECONDS = 180


def runtime_command():
    """Java with the installed runtime's libraries; a class-path wildcard, because the Windows launcher's line is too long here."""
    if not LIBRARIES.is_dir() or not SCRIPTS.is_dir():
        sys.exit(f"No game runtime at {LIBRARIES}; build it with ./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses")
    home = os.environ.get("JAVA_HOME")
    java = str(Path(home) / "bin" / "java") if home else "java"
    command = [java, "-cp", str(LIBRARIES / "*"), f"-Dgdxunity.scripts={SCRIPTS}", "--enable-native-access=ALL-UNNAMED"]
    if sys.platform == "darwin":
        command.append("-XstartOnFirstThread")
    return command + [MAIN_CLASS]


def crop_to_preview(source, target):
    with Image.open(source) as image:
        image = image.convert("RGB")
        width, height = image.size
        wanted = width * HEIGHT / WIDTH
        if height > wanted:
            top = int((height - wanted) / 2)
            image = image.crop((0, top, width, top + int(wanted)))
        else:
            wanted_width = height * WIDTH / HEIGHT
            left = int((width - wanted_width) / 2)
            image = image.crop((left, 0, left + int(wanted_width), height))
        image.resize((WIDTH, HEIGHT), Image.LANCZOS).save(target, optimize=True)


def main(names):
    launcher = runtime_command()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    failed = []
    with tempfile.TemporaryDirectory() as temp:
        for entry in catalog["templates"]:
            scene = entry["scene"]
            name = Path(scene).stem
            if names and name not in names:
                continue
            frame = Path(temp) / f"{name}.png"
            env = dict(os.environ, GDXUNITY_SCREENSHOT=str(frame))
            try:
                result = subprocess.run(launcher + ["--scene", scene, f"--smoke-test={FRAMES}"], cwd=ASSETS, env=env,
                                        capture_output=True, text=True, timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                failed.append(f"{name}: no result after {TIMEOUT_SECONDS} s")
                continue
            if result.returncode != 0 or not frame.exists():
                failed.append(f"{name}: exit {result.returncode}: {(result.stderr or result.stdout).strip()[-400:]}")
                continue
            crop_to_preview(frame, PREVIEWS / f"{name}.png")
            print(f"{name}: preview written")
    if failed:
        print("\n".join(["Failed:"] + failed), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main(set(sys.argv[1:]))
