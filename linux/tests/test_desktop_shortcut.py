import unittest

from cyime_linux.desktop_shortcut import BASE, configure_clipboard_shortcut


class Settings:
    def __init__(self, **values):
        self.values = values

    def get_string(self, key):
        return self.values.get(key, "")

    def get_strv(self, key):
        return self.values.get(key, [])[:]

    def set_string(self, key, value):
        self.values[key] = value
        return True

    set_strv = set_string

    def delay(self): pass
    def apply(self): pass
    def revert(self): pass


class ShortcutTests(unittest.TestCase):
    command = "/usr/bin/python3 /repo/linux/run.py clipboard"

    def test_replaces_copyq_binding_without_changing_other_shortcuts(self):
        paths = [BASE + "other/", BASE + "copyq/"]
        root = Settings(**{"custom-keybindings": paths})
        children = {paths[0]: Settings(binding="<Super>x", command="terminal"),
                    paths[1]: Settings(binding="<Super>v", command="copyq toggle")}
        for _ in range(2):
            self.assertEqual(configure_clipboard_shortcut(self.command, root, children.__getitem__), paths[1])
        self.assertEqual(root.get_strv("custom-keybindings"), paths)
        self.assertEqual(children[paths[0]].get_string("command"), "terminal")
        self.assertEqual(children[paths[1]].get_string("command"), self.command)

    def test_adds_binding_without_removing_existing(self):
        root = Settings(**{"custom-keybindings": [BASE + "other/"]})
        children = {BASE + "other/": Settings(binding="<Super>x", command="terminal")}
        def child(path):
            return children.setdefault(path, Settings())
        configure_clipboard_shortcut(self.command, root, child)
        self.assertEqual(root.get_strv("custom-keybindings"), [BASE + "other/", BASE + "cyime/"])

    def test_preserves_custom_super_v_command(self):
        path = BASE + "custom/"
        root = Settings(**{"custom-keybindings": [path]})
        settings = Settings(binding="<Super>v", command="my-custom-clipboard")
        with self.assertRaises(ValueError):
            configure_clipboard_shortcut(self.command, root, lambda _: settings)
        self.assertEqual(settings.get_string("command"), "my-custom-clipboard")

    def test_rejects_duplicate_super_v_bindings(self):
        root = Settings(**{"custom-keybindings": [BASE + "one/", BASE + "two/"]})
        with self.assertRaises(ValueError):
            configure_clipboard_shortcut(self.command, root,
                                         lambda _: Settings(binding="<Super>v", command="copyq toggle"))
