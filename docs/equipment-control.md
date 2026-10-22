# Equipment Control recognition

Some Fire OS devices can report that the connected remote does not support Equipment Control after native recognition was previously established. Fire TV UI records the descriptor only while Fire OS reports support and exactly one eligible Amazon Fire TV Remote is connected. If the flag later clears with that same remote still connected, the local guard restores recognition.

An eligible remote must be external, nonvirtual, named Amazon Fire TV Remote and have nonzero vendor and product identifiers. Disconnected, unknown, ambiguous or previously unrecognized remotes do not enable support. Copying preferences from another TV does not establish support for its different remote. Input connection changes, native flag changes and the normal guard interval trigger checks.

If Equipment Control leaves its exact unsupported-remote error visible after recognition returns, the Home accessibility service can dismiss that error and reopen the native Equipment Control page. It matches the native package, resource IDs and complete title/message/button text and limits retries to once per 30 seconds. The service requires window-content access and view IDs for this specific route. It does not act on System UI, wireless-debugging, computer authorization or network permission dialogs.

This repair does not pair a remote, create equipment support, select a soundbar/receiver profile, change an optical audio route or send power commands. Those device functions require separate physical verification. The remembered descriptor is device-local. New backups exclude it, and restore ignores imported descriptors while preserving the destination's existing cache. An old backup cannot establish recognition for a different remote.

## Verification

Host tests exercise the actual recognition class with controlled Android input/settings fixtures. They cover first recognition, the same remote losing its flag, a different or disconnected remote, multiple remotes, virtual/internal devices, absent IDs, empty descriptors, non-Fire TV and rejected writes. Build and host success do not establish a physical soundbar repair.

For an explicitly reserved physical Fire TV with an already supported paired remote:

```bash
FIRE_TV_UI_EQUIPMENT_TEST_SERIAL=TV_SERIAL python3 -m unittest discover -s tests -p test_equipment_native.py -v
```

This optional native regression briefly clears the recognition flag, checks its recovery and reopens the Equipment Control page using directional keys. It refuses an initially unsupported flag and restores the original flag afterward. Save the target's preferences and system baseline separately, verify the actual Receiver/Soundbar route and power/audio behavior, and restore Home/sleep state after the test. No physical result is claimed by the default host suite.
