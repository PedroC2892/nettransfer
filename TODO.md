# TODO

- [ ] macOS/Windows test VMs: Mojave 10.14 is not usable — JavaFX 21 requires macOS 11+ (Metal graphics pipeline). Tiny10 is an unofficial "debloated" build and may give false negatives due to missing Windows components. Replace with a real macOS 11+ (Big Sur/Ventura/Sonoma) and the official Windows 10/11 evaluation image (Microsoft, 90 days, VMware-ready).
- [ ] Single-instance guard: running the app twice on the same host makes the second instance fail to bind UDP port 54321 silently (the receiver thread dies with no warning in the UI). Detect this at startup (try the bind before opening the window) and show a clear error or prevent a second instance, instead of failing silently.
- [ ] jpackage + CI matrix (Windows/macOS/Linux) for native installers — approach already agreed with the user, waiting for confirmation to proceed.
