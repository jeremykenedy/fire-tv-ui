# Fresh installation defaults

A fresh installation starts with the reviewed five-column X Pro layout and app order, 20-minute light standby, deep sleep off and Alexa wake enabled where the TV supports it. Apps appear when installed. HDMI 1 and HDMI 2 retain the reviewed visibility and Xbox One/Wii U icon choices; both image files are bundled and copied into the app's own files directory, so a new TV does not depend on another TV's private image paths.

Defaults apply only when Android reports an initial installation and no visual layout settings exist. Existing layouts, imported settings and installer-created preferences are preserved. The explicit simple setup action uses the same reviewed template and no longer forces Never sleep.

The template excludes device-local remote recognition, trusted debugging network identity, accessibility consent, cached content and the selected DreamService. Weather widget locations are empty until configured. Existing system screensaver selection is retained rather than copying a component that might not be installed. Update blocking still requires Android VPN consent; the template does not grant it.

## Verification

The API34 fresh test used a separate disposable package containing the same compiled classes and assets. Its five-column layout was viewed, and its saved standby/deep-sleep/wake values and bundled icon references were checked. A same-signer upgrade of the existing UI preserved its typed preferences exactly. Eight OS values, external backup files, storage access and the previous guard state were restored, and the disposable package was removed. Native HDMI image rendering remains part of physical validation because the emulator has no matching HDMI inputs.
