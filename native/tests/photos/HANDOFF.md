# Photo frame integration and validation

The approved photo widget is implemented in `widget/photos` and starts hidden.
Register `PhotoWidgetProvider` at its stable `widget:photo-frame` identity and
register `PhotoSettingsActivity` as a nonexported activity. No media-library or
storage permission is needed. No gallery enumeration is used.

Configuration uses Android OPEN_DOCUMENT with explicit image selection and
persistent read grants. It supports up to 32 sources, order, individual removal
and replacement, slideshow intervals and proportion-preserving fit or crop.
Replacement retains the source's stable identity. Missing access or deleted
images retain the selection and show recovery text. Invalid selection or picker
cancellation preserves the prior configuration. Image bytes, source dimensions
and decoded dimensions are bounded. EXIF orientation is respected. Stop closes
pending streams, cancels workers and slideshow callbacks, and rejects stale
results. Repeated starts preserve rotation when saved settings are unchanged.

`native_photo_frame` is device-local private state. Exclude it from portable
backup/setup and migration whitelists. Private document grants do not transfer
between devices. Explicitly removed images are removed from this widget only;
shared provider files and existing platform grants are preserved.

Host validation: `python3 native/tests/photos/run.py` checks observable order,
replacement identity, empty versus setup state, interval/fit, bounds, strict
persistence, failed-write rollback and retention of unreadable settings.

After a combined source build, run `build_native_test.py` in this directory.
It builds `photos-proof.apk` and `photo-fixture.apk`. A coordinator with the
exclusive emulator lease should install both, then run instrumentation
`com.jeremykenedy.firetv.ui.photosproof/com.jeremykenedy.firetv.ui.nativeapp.widget.photos.PhotosInstrumentation`.
The fixture supplies only synthetic photos through an actual activity-result
URI grant. Instrumentation redirects the requested document selector to that
fixture, checks the original explicit SAF intent, durable grants, native add,
remove, order, interval, fit, cancel, invalid-image preservation, recovery and
stale callback rejection. Settings and newly acquired fixture grants are restored.
Uninstall `proof.photos.fixture` and the proof package after verification.

Current checks pass on the host and in isolated Android Java compilation and
fixture manifest linking. Instrumentation has been prepared, not executed here.
Native D-pad, Back, readability, actual document providers, slideshow timing,
process recreation and physical TV validation remain coordinator checks.
