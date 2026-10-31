"""Restore the invoking X11 window before issuing a normal clipboard paste."""

import os
import time

from PyQt6.QtCore import QObject, QTimer


def active_target():
    if os.environ.get("QT_QPA_PLATFORM") == "offscreen" or os.environ.get("XDG_SESSION_TYPE") == "wayland":
        return None
    try:
        from Xlib import X, display
        connection = display.Display()
        try:
            prop = connection.screen().root.get_full_property(
                connection.intern_atom("_NET_ACTIVE_WINDOW"), X.AnyPropertyType)
            if prop is None or not len(prop.value) or not prop.value[0]:
                return None
            window = connection.create_resource_object("window", int(prop.value[0]))
            pid = window.get_full_property(connection.intern_atom("_NET_WM_PID"), X.AnyPropertyType)
            return dict(window=window.id, pid=int(pid.value[0]) if pid is not None else 0)
        finally:
            connection.close()
    except Exception:
        return None


class PasteTarget(QObject):
    def __init__(self, parent):
        super().__init__(parent)
        self.target = None
        self.connection = None
        self.timer = QTimer(self)
        self.timer.setInterval(25)
        self.timer.timeout.connect(self.poll)

    def remember(self, target):
        if (isinstance(target, dict) and type(target.get("window")) is int
                and type(target.get("pid")) is int and target["window"] > 0
                and target["pid"] != os.getpid()):
            self.target = target.copy()

    def paste(self, window, failure):
        if self.timer.isActive():
            return
        self.failure = failure
        try:
            if not self.target:
                raise ValueError("已复制；请从目标输入框按 Super+V 打开剪贴簿后再双击粘贴")
            from Xlib import X, display, protocol
            self.connection = display.Display()
            self.root = self.connection.screen().root
            self.destination = self.connection.create_resource_object("window", self.target["window"])
            attributes = self.destination.get_attributes()
            pid = self.destination.get_full_property(self.connection.intern_atom("_NET_WM_PID"), X.AnyPropertyType)
            if attributes.map_state != X.IsViewable or (self.target["pid"] and
                    (pid is None or int(pid.value[0]) != self.target["pid"])):
                raise ValueError("原窗口已关闭或不可见；内容已复制，未自动粘贴")
            current = self.root.get_full_property(self.connection.intern_atom("_NET_ACTIVE_WINDOW"), X.AnyPropertyType)
            if current is None or int(current.value[0]) not in (int(window.winId()), self.destination.id):
                raise ValueError("焦点已切换；内容已复制，未自动粘贴")
            self.deadline = time.monotonic() + 1.5
            window.hide()
            event = protocol.event.ClientMessage(window=self.destination.id,
                client_type=self.connection.intern_atom("_NET_ACTIVE_WINDOW"),
                data=(32, [2, X.CurrentTime, 0, 0, 0]))
            self.root.send_event(event, event_mask=X.SubstructureRedirectMask | X.SubstructureNotifyMask)
            self.connection.flush()
            self.timer.start()
        except Exception as error:
            self.finish(str(error))

    def poll(self):
        try:
            from Xlib import X, XK
            from Xlib.ext import xtest
            if time.monotonic() >= self.deadline:
                raise ValueError("无法恢复原输入框焦点；内容已复制，未自动粘贴")
            current = self.root.get_full_property(self.connection.intern_atom("_NET_ACTIVE_WINDOW"), X.AnyPropertyType)
            if current is None or int(current.value[0]) != self.destination.id:
                return
            # Do not combine a still-held Super/Alt/Shift with the generated Ctrl+V.
            modifiers = self.root.query_pointer().mask
            if modifiers & (X.ShiftMask | X.ControlMask | X.Mod1Mask | X.Mod4Mask):
                return
            control = self.connection.keysym_to_keycode(XK.string_to_keysym("Control_L"))
            key = self.connection.keysym_to_keycode(XK.string_to_keysym("v"))
            if not control or not key:
                raise ValueError("当前键盘布局不支持自动粘贴；内容已复制")
            self.connection.grab_server()
            try:
                # Recheck under the X server grab: never inject into a different app.
                current = self.root.get_full_property(self.connection.intern_atom("_NET_ACTIVE_WINDOW"), X.AnyPropertyType)
                if current is None or int(current.value[0]) != self.destination.id:
                    raise ValueError("焦点已切换；未自动粘贴")
                xtest.fake_input(self.connection, X.KeyPress, control)
                xtest.fake_input(self.connection, X.KeyPress, key)
                xtest.fake_input(self.connection, X.KeyRelease, key)
                xtest.fake_input(self.connection, X.KeyRelease, control)
            finally:
                self.connection.ungrab_server()
                self.connection.sync()
            self.finish()
        except Exception as error:
            self.finish(str(error))

    def finish(self, error=None):
        self.timer.stop()
        if self.connection is not None:
            self.connection.close()
            self.connection = None
        if error:
            self.failure(error)
