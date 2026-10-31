#!/usr/bin/env python3
"""Prepare a separate Rime user directory and register a user-local IBus component."""

import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from cyime_linux.core import default_state_dir, private_dir, write_private


def component_search_path():
    local = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "ibus/component"
    defaults = [str(Path(root) / "ibus/component")
                for root in os.environ.get("XDG_DATA_DIRS", "/usr/local/share:/usr/share").split(":") if root]
    existing = os.environ.get("IBUS_COMPONENT_PATH", "").split(":")
    return ":".join(dict.fromkeys([str(local)] + [p for p in existing if p] + defaults))


def install_search_path():
    value = component_search_path()
    if any(c in value for c in ('"', "\\", "\n", "$", "`")):
        raise ValueError("Unsupported character in IBus component paths")
    directory = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "environment.d"
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / "60-cyime-ibus.conf"
    content = f'# CyIME user-local IBus component discovery\nIBUS_COMPONENT_PATH="{value}"\n'.encode()
    if target.exists() and target.read_bytes() != content:
        raise RuntimeError(f"Existing environment configuration preserved: {target}")
    if not target.exists():
        write_private(target, content)
    return value


def refresh_ibus(search_path):
    # Explicit opt-in: replacing IBus interrupts an in-progress composition.
    import gi
    gi.require_version("IBus", "1.0")
    from gi.repository import IBus
    IBus.init()
    bus = IBus.Bus()
    previous = bus.get_global_engine() if bus.is_connected() else None
    environment = dict(os.environ, IBUS_COMPONENT_PATH=search_path)
    for command in (["systemctl", "--user", "import-environment", "IBUS_COMPONENT_PATH"],
                    ["dbus-update-activation-environment", "--systemd", "IBUS_COMPONENT_PATH"]):
        subprocess.run(command, env=environment, check=True, timeout=10)
    subprocess.run(["ibus-daemon", "--replace", "--daemonize", "--xim", "--panel", "disable", "--cache", "refresh"],
                   env=environment, check=True, timeout=15)
    for _ in range(100):
        # IBus.Bus() is a process singleton and can still expose the old connection.
        engines = subprocess.run(["ibus", "list-engine", "--name-only"], env=environment,
                                 capture_output=True, text=True, timeout=3)
        if engines.returncode == 0 and "cyime-rime" in engines.stdout.splitlines():
            if previous:
                current = subprocess.run(["ibus", "engine"], env=environment,
                                         capture_output=True, text=True, timeout=3)
                if current.stdout.strip() != previous.get_name():
                    restored = subprocess.run(["ibus", "engine", previous.get_name()], env=environment,
                                              capture_output=True, text=True, timeout=10)
                    if restored.returncode:
                        time.sleep(0.1)
                        continue
            return
        time.sleep(0.1)
    raise RuntimeError("IBus did not discover CyIME; sign out/in to load the login environment")


def enable_gnome_source():
    from gi.repository import Gio, GLib
    settings = Gio.Settings.new("org.gnome.desktop.input-sources")
    sources = settings.get_value("sources").unpack()
    source = ("ibus", "cyime-rime")
    if source not in sources:
        if not settings.set_value("sources", GLib.Variant("a(ss)", sources + [source])):
            raise RuntimeError("GNOME input sources are not writable")
        Gio.Settings.sync()


def prepare(shared, user, ice_dir=None):
    import yaml
    root = Path(__file__).resolve().parents[1]
    source = root / "app/src/main/assets/rime"
    user = private_dir(user)
    for name in ("pinyin_simp.dict.yaml", "pinyin_simp_ext.dict.yaml", "symbols.yaml"):
        target = user / name
        if not target.exists():
            shutil.copyfile(source / name, target)
    schema = yaml.safe_load((source / "pinyin_simp.schema.yaml").read_text())
    schema["schema"]["schema_id"] = "cyime_pinyin"
    schema["schema"]["name"] = "CyIME 拼音"
    schema["menu"]["page_size"] = 6
    schema["engine"]["translators"] = [t for t in schema["engine"]["translators"] if not t.startswith("lua_")]
    schema["translator"].pop("packs", None)
    if ice_dir:
        ice_dir = Path(ice_dir)
        dictionary = yaml.safe_load((ice_dir / "rime_ice.dict.yaml").read_text().split("...", 1)[0])
        for table in ["rime_ice"] + dictionary["import_tables"]:
            relative = Path(table + ".dict.yaml")
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("Invalid dictionary import")
            target = user / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists():
                shutil.copyfile(ice_dir / relative, target)
        license_path = user / "rime-ice-LICENSE"
        if not license_path.exists():
            shutil.copyfile(ice_dir / "LICENSE", license_path)
        schema["translator"]["dictionary"] = "rime_ice"
        schema["translator"]["enable_completion"] = False
        schema["translator"]["enable_correction"] = True
    target = user / "cyime_pinyin.schema.yaml"
    if not target.exists():
        write_private(target, yaml.safe_dump(schema, allow_unicode=True, sort_keys=False).encode())
    target = user / "default.custom.yaml"
    if not target.exists():
        write_private(target, yaml.safe_dump({"patch": {"schema_list": [{"schema": "cyime_pinyin"}]}}, sort_keys=False).encode())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--shared-dir", type=Path, default=Path("/usr/share/rime-data"))
    parser.add_argument("--user-dir", type=Path, default=default_state_dir() / "rime")
    parser.add_argument("--register", action="store_true")
    parser.add_argument("--enable-gnome", action="store_true", help="Append CyIME to GNOME input sources; preserve existing sources")
    parser.add_argument("--refresh-ibus", action="store_true", help="Reload IBus now (interrupts composition); preserve the selected engine")
    parser.add_argument("--library-dir", type=Path)
    parser.add_argument("--ice-dir", type=Path, help="Reuse CyIME's generated, reweighted rime-ice dictionaries")
    args = parser.parse_args()
    if not (args.shared_dir / "default.yaml").exists():
        raise SystemExit("Install rime-prelude and rime-data-stroke, or supply --shared-dir")
    prepare(args.shared_dir, args.user_dir, args.ice_dir)
    if args.register:
        script = Path(__file__).resolve().with_name("ibus_engine.py")
        command = [sys.executable, str(script), "--ibus", "--shared-dir", str(args.shared_dir.resolve()),
                   "--user-dir", str(args.user_dir.resolve())]
        if args.library_dir:
            library_dir = args.library_dir.resolve()
            command = ["/usr/bin/env", f"LD_LIBRARY_PATH={library_dir}",
                       f"CYIME_RIME_LIBRARY={library_dir / 'librime.so.1'}"] + command
        component = ET.Element("component")
        for name, value in dict(name="org.freedesktop.IBus.CyIME", description="CyIME Linux Rime frontend",
            exec=shlex.join(command), version="0.1", author="CyIME contributors",
            license="GPL-3.0-or-later", homepage="https://github.com/Cyletix/CyIME", textdomain="cyime").items():
            ET.SubElement(component, name).text = value
        engines = ET.SubElement(component, "engines")
        engine = ET.SubElement(engines, "engine")
        for name, value in dict(name="cyime-rime", longname="CyIME 拼音", language="zh_CN",
            license="GPL-3.0-or-later", author="CyIME contributors", icon="input-keyboard", layout="us", rank="80").items():
            ET.SubElement(engine, name).text = value
        target = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "ibus/component/cyime.xml"
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.read_bytes() != ET.tostring(component, encoding="utf-8", xml_declaration=True):
                raise SystemExit(f"Existing component preserved: {target}")
        else:
            write_private(target, ET.tostring(component, encoding="utf-8", xml_declaration=True))
        install_search_path()
        print(f"Registered {target}; sign out/in, then add CyIME 拼音 in your desktop's input-source settings.")
    if args.refresh_ibus:
        refresh_ibus(install_search_path())
    if args.enable_gnome:
        enable_gnome_source()
        print("CyIME 拼音 added. Switch using Super+Space or the desktop input-source menu.")
    print(f"Rime data prepared at {args.user_dir}")


if __name__ == "__main__":
    main()
