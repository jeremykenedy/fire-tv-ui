# Native performance comparison

The harness targets a verified installed preview, never builds or replaces the main APK. Acquire the emulator/device lease before running. It rejects a foreground other than preview Main, an unexpected installed APK hash, a source commit that differs from the matching local build manifest, or an existing performance proof package.

Build only this test APK against the coordinator's compiled preview classes:

```sh
python3 native/tests/performance/build_native_test.py
python3 native/tests/performance/run.py --serial emulator-5582 --expected-apk-sha256 <verified-sha256> --source-ref <matching-commit> --output <private-evidence-directory>
```

The test package has the preview signer and can inspect its target's preferences. No `run-as`, root, permission grant, provider row or app preference mutation is needed. The runner hashes preferences before and after, uninstalls its temporary test package and restores preview Main. It launches Android Settings solely to measure returning to the existing warm task; Settings values remain unchanged. Snapshot-only instrumentation exits the target process, so the runner resumes Main before capturing initial memory and screenshots.

The bounded workload includes two cold starts, two existing-task returns, 32 paced D-pad moves over actual apps, 64 temporary app-like tiles and eight cards with local resource artwork, 32 synthetic focus/card updates, and 16 canceled artwork requests followed by a successful request. Temporary views are removed in `finally`. A five-second post-workload hold lets the host capture `gfxinfo` and `meminfo` while the instrumented process still exists. FrameMetrics report View-window total frame duration. The host aborts instrumentation after 40 seconds and each ADB operation has a 40-second timeout.

The baseline from November 1, 2026 uses source `95c3e85`, APK SHA-256 `1d9f408488a57e5453833809937afb58e1e85fc68b527f45ca6e96a645bd07ff`, and API34 emulator5582 (`sdk_google_atv64_amati_arm64`, 1920x1080, 320dpi). All 53 declared build inputs match that commit. The shared working tree already contained later work, which did not enter the artifact.

The completed baseline evidence is retained in private delivery records and is not bundled with source or public release assets. `baseline.json` identifies the artifact, target, Android animation settings, source reference, host load, startup output, restoration and proof APK hash. `source-identity.json` checks all declared inputs. `native-performance.txt`, `gfx-workload.txt` and `memory-workload.txt` preserve the measurements. `home-restored.png` was inspected after the transition settled.

Baseline observations:

- Five real app tiles; all 32 moves immediately selected the expected neighboring tile. The 1404ms workload includes deliberate 20ms pacing and instrumentation overhead.
- Android cold-start TotalTime: 183ms and 538ms. Existing-task return WaitTime: 147ms and 8ms. Android reported UNKNOWN launch state and no TotalTime for task returns; these are not warm creation measurements.
- Real focus: 69 frame samples, median 24.30ms, p95 68.42ms, maximum 123.29ms.
- Synthetic inventory: 76 frame samples, median 41.41ms, p95 72.27ms, maximum 174.02ms. Creation plus UI idle took 210ms.
- Process PSS: 27857KiB before the synthetic inventory, 41882KiB loaded. No claim of a leak follows from a one-run allocation difference.
- Sixteen canceled requests delivered zero obsolete callbacks; the following valid local resource request completed. This does not exercise a stalled network or content provider.
- Temporary views removed, proof APK uninstalled, preference digest unchanged, and original preview Main foreground restored. No physical TV or older5580 access occurred.

The host's 1/5/15-minute load averages were 55.06/37.45/19.34 during parallel development. These measurements are noisy comparison data, not acceptable physical TV performance proof or a measured source bottleneck. Do not infer remote latency, app FPS, real-service loading, large installed-package enumeration or visible production quality from this workload. Repeat a bounded comparison on the same target when a candidate exists, with the host load recorded. Do not optimize based solely on this run.

Two earlier preparation attempts are separate from the completed evidence: the nondebuggable preview rejected `run-as`, then Android rejected shell launching the unexported preview Settings activity. The latter attempt's cleanup verified unchanged preferences and restored foreground. The completed run uses target instrumentation for preference hashing and a supported Android Settings action. Later harness hardening adds preflight source-manifest enforcement, timeout handling and a settled final screenshot; it does not change the native workload that passed.

API semantics were checked against Android's official FrameMetrics reference and rendering guide:

- https://developer.android.com/reference/android/view/FrameMetrics
- https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering

## Combined regression queue preparation

`regression_queue.py --output <private-path>/queue.json` reads test manifests and writes the prioritized execution plan without ADB, installation or test builds. The primary queue retains the existing ten suites, HomeContent, folders/locks/inputs/appearance, layout widget/mode behavior, network/protection/screensavers, migration crash recovery, setups, updater and backup readiness. A separate later queue covers eight additional widget modules, local Plex cards and notification cards.

Only after the coordinator supplies the exact new APK hash and shared classes readiness may the operator run:

```sh
python3 native/tests/performance/regression_queue.py --output <private-path>/queue.json --build-tests --shared-classes-ready --artifact-sha256 <exact-new-apk-hash> --stage initial
```

Use `--stage later` or `all` deliberately for later test builds. The script validates the local artifact and manifest hash, builds only isolated test outputs and records each build log. It never installs or runs anything, and execution authorization remains false in its JSON. Runtime suites remain serial and require an exact installed-artifact check, per-suite state snapshot/restoration readback, fixture cleanup, permission/listener restoration and foreground restoration. The plan records unusual success protocols and fresh-store refusal constraints rather than treating every instrumentation exit code as equivalent.

Before the next build, root authorized narrow updates to NetworkInstrumentation and ProtectionInstrumentation so each selects the actual Device category before its unchanged child routes. These test edits passed diff checks; native builds and execution wait for the new artifact/classes authorization. No other route tests or product source were edited for queue preparation.
