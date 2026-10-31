"""Optional X11 candidate surface. Native IBus/GNOME presentation remains the default."""

import gi
gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
from gi.repository import Gdk, GdkPixbuf, Gtk, Pango


class CandidateWindow(Gtk.Window):
    def __init__(self, select, page):
        super().__init__(type=Gtk.WindowType.POPUP)
        self.set_title("CyIME candidates")
        self.set_accept_focus(False)
        self.set_focus_on_map(False)
        self.set_type_hint(Gdk.WindowTypeHint.COMBO)
        self.set_skip_taskbar_hint(True)
        self.set_resizable(False)
        self.set_name("cyime-candidates")
        self.select = select
        self.page = page
        self.anchor = (0, 0, 0, 0)
        self.background = None
        self.image_path = None
        self.settings = None
        self.provider = Gtk.CssProvider()
        self.get_style_context().add_provider(self.provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        self.box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.box.set_border_width(10)
        self.reading = Gtk.Label(xalign=0)
        self.reading.set_name("cyime-reading")
        self.reading.set_ellipsize(Pango.EllipsizeMode.END)
        self.reading.set_max_width_chars(60)
        self.reading.set_selectable(False)
        self.box.pack_start(self.reading, False, False, 0)
        self.row = Gtk.Box(spacing=4)
        self.box.pack_start(self.row, False, False, 0)
        self.add(self.box)
        self.box.connect("draw", self.draw_background)
        self.connect("scroll-event", self.scroll)

    def apply_theme(self, settings):
        self.settings = settings
        light = settings.theme == "light"
        bg, fg, selected = ("#f6f7f9", "#20252b", "#cce9ed") if light else ("#161a20", settings.foreground, "#314854")
        css = f"""
        #cyime-candidates {{ background-color: {bg}; color: {fg}; border: 1px solid #68737c; border-radius: 6px; }}
        #cyime-candidates label {{ color: {fg}; font-size: {settings.font_size}px; }}
        #cyime-reading {{ background-color: rgba(127, 127, 127, 0.16); border-radius: 4px; padding: 5px 8px; }}
        #cyime-candidates button {{ background: transparent; color: {fg}; border: none; box-shadow: none; padding: 6px 8px; }}
        #cyime-candidates button:hover, #cyime-candidates button.selected {{ background: {selected}; border-radius: 4px; }}
        #cyime-candidates button.selected label {{ color: {"#153f48" if light else settings.accent}; }}
        """
        self.provider.load_from_data(css.encode())
        for widget in (self.box, self.reading, self.row):
            widget.get_style_context().add_provider(self.provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        if settings.background != self.image_path:
            self.image_path = settings.background
            self.background = None
            if settings.background:
                try:
                    self.background = GdkPixbuf.Pixbuf.new_from_file_at_scale(settings.background, 1600, 800, True)
                except Exception:
                    pass

    def draw_background(self, widget, cr):
        if self.settings and self.settings.theme == "custom" and self.background:
            width, height = self.get_allocated_width(), self.get_allocated_height()
            image = self.background
            scale = max(width / image.get_width(), height / image.get_height())
            cr.save()
            cr.translate((width - image.get_width() * scale) / 2, (height - image.get_height() * scale) / 2)
            cr.scale(scale, scale)
            Gdk.cairo_set_source_pixbuf(cr, image, 0, 0)
            cr.paint()
            cr.restore()
            cr.set_source_rgba(0, 0, 0, self.settings.overlay / 100)
            cr.paint()
        return False

    def scroll(self, widget, event):
        if event.direction in (Gdk.ScrollDirection.UP, Gdk.ScrollDirection.DOWN):
            self.page(-1 if event.direction == Gdk.ScrollDirection.UP else 1)
        return True

    def button(self, label, callback, tooltip):
        button = Gtk.Button(label=label)
        button.set_can_focus(False)
        button.set_tooltip_text(tooltip)
        button.connect("clicked", callback)
        for widget in (button, button.get_child()):
            widget.get_style_context().add_provider(self.provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        self.row.pack_start(button, False, False, 0)
        return button

    def update_candidates(self, context, settings):
        self.apply_theme(settings)
        self.reading.set_text(context["preedit"])
        for child in self.row.get_children():
            self.row.remove(child)
            child.destroy()
        monitor = self.get_display().get_monitor_at_point(*self.anchor[:2])
        available = monitor.get_workarea().width - 160
        label_chars = max(3, available // max(1, len(context["candidates"])) // settings.font_size - 3)
        for index, (text, comment) in enumerate(context["candidates"]):
            button = self.button(f"{index + 1} {text}", lambda _, i=index: self.select(i), comment or text)
            button.get_child().set_max_width_chars(label_chars)
            button.get_child().set_ellipsize(Pango.EllipsizeMode.END)
            if index == context["selected"]:
                button.get_style_context().add_class("selected")
        self.button("‹", lambda _: self.page(-1), "上一页 / Page Up").set_sensitive(context["page"] > 0)
        self.button("›", lambda _: self.page(1), "下一页 / Page Down").set_sensitive(not context["last"])
        if not context["preedit"]:
            self.hide()
            return
        self.show_all()
        self.resize(1, 1)
        self.position()

    def position(self):
        x, y, w, h = self.anchor
        display = self.get_display()
        monitor = display.get_monitor_at_point(x, y)
        area = monitor.get_workarea()
        width, height = self.get_preferred_size()[1].width, self.get_preferred_size()[1].height
        x = max(area.x, min(x, area.x + area.width - width))
        y = y + h + 4 if y + h + height + 4 <= area.y + area.height else y - height - 4
        self.move(x, max(area.y, y))
