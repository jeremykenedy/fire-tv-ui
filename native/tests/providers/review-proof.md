# Merged recommendation path review

Reviewed source on 2026-11-01. This records source inspection and focused changed-code checks, not native acceptance or real account verification.

## Coverage

Inspected `RecommendationSources`, `RecommendationLoader`, `TvRecommendations`, `NotificationRecommendations`, `NotificationRules`, `RecommendationActions`, source settings, Main action labels, card artwork lifecycle, `ArtworkLoader`, and Plex repository, API, transport/redirect policy, feed parsing, encrypted connection/profile storage, pairing error reporting, artwork cache/provider and details links. Reviewed the manifest permission/export boundaries. No private account files, tokens or live services were read.

## Confirmed fixes in provider core

- Recommendation launches previously bypassed launcher locks. URI launches now require an Activity, guard the canonical publisher launcher and resolved destination, then revalidate the pinned component. A pending PIN gate returns `PENDING`; dispatch success remains `OPENED`. Main and source settings must accept both outcomes without an unavailable toast.
- PendingIntent destinations are opaque through public APIs. Notification launch scans stored component locks for the exact publisher, requires the common launcher PIN once when any match exists, and revalidates the current source-owned action after authorization. Unreadable lock storage and non-Activity dispatch fail closed. This is conservative publisher-level gating, not inspection of the hidden target component.
- Untrusted TV artwork previously allowed another app's content/resource authority to be opened under the launcher's grants. Those schemes now require the actual publishing package; arbitrary private document/provider authorities are rejected. Trusted Plex cache references originate from its maintained adapter and private provider separately.

Focused new artwork ownership regression passed. The isolated provider fixture/proof APKs compile against SDK 36 after the guard changes. Existing unchanged host checks were not repeatedly rerun for this review.

## Verified source properties

- TV status remains distinct from optional source readiness. An optional failure retains other feeds. Plex is checked for explicit enablement before repository access; notifications default off and require Android listener access separately.
- Notification connection/post/remove events notify registered loaders. Active loaders debounce refresh, increment a generation, cancel superseded work and suppress stale/stopped callbacks; stop removes notification listeners and TV observers. Native arrival behavior still requires the notification runner.
- TV scans are capped at 2048 rows per table, 64 feeds and 24 programs per feed. Notifications scan at most 512 entries, with at most 32 feeds and 24 programs per feed. Plex permits five main feeds and eight authorized profile feeds, with at most 24 main programs and 96 profile programs. These produce at most 109 current feeds and 2168 retained programs. Historical unavailable slots still have the separate 512 preference bound; current-feed bounds alone do not protect discovery after repeated channel churn.
- PendingIntent eligibility checks recommendation category, nonblank bounded title, creator package, current installed publisher UID, notification UID where API permits, and an installed TV launch route. API 31+ also verifies activity action type. API 27-30 cannot inspect action type through that public API; these remain generic source-supplied handoffs, never playback claims. Click rechecks the active notification identity and token; no fill-in Intent or launcher credentials are supplied.
- Generic TV and notification links have no details/play guarantee. Plex declares details only, with `playIntentUri` absent. Main suppresses the duplicate generic title link when it equals details.
- Plex credentials stay in bounded AES-GCM Android Keystore-backed private storage; account/server/profile tokens are absent from card keys, artwork URIs and details links. Network error reporting replaces underlying exception messages. Artwork provider is not exported; redirect policy removes credentials on approved cross-origin artwork redirects. No account information or raw exceptions are logged by the reviewed runtime paths.
- Card artwork requests have bounded queue/cache/bytes/decoded dimensions and elapsed deadlines; detached/stale requests cannot replace current artwork. Provider descriptor reads are nonblocking and deadline-polled. HTTPS redirects are disabled in the generic loader.

## Open owner fixes and native gaps

- Plex cancellation fix read back after owner correction: read-side API now receives the CancellationSignal, disconnects active HTTP off the caller thread, checks cancellation around requests and each body read, preserves cancellation exceptions and includes redirects in whole-request deadlines of 15 seconds for metadata and 6 seconds for artwork. Connection/read timeouts are at most 3 seconds. Artwork cancellation is rethrown. The Plex owner reports eight focused fixture suites and isolated SDK compilation passing; those unchanged suites were not rerun here.
- Plex metadata fix read back after owner correction: retained title, series title and combined episode labels are bounded to 512 characters, summary to 2048 and artwork paths to 8192. The owner added oversized fixture coverage and reports it passing.
- Confirmed open integration gap sent to root/cards rendering owner: `HomeContentView.refreshPreferences` calls `HomeSlots.discover`, which appends fresh feed IDs to retained `LayoutOptions.order`; exceeding 512 throws on the UI listener. Repeated publisher channel replacement can exhaust historical capacity despite bounded current feeds. Discovery must preserve saved identities while declining new ones gracefully, with an explicit capacity state rather than a crash. No layout-owned file was edited here.
- Native acceptance must still verify real notification arrival/removal refresh, foreground title/app handoff, launcher PIN cancel/success behavior, stale/foreign PendingIntent rejection, artwork cancellation, fixture cleanup and restoration. Emulator proof does not establish real Plex playback or physical Fire TV behavior.
- This review does not authorize listener grants, live credentials, production deployment or external account reads. No device lease, shared build, publication or commit was used here.
