# Household notes and lists

Implemented local-only notes and checklist items with stable UUIDs; native add/edit/delete/check/uncheck/reorder controls, restored row focus, activity recreation, empty/setup/ready/unavailable states, 64 item and 512 character limits. Data uses one bounded exact-type JSON document in `native_household_notes` / `document`; unreadable data is retained. Failed commits attempt restoration of the old document, including in-memory SharedPreferences state.

Provider: `com.jeremykenedy.firetv.ui.nativeapp.widget.notes.NotesWidgetProvider`, stable key `widget:household-notes`. Default hidden via WidgetProvider default visibility. Configuration opens `widget.notes.NotesActivity`, which must be nonexported in the app manifest. Registry handled by layout owner.

Home shows counts only by default. Showing item previews requires an explicit warning and selection. This is a display privacy preference, not authentication: anyone using the launcher can open the editor. No account, remote sync, sharing, analytics, external fetch, background timer or alarm exists. Recurring chores are outside this implementation.

Portable content is excluded by default. `NotesPreferences.exportAllowed(context)` reads current local consent. `portableDocument(context)` throws unless consent is enabled. Explicit settings describe that portable text is readable to anyone with the file. Backup integration must not silently include this store. Import should force export consent off and Home summaries on, or preserve local privacy choices rather than accepting file-provided consent.

Host checks: `python3 native/tests/notes/run.py` passed, including mutations, bounds, duplicate IDs, invalid exact types, multiline/Unicode roundtrip, private Home summaries, opt-in, corrupt-storage preservation and failed commit recovery.

Native instrumentation prepared and compiled: `python3 native/tests/notes/build_native_test.py`. It uses only its own `tests/notes/.build` intermediates and reads the preview signer. Instrumentation APK: `native/tests/notes/.build/notes-proof.apk`. Runner: `com.jeremykenedy.firetv.ui.notesproof/com.jeremykenedy.firetv.ui.nativeapp.widget.notes.NotesInstrumentation`.

The native test exercises actual text editor/dialog add/edit, check/uncheck, move, delete, Home privacy, backup privacy and recreation, then restores the exact notes preferences. It has not been executed by this worker. Coordinator owns APK build, installation/device lease, route verification, native screenshots, physical remote acceptance and final artifact proof. No devices were accessed and no shared build/manifest/Main/Settings files were edited by this worker. No commit or publication was made.
