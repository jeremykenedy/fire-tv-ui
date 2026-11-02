# Network and diagnostics source lane

The source classes live in `device.network`. Register NetworkActivity and DiagnosticsActivity as non-exported activities and declare ACCESS_NETWORK_STATE. No Wi-Fi location, storage, privileged settings, DUMP or Internet permission is needed by this lane. DeviceRoutes owns the system-only NETWORK_SETTINGS handoff and its query visibility.

Use Settings button labels `Network and IP` and `Diagnostics` for the current native route test. The activities use the existing Pages views and Android scroll containers, with explicit initial focus and asynchronous collection. Refresh preserves the selected control. Returning from native network settings triggers a fresh snapshot.

Network values come only from ConnectivityManager capabilities and LinkProperties. VPN and physical connection values are separate sections; no fixed-IP mode or DHCP reservation is guessed. Empty, missing-service, permission and runtime failure messages remain explicit. The module never changes connectivity or sends network probes.

Diagnostics collects only public device manufacturer/model/API, its own app version/signing certificate SHA-256, aggregate storage/memory and its own Java heap. It does not read preferences, accounts, network addresses, serials, system logs, dumps or other app activity. Export requires a user-selected Android document destination; canceled, unsupported and failed exports have visible statuses. No automatic export is performed.

Checks completed: `python3 native/tests/network/run.py` compiles the lane against Android 36 in an isolated temporary directory and passes bounded text/known SHA-256 checks. `python3 native/tests/network/build_native_test.py` compiles the native suite and creates the separate network-proof APK. Neither command modifies the combined app output. Native suite execution is pending the coordinator's combined app and emulator lease.

Native operator steps after installing the combined preview:

```
adb -s emulator-5582 install -r native/tests/network/.build/network-proof.apk
adb -s emulator-5582 shell am instrument -w com.jeremykenedy.firetv.ui.networkproof/com.jeremykenedy.firetv.ui.nativeapp.device.network.NetworkInstrumentation
```

The suite opens the real Settings routes, checks initial focus, refresh, Back focus restoration, actual report fields and canceled/unsupported export without saving a document. It does not modify preferences, disconnect a network or install a producer fixture. The suite does not prove offline/VPN device fixtures, a successful document-provider write, physical Fire OS behavior or the network-settings handoff. Keep those validation limits explicit. No production deployment, commit or release was performed by this lane.
