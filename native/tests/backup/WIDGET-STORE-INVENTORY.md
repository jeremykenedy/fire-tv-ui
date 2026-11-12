# Widget presentation and private state inventory

Verified against native source preference/file adapters during the source preview integration. This is a preservation boundary, not a production migration completion claim.

| Source | Actual store | Portable backup / named setup | Apply and migration preservation |
| --- | --- | --- | --- |
| Local clock | Android system locale/timezone, layout slot | Slot order/visibility through `native_layout`; no system-setting export | Never changes system clock or timezone |
| World clocks | `native_world_clocks` / `clocks` String | Shared PresentationState registry, validated `WorldClocks.decode` | Stored IANA zones, labels and order retained; missing key remains absent |
| Breathing break | `native_breathing_break` cadence Int fields and `reduced_motion` Boolean | Shared registry contains inhale/hold/exhale/rest/duration/reduced_motion only | Runtime elapsed/anchor/running/process never imported; incompatible active-session changes rejected |
| Local timers/countdown | `native_local_timers` timer/countdown labels, Long duration/deadlines/remaining, zone, running/sound/boot | Excluded: runtime and labels currently share one schema; no verified presentation-only split | Entire store remains untouched by backup/setup apply; portable timer configuration parity remains open |
| Sun/moon | `native_astronomy` / `location` String with manual coordinates | Excluded as private location data | Original store retained; no location permission or coordinates imported |
| Photo frame | `native_photo_frame` / `document` String containing selected content URIs | Excluded: document grants and personal media are device local | Original selections/grants untouched; portable photo packaging remains open |
| Notes/lists | `native_household_notes` / `document` String | Excluded from base/named setups. Optional household envelope requires this export's checkbox plus existing local notes export permission | Imported document always summaryOnly=true and allowExport=false; selected content replaced only after explicit import confirmation, journaled with base presentation; omission leaves original notes untouched |
| Reminders | `native_household_reminders` / `document` String | Excluded from base/named setups. Optional household envelope requires this export's checkbox plus existing local reminder export permission | Imported document always summaryOnly=true, allowExport=false, enabled=false; local user must enable reminders again. Omission leaves reminders untouched |
| Quick actions | private `noBackupFilesDir/quick-actions-v1.txt` | Excluded: selected components and URLs are private local configuration | Never opened, rewritten, removed or exported by this module |

The shared safe registry additionally contains reviewed app-grid/focus/layout/card/folder/input/icon/theme presentation fields. Supported Clock/Year widget groups and their exact identities, sizes and order are portable; unknown fields, unsupported widgets and private widget data reject export/import instead of being discarded. Older portable documents without a widget grouping retain the destination grouping. Account connections and client identity, hardware controls, Home routing state, lock verifier/retry files, personal media bytes, document grants, Plex encrypted files and Android Keystore material are outside every portable document here.

Production migration still needs the complete in-place preference/private-file inventory, package/signer/version continuity, recovery and physical-device gates. Exclusion from portable documents does not authorize deleting or resetting the corresponding in-place data. No production data was inspected for this inventory.
