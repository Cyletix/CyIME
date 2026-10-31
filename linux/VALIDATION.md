# Linux Preview Validation

## Scope

This directory is an opt-in Linux desktop preview. It adds no Android changes,
release/signing configuration, bundled binaries, private state, or theme images.
Default input sources and themes remain unchanged until explicitly configured.
Source registration and desktop launchers are user-local.

## Environment And Checks

Test environment: Ubuntu 22.04, GNOME X11, Python 3.10, PyQt6, GTK 3, IBus,
and host librime 1.7.3 with Rime shared data. Tests use throwaway history/trust stores,
certificates and Rime user directories. Native UI/paste tests use isolated Xvfb/IBus
sessions, not the user's clipboard or active input source.

2026-10-31: all 57 tests passed from an exported Linux-only contribution snapshot
based on upstream `5eba993`, with the pinned Rime asset submodule `a9d394b`.
The snapshot contained no local Android patches or private theme files.

Coverage includes:

- V2 QR/nearby pairing, TLS/authentication/revocation, direction switches,
  limits, text/ETag round trips, offline cache and single-backend locking.
- Preview-first images, rejecting unsolicited/wrong-owner originals, integrity
  checks, cache reuse, legacy SQLite migration and backup merging.
- Source/type/search filters, pins, explicit editing, frameless controls,
  single-manager forwarding and full-size image paste into an external X11 editor.
- Opt-in GNOME registration, preservation of existing sources/launchers/environment,
  virtual-environment interpreter paths and settings persistence.
- Native/custom IBus composition, separate Pinyin reading/inline preview,
  Backspace under Num Lock, Shift/raw-English, Tab paging and selection.
- Synthetic clipboard/settings/candidate rendering at normal and compact sizes.
  No real clipboard contents or private backgrounds are included.

Reproduce commands in [README.md](README.md#tests). Set `CYIME_RIME_SHARED` to
enable native Rime tests; otherwise those checks are skipped. Full integration
requires `xvfb-run`, GI typelibs and an initialized asset submodule. No Android
build is needed for this Linux-only contribution.

## Remaining Gaps

- Wayland clipboard observation and automatic paste are not implemented. Other
  desktops and distribution/librime versions have not been validated.
- Upstream Android's v2 QR/text wire format is reused; automatic Linux discovery
  and image sync need separate Android changes. Protocol tests are not stock-phone
  end-to-end acceptance.
- Android custom decoder/correction modules, AI translation, voice input and
  soft-keyboard UI are not ported. Normal host Rime learning remains available.
- Image caches are bounded. Cached originals work offline; preview-only entries
  need their source online. Backups deliberately omit trust keys.
- Maintainer discussion is required before merge readiness. This is not a packaged
  distribution or official release.
