# CyIME Linux (Preview)

Linux companion and IBus/Rime frontend. No CopyQ process or database is used.
Experimental GPL-3.0-or-later desktop addition; Android behavior is unchanged.

## Setup

Use Python 3.10+ with system PyGObject/GTK/IBus bindings. On Debian/Ubuntu:

```bash
sudo apt install python3-venv python3-gi gir1.2-ibus-1.0 gir1.2-gtk-3.0 \
  ibus librime1 rime-prelude rime-essay rime-data-stroke
git submodule update --init app/src/main/assets/rime
cd linux
python3 -m venv --system-site-packages .venv
. .venv/bin/activate
python3 -m pip install -r requirements.txt
```

Run the commands below from `linux/` with that environment activated. No Android
build, native-source submodules, APK, personal theme or downloaded runtime is needed.

## Desktop Clipboard

Tested on Ubuntu 22.04 / GNOME / X11 / Python 3.10. Wayland background clipboard
capture is **not supported yet**; Qt alone cannot reliably observe other apps there.
The backend requires an active desktop session, but no open manager window.

```bash
python3 run.py gui
```

The GUI starts a detached backend if needed. Closing the GUI leaves it running;
Settings → Stop Backend or `python3 run.py stop` stops it explicitly.
The history filter separates all/computer/phone content, combined with
all/text/link/address/phone/email/image categories. Search, pin, copy, text editing,
image import, delete, paired-device
revocation, direction switches, clipboard export and merging restore are available.
Local history supports non-empty plain text up to 64 KiB and PNG/JPEG/WebP/GIF
images up to 32 MiB / 16 megapixels. The Linux server implements text synchronization
using Android's existing v2 pairing protocol and an optional image protocol.
The Android image client is not included in this Linux-only contribution. With an
image-capable client, images initially transfer **only a JPEG preview**
(at most 256 × 256 pixels / 48 KiB) and metadata. Selecting an entry does not
download its original. Paste, double-click paste, or explicit copy requests the
original over authenticated, certificate-pinned TLS; SHA-256 and size are checked
before use. The receiving app gets image MIME data, never a substituted thumbnail.
The app must accept images.

Phone → Linux original upload is authorized only while a local copy/paste request
is pending. The phone must be online with its sync bridge running (open the keyboard
if Android has suspended it). Linux → phone originals are downloaded on image paste.
Downloaded originals are reused offline; preview-only entries remain visible offline
but cannot be pasted until their source is available. Original caches are bounded
at 512 MiB per side; Linux previews at 16 MiB. A full cache rejects new data rather
than deleting already-loaded previews. Image sync requires a client implementing
the `images_v=1` endpoints described below; stock Android image synchronization
is not provided by this directory.
History persists without a network connection; offline devices are not pruned.
The current clipboard is synchronized; this is not a full remote-history download API.

State: `$XDG_DATA_HOME/cyime-linux` (default `~/.local/share/cyime-linux`), mode 0700.
History and keys are private to the local user, **not encrypted at rest**. Clipboard
history may contain passwords. Backups contain clipboard text, image previews and
already-cached originals, not pairing credentials;
restore merges entries without resetting current trust/settings. Keep backups private.
Restored preview-only entries cannot restore their source's trust; an uncached
original becomes available only when a paired source shares that image again.

For foreground CLI use, `python3 run.py daemon`. While running, `python3 run.py qr`
prints a terminal QR, and `python3 run.py status` displays non-secret status.
Default LAN port: TCP 8788. mDNS uses UDP 5353. No automatic firewall modifications.

For text pairing in CyIME Android: Clipboard Sync → Scan Computer QR, then enable sync. The QR is
single-use, valid for five minutes, and replaced by each new QR. Do not share it.
TLS 1.2+ encrypts transport; the phone pins the QR certificate fingerprint. Each phone
receives a separate revocable bearer token. LAN mDNS updates addresses, never trust.
Nearby click-to-pair: select the computer on a compatible client, then open the Linux manager's
Devices tab and enter the phone's 8-digit code for that request. Allow it locally,
then confirm on Android. Requests expire after two minutes; five incorrect codes
reject a request. No token is issued without local approval. QR pairing remains
available and uses a compact, single-use code with a pinned TLS certificate.
The server advertises mDNS `kind=linux`. Current upstream Android discovery filters
for `windows`/`android`, so it does not list Linux automatically; use QR for text.
Android Linux discovery and image-client support require separate Android changes.

Optional user launchers/autostart (does not change your default input method):

```bash
python3 install_desktop.py --install
systemctl --user daemon-reload
systemctl --user enable --now cyime-linux
```

On GNOME, explicitly register **Super+V** with:

```bash
python3 install_desktop.py --clipboard-shortcut
```

This replaces the legacy `copyq toggle` binding, preserves unrelated shortcuts and
refuses to overwrite other custom Super+V commands. CopyQ and its history are not
removed or imported. `python3 run.py clipboard` opens the CyIME clipboard tab with
all/computer/phone filtering; repeating it while that tab is active hides the window.
Esc also hides it. GUI launchers and the shortcut reuse one manager per state
directory, including after hiding. Closing the manager still leaves the backend
running. No extra permissions or global key grab are added to the backend.

Select an entry and click the pencil icon to edit; Save updates that history entry
without changing its origin, pin or timestamp. Cancel leaves it untouched. Saving
alone does not change the system clipboard or send text to the phone. Copying or
pasting the saved entry makes it the current synchronized text. Empty/oversized
text, stale edits and same-origin duplicate text are rejected without overwriting.
On X11, double-click an entry or click Paste to return to the window that invoked
the manager and issue Ctrl+V. The original input field must accept normal Ctrl+V;
if the destination disappears or focus cannot be restored, text is only copied
and the manager reports the problem. Terminal-specific paste shortcuts and Wayland
automatic paste are not implemented. `python-xlib` provides the X11 integration.

The systemd user manager must inherit the graphical-session environment (DISPLAY,
XAUTHORITY when required, DBUS_SESSION_BUS_ADDRESS). GUI launch remains available
without systemd. Choose GUI-managed **or** systemd-managed startup; a process lock
prevents two backends sharing the same database.

## Linux Pinyin Frontend

Linux uses a native IBus frontend with librime's versioned public C API. All
segmentation, dictionary ranking, composition and user learning belong to Rime.
The portable schema comes from CyIME's `pinyin_simp` assets, with the
Android-only UUID Lua helper removed. The frontend shows six candidates per page by
default, adjustable from 3 to 9. It has its own
Rime user directory and does not overwrite your existing IBus/libpinyin data.

After installing the dependencies and initializing the asset submodule above:

```bash
python3 setup_ibus.py --register
```

Registration also installs a user-local `environment.d` search path: Ubuntu 22.04's
IBus does not discover `~/.local/share/ibus/component` by default. Sign out/in, then
add **CyIME 拼音** under Settings → Keyboard → Input Sources → Chinese.
On GNOME, `--enable-gnome` appends it without removing or selecting existing sources.
Use `--refresh-ibus` to apply registration immediately instead of logging out; this
restarts IBus and interrupts an in-progress composition, but restores the selected engine.

```bash
python3 setup_ibus.py --register --enable-gnome --refresh-ibus
```

Switch using **Super+Space** or the top-right input-source menu; select **CyIME 拼音**.
For a one-off switch from a terminal after registration: `ibus engine cyime-rime`.
Switch back by selecting your previous input source in the same desktop menu.
Registration does not select it automatically. The default candidate theme is provided
by IBus/GNOME; it is not a port of Android's on-screen keyboard. CyIME's customized Android native
correction/ranking modules are **not yet ported** to this frontend.
Open **CyIME Clipboard** from the application menu (after installing launchers),
then its **输入法** tab, or use `python3 run.py settings`. Settings include page size,
first-candidate inline preview, paging keys, punctuation, full-width characters,
candidate font size, theme, background image, foreground/accent colors and overlay.
Rime retains its normal user learning; a learning-disable switch is not offered.
Settings take effect when focus next enters the input method.

The application cursor shows an uncommitted first-candidate preview; raw Pinyin is
shown above the candidates. Backspace edits pending Pinyin, not already committed text.
Page Up/Page Down and optional `-`/`=` page candidates; number keys select within a page.
Custom X11 windows also support mouse paging buttons and the scroll wheel.
Tap either Shift key to switch between Chinese and English. Switching to English
commits unfinished Pinyin as raw letters without generated syllable spaces. Shift
chords remain normal shortcuts. Backspace edits active Pinyin even with Num Lock on;
idle Backspace still reaches the application. Tab/Shift+Tab page forward/backward
when enabled in settings; idle Tab is not intercepted.

One **CyIME 剪贴簿** application/tray entry opens the unified manager; the old
settings desktop entry is hidden for compatibility. Settings commands reuse the
same manager window. Its Qt title bar replaces system decorations and supports
native dragging/resizing, double-click maximize, minimize, restore and close.
The persistent backend owns the tray with clipboard and stop-backend entries.
Closing the GUI does not stop the backend. GNOME requires an AppIndicator extension for tray
icons (Ubuntu's enabled AppIndicator extension is supported).

The repository default remains the system theme without any background. Light, dark
and custom candidate themes use an optional GTK popup on X11; Wayland falls back to
the native IBus surface. Custom images are imported into private user settings;
no background is bundled or selected by default. Fonts/colors/background apply to
the GTK popup, not GNOME Shell.
If custom window creation fails, the frontend keeps native candidates and Pinyin visible.
To reuse the same large, reweighted dictionaries prepared by the Android build,
pass `--ice-dir ../app/build/generated/chinese-assets/rime_ice` on **first setup**.
Existing schema files are preserved; prepare a new `--user-dir` to change presets.
This shares dictionaries/weights, not Android's custom decoder or personal user model.

Developers can prepare data without registration:
`python3 setup_ibus.py --shared-dir /path/to/rime-data --user-dir /tmp/cyime-rime`.
`CYIME_RIME_LIBRARY` selects a custom native library. A private library prefix may
also be registered using `--library-dir /path/to/usr/lib/x86_64-linux-gnu`.

## Tests

```bash
python3 -m unittest discover -s tests -v
CYIME_RIME_SHARED=/usr/share/rime-data python3 -m unittest discover -s tests -p test_rime.py -v
```

Protocol tests launch a temporary offscreen Qt backend and make actual pinned TLS
requests. They do not replace the user's clipboard or default input method. Native
Rime tests are skipped unless the shared data path is explicitly configured.
`tests/ibus_smoke.py` additionally exercises actual IBus candidate/commit signals,
but must run under `dbus-run-session` with a fresh private `IBUS_ADDRESS`,
`XDG_CONFIG_HOME`, `CYIME_ISOLATED_IBUS=1`, and explicit `CYIME_RIME_SHARED` /
`CYIME_RIME_USER`. It refuses to run against the normal session by default.
Custom-theme integration additionally uses `xvfb-run` and `python3-xlib` to verify the
window created by the actual engine, including visibility and painted content.

Install `xvfb` and `dbus-x11` for the isolated integration checks. To include native
Rime checks in one run:

```bash
QT_QPA_PLATFORM=offscreen CYIME_RIME_SHARED=/usr/share/rime-data \
  python3 -m unittest discover -s tests -v
```

See [validation notes](VALIDATION.md) for tested scope and remaining gaps.

## Optional Image API

All image endpoints require the same paired bearer token and pinned TLS as text:

- `GET /api/status`: `images_v=1` advertises server image support.
- `PUT /api/images/current`: publish metadata and preview, never the original.
- `GET /api/images/current`: retrieve the latest descriptor (null for its owner).
- `GET /api/images/needs`: originals explicitly requested by a local copy/paste,
  restricted to the owning device with a 45-second request lifetime.
- `PUT /api/images/{sha256}/original`: upload only for a pending owned request.
- `GET /api/images/{sha256}/original`: fetch cached original bytes on explicit paste.

Descriptors contain `hash`, `size`, `width`, `height`, `mime`, Base64 JPEG `preview`
and `preview_hash`; hashes are lowercase SHA-256 hex. Bodies are bounded, direction
switches apply, and original hash/size/dimensions are verified. Integration tests
exercise both original-transfer directions over TLS. This is a current-item API,
not a full remote-history download or an Android image client.

## Boundaries

No existing CopyQ, Android, IBus/libpinyin or Rime user data is migrated or removed.
Linux uses the installed public librime engine; Android-specific correction modules,
soft-keyboard layouts, AI translation and voice input are not ported. Nothing here
changes Android packaging, signing, settings or version numbers.
