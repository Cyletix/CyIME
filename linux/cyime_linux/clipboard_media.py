"""Bounded image decoding and Android-compatible clipboard categories."""

import base64
import hashlib
import re

from PyQt6.QtCore import QBuffer, QByteArray, QIODevice, Qt
from PyQt6.QtGui import QImage, QImageReader

MAX_IMAGE = 32 * 1024 * 1024
MAX_PIXELS = 16 * 1024 * 1024
MAX_PREVIEW = 48 * 1024
CATEGORIES = (("全部", "all"), ("文本", "text"), ("链接", "link"),
              ("地址", "address"), ("电话", "phone"), ("邮箱", "email"), ("图片", "image"))
PATTERNS = {
    "email": re.compile(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.I),
    "link": re.compile(r"(?:https?://|www\.)\S+", re.I),
    "phone": re.compile(r"(?:\+?[0-9][0-9 ()-]{7,}[0-9])"),
    "address": re.compile(r"〒\s*[0-9]{3}|[都道府県].*[市区町村]|[省市自治区].*[路街巷弄号]|[0-9]+丁目|[0-9]+番地"),
}


def matches(text, kind, category):
    if category == "all": return True
    if kind == "image": return category == "image"
    if category == "text": return True
    if category == "phone":
        return any(10 <= sum(c.isdigit() for c in match.group()) <= 15
                   for match in PATTERNS[category].finditer(text))
    return category in PATTERNS and PATTERNS[category].search(text) is not None


def encode(image, format="PNG", quality=-1):
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    if not image.save(buffer, format, quality):
        raise ValueError("图片编码失败")
    return bytes(data)


def decode(data, limit=MAX_IMAGE, pixels=MAX_PIXELS):
    if not isinstance(data, bytes) or not 0 < len(data) <= limit:
        raise ValueError("图片大小超限或为空")
    buffer = QBuffer()
    buffer.setData(data)
    buffer.open(QIODevice.OpenModeFlag.ReadOnly)
    reader = QImageReader(buffer)
    size = reader.size()
    if not size.isValid() or size.width() * size.height() > pixels:
        raise ValueError("图片尺寸超限")
    image = reader.read()
    if image.isNull(): raise ValueError("无法解码图片")
    return image


def image_record(data):
    image = decode(data)
    thumb = image.scaled(256, 256, Qt.AspectRatioMode.KeepAspectRatio,
                         Qt.TransformationMode.SmoothTransformation)
    preview = encode(thumb, "JPEG", 70)
    reader = QImageReader()
    device = QBuffer()
    device.setData(data)
    device.open(QIODevice.OpenModeFlag.ReadOnly)
    reader.setDevice(device)
    fmt = bytes(reader.format()).decode(errors="ignore").lower()
    mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png",
            "webp": "image/webp", "gif": "image/gif"}.get(fmt)
    if mime is None: raise ValueError("不支持此图片格式")
    value = dict(hash=hashlib.sha256(data).hexdigest(), size=len(data),
                 width=image.width(), height=image.height(), mime=mime,
                 preview=base64.b64encode(preview).decode(), preview_hash=hashlib.sha256(preview).hexdigest())
    return validate_image(value), data


def validate_image(value):
    if not isinstance(value, dict): raise ValueError("无效图片描述")
    for key in ("hash", "preview_hash"):
        if not isinstance(value.get(key), str) or re.fullmatch("[0-9a-f]{64}", value[key]) is None:
            raise ValueError("无效图片哈希")
    if value.get("mime") not in ("image/png", "image/jpeg", "image/webp", "image/gif"):
        raise ValueError("不支持此图片格式")
    if any(type(value.get(key)) is not int or value[key] <= 0 for key in ("size", "width", "height")):
        raise ValueError("无效图片尺寸")
    if value["size"] > MAX_IMAGE or value["width"] * value["height"] > MAX_PIXELS:
        raise ValueError("图片超出 32 MiB / 1600 万像素限制")
    encoded = value.get("preview")
    if not isinstance(encoded, str) or len(encoded) > MAX_PREVIEW * 4 // 3 + 4:
        raise ValueError("预览过大")
    preview = base64.b64decode(encoded, validate=True)
    image = decode(preview, MAX_PREVIEW, 256 * 256)
    if image.width() > 256 or image.height() > 256 or hashlib.sha256(preview).hexdigest() != value["preview_hash"]:
        raise ValueError("图片预览校验失败")
    return {key: value[key] for key in ("hash", "size", "width", "height", "mime", "preview", "preview_hash")}


def validate_original(value, data):
    if len(data) != value["size"] or hashlib.sha256(data).hexdigest() != value["hash"]:
        raise ValueError("原图校验失败")
    image = decode(data)
    if (image.width(), image.height()) != (value["width"], value["height"]):
        raise ValueError("原图尺寸与预览描述不一致")
