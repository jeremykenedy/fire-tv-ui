# Home transition correction

The Home accessibility service previously treated every Fire TV UI activity as a possible Amazon Settings trampoline. A Home press from the screensaver controls could enter a delayed redirect that another window transition cancelled. The correction reserves that grace period for actual Amazon Settings packages. Fire TV UI opens native system settings through its reviewed explicit settings entry.

A host regression fails against the prior service and passes against the correction: Home from the UI's own controls returns directly, app switching returns normally, and an actual Amazon Settings handoff keeps its grace period.

On October 22, 2026, the focused signed code37 candidate `f1d9205766057718fd3d3f9b7464786c61d192a019a08eede568987cfcfa78f8` was installed on the new Fire TV. Each settings-origin test verified that ScreensaverActivity was actually foreground before pressing Home. Warm returns reached Main in 2.29 and 0.89 seconds. Home from the bound selected Aquarium Dream and light standby reached Main in 0.85 seconds; Home from the native Live TV guide reached Main in 0.96 seconds. A first cold transition exceeded the 2.33-second observation window, so this evidence does not establish an instantaneous return in every case.

The prior build had remained on Amazon Home at approximately nine seconds in one timed observation before returning to Main by twelve seconds. Rebinding alone did not eliminate that delayed settings-origin behavior. Both Home services were actually bound, with no crashed services, when it was reproduced.

The focused physical test preserved every portable launcher preference and all eight recorded OS keys, retained native remote recognition, left OwnTV unchanged, and restored the initial selected Aquarium Dream and Dreaming power state. The device-local remote identity is deliberately excluded from portable exports by the separate migration fix. No physical permission grants were made.

An earlier emulator check reused a task with Main above the intended settings activity. Its initial Home assertions are excluded from the settings-origin evidence. Native checks now verify the foreground precondition and use a clear-top intent when a shell-launched task needs it.
