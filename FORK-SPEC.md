# Easycontrol EN — fork spec (nashthuo/Easycontrol, branch `english-fork`)

Grill: 2026-11-25, Nash (lite grill, 2 rounds, stop trigger (c): all plan-changing unknowns resolved).
Rule for decisions (Nash 2026-11-20): every decision reaches Nash as clickable questions, recommendation
first; a blocking one is asked by the session that needs it; small reversible calls go under "Decided for you".

## Goal

A free, always-English, more reliable build of Easycontrol V1.0.11, for one use: the Lenovo Tab M10
(Android 10, en-US) mirrors and controls the Redmi (broken screen and power button) over Wi-Fi adb.

## Base and licence

- Base: upstream tag `10011` (V1.0.11, commit 962954c). This version has no activation code.
- Upstream: mingzhixian/Easycontrol, GPL-3.0. Keep `LICENSE`. Keep the author credit. The source stays
  public in this fork (GPL-3.0 section 6).
- Features ported from later upstream tags (V1.3.x to V1.5.7, also GPL-3.0) are re-implemented in this
  code base. Do not port `ActiveActivity` or any activation, licence-check or payment code.

## Decisions ledger

| # | Question | Answer |
|---|---|---|
| E1 | Which source? | Fork V1.0.11 (free, no activation). |
| E2 | Where to build? | Public GitHub fork, GitHub Actions (free minutes for a public repo). |
| E3 | Who writes code? | Codex first. Claude reviews every diff, fixes defects, tests on the tablet. |
| E4 | Ship plan | Two waves. Wave 1 is small and tested on the tablet first. |
| E5 | Language | Always English, whatever the system locale is. |
| E6 | Wave 1 scope | English, first-connect message, restore phone screen, stability + white-screen fixes, auto-open default device, key buttons, auto-reconnect, Tailscale fallback address. |
| E7 | Wave 2 scope | ADB reuse, Tasker broadcast commands, remember window size per orientation, clipboard sync switch, send file, decoder auto-pick, single-app mirroring, resolution on connect + restore, LAN device scan. |

## Decided for you (small, reversible)

- New `applicationId` `io.github.nashthuo.easycontrol` (the Java `namespace` stays). The fork installs
  next to the stock app, so the stock app stays as a fallback until Wave 1 passes. Undo: set it back.
- App label "Easycontrol EN". Undo: change `app_name`.
- Own signing key (PKCS12), kept outside every repo; CI reads it from Actions secrets
  `EASYCONTROL_KEYSTORE_B64`, `EASYCONTROL_KEYSTORE_PASSWORD`, `EASYCONTROL_KEY_ALIAS` (names only).

## Wave 1 — tasks and acceptance

1. **Build in CI.** `.github/workflows/build.yml`: on push to `english-fork`, on tags `en-v*`, and on
   `workflow_dispatch`. JDK 17, Android SDK 33. Run `./gradlew :server:copyRelease :app:assembleRelease`
   in `easycontrol/`. Sign with the three secrets. Upload the APK as an artifact. On an `en-v*` tag, attach
   it to a GitHub release. Pin every action to a full version tag.
   - Accept: a push builds a signed release APK that installs on Android 10.
2. **Always English.**
   - Make `res/values/strings.xml` English. Move the Chinese file to `res/values-zh/strings.xml`.
     Reuse the English text from upstream tag `10507` `res/values-en/strings.xml` where the key and meaning
     match. Translate the rest.
   - Remove `resConfigs "zh"`. Keep only `en` (and `zh` if size allows).
   - Force the English locale in every Activity and in the floating window context
     (`attachBaseContext` / `createConfigurationContext`), so a Chinese system locale still shows English.
   - Move every hard-coded Chinese string in Java (Toast, dialog, notification, exception text shown to
     the user) to string resources, in `app` and in `server`.
   - Add `tools/check-no-cjk.py`: it fails when a CJK character is in any file under `easycontrol/` except
     `res/values-zh/`. CI runs it before the build.
   - Accept: the CJK check passes; every screen, menu, dialog and toast on the tablet is English.
3. **First-connect message.** When the adb auth is not yet approved on the phone, show "Tap Allow on the
   phone to approve this tablet" and wait up to 60 s for the approval, instead of closing after ~5 s with no
   reason. On a real failure, show the cause (refused, timeout, auth) in plain English.
   - Accept: with a fresh key, the message shows, and the link opens after Allow without a second tap.
4. **Restore phone screen.** The server turns the phone display off while it mirrors. On every exit path
   (window close, link drop, server crash, app kill) the phone display power mode comes back to normal.
   - Accept: after each exit path, `dumpsys power` on the phone shows the display can turn on again, and a
     wake key lights the screen.
5. **Stability + white-screen fixes.** Port the fixes from upstream V1.3.13 to V1.5.2 release notes that
   apply to this code: unstable links, white screen at start, decoder errors on unknown decoders, crashes on
   close. List each ported fix with its upstream tag in `FORK-CHANGES.md`.
   - Accept: 20 open/close cycles with no white screen and no crash (Claude runs them by adb).
6. **Auto-open default device.** When the app starts, it opens the device marked default (the existing
   "Set default" menu item) without a tap. A setting turns this off.
   - Accept: start the app by adb; the Redmi window opens.
7. **Key buttons.** Add to the mirror window bar: Volume up, Volume down, Power, Wake. They send the key
   events through the existing control channel.
   - Accept: each button acts on the Redmi (checked by `dumpsys audio` volume and display state).
   - Nash 2026-11-25: the Close (X) is hidden until a tap on the small blue top line, so he could not find
     it. Show a small Close button on the window at all times, and show the button bar for 3 s when the
     window opens, so the controls are easy to find.
8. **Auto-reconnect.** When the link drops, retry with backoff (2, 4, 8, 16, 30 s, then every 30 s) for
   up to 10 minutes, and show "Reconnecting…" in the window. A tap on the window cancels.
   - Accept: turn the phone Wi-Fi off for 20 s and on; the window comes back without a tap.
9. **Fallback address.** Each device gets an optional second address (for example the Tailscale IP).
   The app tries the first address, then the second.
   - Accept: with the first address wrong, the link opens on the second.

## Wave 1 — results on the Lenovo Tab M10 + Redmi Note 8 (2026-11-25, build 881ce10)

| Check | Result |
|---|---|
| All UI text English (`tools/check-no-cjk.py`) | PASS |
| First connect shows "Tap Allow on the phone to approve this tablet" | PASS |
| First frame | PASS, under 2 s (after the Qualcomm SPS+PPS+IDR fix) |
| Close always visible; Close leaves 0 server processes; phone screen ON | PASS |
| Key buttons (Home, Back, Recents, Volume, Power, Wake) | PASS (Home moved the phone from Deezer to the MIUI launcher) |
| Auto-open the default device on app start | PASS |
| Auto-reconnect (tablet Wi-Fi off 20 s) | PASS ("Reconnecting…", then a live frame) |
| Tailscale fallback (dead first address) | PASS, link in about 6 s; phone adb peer = tablet Tailscale IP |
| 20 open/close cycles | PASS 20/20, 0 leftover processes, screen ON each time |
| Kill path (force-stop the app mid-session) | PASS, server ends within 2 s, screen ON |

Known bug (moves to Wave 2): a long-press on the device row sometimes opens the menu AND starts a mirror
behind it. Fix: in `DeviceListAdapter`, ignore the click that follows a handled long-press.

## Wave 2 — tasks (spec detail added when Wave 1 lands)

ADB reuse (one adb link kept for a reconnect) · Tasker broadcast commands (open, close, key, by an
explicit intent with a permission) · remember window size and position per orientation · clipboard sync
with an on/off switch · send a file to the phone Download folder · decoder auto-pick for lowest lag ·
single-app mirroring · resolution on connect, restored after · LAN scan for wireless adb devices ·
fix the long-press stray mirror (above).

## Operating rule

Never run the stock app and Easycontrol EN against the Redmi at the same time. The stock app kills every
process named "easycontrol" on the phone; the fork's display guard runs as `ec_display_guard` to survive it.

## Out of scope

Activation or payment code. Play Store release. iOS. Any change to the Redmi other than what the server
does while it mirrors. A software reboot of the Redmi (its power button is broken: never reboot it).

## Test rig

Tablet USB serial HGAKW018 (Wi-Fi 192.168.100.32:5555). Redmi wireless adb 192.168.100.31:5555. Claude
installs the APK by adb, drives the tablet with `input` events, and reads both devices' logcat.
