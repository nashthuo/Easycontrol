# Easycontrol EN Wave 1 changes

- Added a GitHub Actions build, CJK gate, APK signing and verification, artifact upload, and tag release.
- Set the application ID to `io.github.nashthuo.easycontrol` and the label to Easycontrol EN. Set both build versions to `10012` / `1.0.12-en`.
- Ported matching English strings from upstream [10507](https://github.com/mingzhixian/Easycontrol/releases/tag/10507) and translated the remaining app UI, diagnostics, and server text.
- Kept the base Chinese strings in `values-zh`, forced English in activities and overlay inflation, and added the CJK source check.
- Kept the upstream README credit and added a short fork section.
- Showed phone approval instructions during ADB public-key auth and allowed 60 seconds for the reply on the same connection.
- Added plain-English connection, authorization, and server startup errors.
- Added a per-session display guard that restores normal power on server pipe EOF, including when the main server gets SIGKILL.
- Ported scoped server exit and adapted the backlight restore fix from upstream [10402](https://github.com/mingzhixian/Easycontrol/commit/991295d).
- Ported replacement of a previous session for the same saved device and the startup dimension check from upstream [10313](https://github.com/mingzhixian/Easycontrol/commit/e0b25efd945def57a0ffae2be075a8d831c06ab0).
- Ported default AVC decoder selection for unknown decoder names from upstream [10370](https://github.com/mingzhixian/Easycontrol/commit/2bee94bfe115309bbe7c8e552ef6279988e98d61). Try another compatible AVC decoder if default setup fails.
- Adapted the white-screen startup fix from upstream [10402](https://github.com/mingzhixian/Easycontrol/releases/tag/10402) for the fork's AVC-only stream. Send the codec setup and first sync frame explicitly, then submit packets in receive order and time out if the decoder produces no frame.
- Ported idempotent client close, error-driven reconnect, and bounded startup waits from upstream [10502](https://github.com/mingzhixian/Easycontrol/commit/c33a27f41687aac7cb6f429f5e685335c8033aa6).
- Made ADB stream close wake blocked readers. Bounded connection and stream-open waits, validated packet lengths, capped stream buffers, and installed the versioned server file after a successful upload.
- Kept the server in its ADB shell until session teardown and used a separate socket per attempt.
- Timed out optional Opus setup and kept video available if audio negotiation failed.
- Opened the default device on app launch after overlay permission is available. Added a switch that defaults to on.
- Added Volume up, Volume down, Power, and Wake to the mirror controls. Added a persistent Close and showed controls for three seconds after opening.
- Kept the mirror window visible during retry, with a tap to cancel. Used delays of 2, 4, 8, 16, then 30 seconds for up to ten minutes.
- Saved an optional fallback address per device with a version 1 to version 2 database migration. Tried the primary address before the fallback on initial connection and retry.

## Review fixes (Claude, 2026-11-25)
- CI: call sdkmanager by full path; build the server and the app in two Gradle runs.
- Display guard runs as `ec_display_guard`, so the stock app's `grep easycontrol | kill` cannot stop it; a hung guard is destroyed before restore.
- adb stream open waits 10 s (was 1 s), for Wi-Fi power save and Tailscale relays.
- versionCode adds the CI run number, so each build pushes a fresh server jar to the phone.
- A decoder error after video starts now reconnects instead of ending the session.
- Decoder input buffer sized for large key frames.
- Known behaviour: after a Power or Wake press, "turn off phone screen" stays paused for the rest of that session.
- Startup sends the first frame when SPS, PPS and the IDR arrive in one buffer (Qualcomm OMX); the loop reads the NAL types, not only the flags.

## Wave 2

- Reuse the live ADB transport for the same address and close the mirror streams between attempts.
- Add a live Sync clipboard setting for both directions. It defaults to on.
- Prefer hardware AVC decoders in Auto mode, offer a manual choice, and retry codecs without rejected latency hints.
- Use the display guard to restore the previous size and density, including overrides, when the server ends.
- Save an optional app package per device and mirror it on a virtual display with touch and key input. Fall back to the whole screen if Android cannot start the app display.
- Prevented device-row long-presses from opening a mirror and ignored repeat opens for the same device within one second.
- Added opt-in Tasker broadcasts to open or close mirrors and send keys, with Power blocked.
- Added a Wi-Fi subnet scan for port 5555 in the add and edit device dialog.
- Added file transfer to the phone's Download folder from the device menu and Android share sheet, with a 512 MB limit.
