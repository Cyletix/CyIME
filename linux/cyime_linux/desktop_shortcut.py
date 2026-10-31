"""Explicit, non-destructive GNOME shortcut registration."""

import shlex


BASE = "/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/"


def configure_clipboard_shortcut(command, root, child):
    paths = root.get_strv("custom-keybindings")
    matches = [path for path in paths
               if child(path).get_string("binding").lower() in ("<super>v", "<mod4>v")]
    if len(matches) > 1:
        raise ValueError("多个 Super+V 绑定，请先在 GNOME 设置中处理冲突")
    path = matches[0] if matches else BASE + "cyime/"
    settings = child(path)
    previous = settings.get_string("command")
    try:
        parts = shlex.split(previous)
    except ValueError:
        parts = []
    ours = len(parts) == 3 and parts[1:] == shlex.split(command)[1:]
    if previous and previous not in ("copyq toggle", command) and not ours:
        raise ValueError("已有快捷键自定义命令，未覆盖：" + path)
    settings.delay()
    for key, value in (("name", "CyIME 剪贴簿"), ("command", command), ("binding", "<Super>v")):
        if not settings.set_string(key, value):
            settings.revert()
            raise RuntimeError("无法写入 GNOME 快捷键")
    settings.apply()
    if path not in paths and not root.set_strv("custom-keybindings", [*paths, path]):
        raise RuntimeError("无法启用 GNOME 快捷键")
    return path


def install_clipboard_shortcut(python, runner):
    from gi.repository import Gio

    command = shlex.join([str(python), str(runner), "clipboard"])
    root = Gio.Settings.new("org.gnome.settings-daemon.plugins.media-keys")
    def child(path):
        return Gio.Settings.new_with_path(
            "org.gnome.settings-daemon.plugins.media-keys.custom-keybinding", path)
    path = configure_clipboard_shortcut(command, root, child)
    Gio.Settings.sync()
    return path
