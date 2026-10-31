"""User-local desktop IME preferences, separate from clipboard trust and Rime data."""

from dataclasses import asdict, dataclass, fields
import json
from pathlib import Path

from .core import default_state_dir, private_dir, write_private


@dataclass(frozen=True)
class ImeSettings:
    page_size: int = 6
    inline_preview: bool = True
    page_keys: bool = True
    tab_pages: bool = True
    ascii_punct: bool = False
    full_shape: bool = False
    theme: str = "system"
    font_size: int = 17
    background: str = ""
    overlay: int = 75
    foreground: str = "#f5f7fa"
    accent: str = "#bfeff4"

    @classmethod
    def from_dict(cls, value):
        defaults = asdict(cls())
        for field in fields(cls):
            candidate = value.get(field.name, defaults[field.name])
            if type(candidate) is type(defaults[field.name]):
                defaults[field.name] = candidate
        for key, lower, upper in (("page_size", 3, 9), ("font_size", 12, 28), ("overlay", 60, 95)):
            defaults[key] = max(lower, min(upper, defaults[key]))
        if defaults["theme"] not in ("system", "light", "dark", "custom"):
            defaults["theme"] = "system"
        for key in ("foreground", "accent"):
            color = defaults[key]
            if len(color) != 7 or not color.startswith("#") or any(c not in "0123456789abcdefABCDEF" for c in color[1:]):
                defaults[key] = getattr(cls(), key)
        return cls(**defaults)


def load(directory=None):
    try:
        value = json.loads(((directory or default_state_dir()) / "ime.json").read_text())
        return ImeSettings.from_dict(value if isinstance(value, dict) else {})
    except (OSError, ValueError):
        return ImeSettings()


def save(settings, directory=None):
    directory = private_dir(directory or default_state_dir())
    settings = ImeSettings.from_dict(asdict(settings))
    write_private(directory / "ime.json", json.dumps(asdict(settings), ensure_ascii=False, indent=2).encode())
    return settings


def import_background(source, directory=None):
    # Decode before importing and cap memory use. The image stays local, outside source control.
    from PyQt6.QtGui import QImageReader
    reader = QImageReader(str(source))
    size = reader.size()
    if not size.isValid() or size.width() * size.height() > 40_000_000:
        raise ValueError("请选择不超过 4000 万像素的图片")
    image = reader.read()
    if image.isNull():
        raise ValueError("无法读取背景图片")
    directory = private_dir((directory or default_state_dir()) / "themes")
    target = directory / "background.png"
    pending = directory / "background-import.png"
    if not image.save(str(pending), "PNG"):
        raise ValueError("无法保存背景图片")
    pending.chmod(0o600)
    pending.replace(target)
    return str(target)


def presentation(context, settings):
    composing = bool(context["preedit"])
    preview = context.get("preview") or (context["candidates"][0][0] if context["candidates"] else context["preedit"])
    inline = preview if settings.inline_preview else context["preedit"]
    return inline if composing else "", context["preedit"] if composing else ""
