# Feature tour for the demo video on the SM-X800 (portrait, CyIME docked full width, Obsidian demo note
# in front): every toolbar panel, the menu toggles, settings search and a few colour themes, ending on
# the theme it started with. Plain `input tap` is fast enough at this pace. Coordinates: layouts/sm-x800.json
# and screenshots of 1.14.148. Run it inside scripts/guarded-device-run.py so every setting comes back.
set -e
S=${SERIAL:-R52T800SKWZ}
a() { adb -s "$S" shell "$@"; }
tap() { a input tap "$1" "$2"; sleep "${3:-1.2}"; }
typed() { for k in "$@"; do a input tap $k; done; }
front() { a "dumpsys window | grep mCurrentFocus" | grep -q "$1" || { echo "stopped: $1 not in front"; exit 2; }; }

front md.obsidian
a "dumpsys input_method | grep -q mInputShown=true" || tap 880 600 2
# Toolbar panels, each closed with the back arrow in the toolbar's first slot.
tap 283 1999 2.5; tap 104 1999            # layouts and schemes
tap 623 1999 1.8; tap 140 2081 1.8; tap 104 1999   # emoji, then the kaomoji tab
tap 792 1999 2.2; tap 104 1999            # edit compass
tap 1302 1999 2.0; tap 104 1999           # handwriting pad
tap 1473 1999 2.5; tap 104 1999           # voice
tap 452 1999 2.5; tap 1254 2467           # size and transparency, confirm unchanged
# Menu toggles: glass off and on, full width off and on.
tap 104 1999 1.5
tap 448 2484 2.0; tap 448 2484 1.5
tap 1016 2484 1.2; tap 1016 2484 1.2
# Settings: search for 主题, open 主题与外观, try three palettes, look at each on the keyboard.
tap 448 2274 2.5
front com.cyletix.cyime
tap 880 300 1.5
typed "246 2457" "1009 2296" "1134 2135" "791 2135" "1306 2135"; sleep 0.6; tap 850 2612 2.0
tap 300 480 2.0
for card in "1444 1460" "868 1880" "312 2320"; do
  tap $card 1.5
  a input keyevent 4; sleep 1.2                 # back to the main settings page
  tap 880 784 2.5                               # the test field raises the keyboard in the new colours
  a input keyevent 4; sleep 0.8
  tap 880 1640 1.5                              # 主题与外观 again
done
tap 312 1460 1.5                                # back to iOS 渐变
a input keyevent 4; sleep 1; a input keyevent 4; sleep 1.5
front md.obsidian
echo tour done
