# Factory UI tile

On Fire TVs with an exported Amazon system HOME activity, **Factory UI** appears in the app list alongside installed launchers. It opens that exact native activity. An unavailable or non-system launcher is not presented as the factory interface.

Opening the tile does not disable Fire TV UI's Home setting. A temporary launch bypass lets the initial Amazon Home transition settle. A delivered Home key clears the bypass; where Fire OS does not deliver that key to accessibility, the next distinct Home transition restores normal redirection. The bypass is held only in process memory and expires automatically. It is not exported in backups.

Host regressions cover explicit native resolution, duplicate tile suppression, missing/non-system activities, the initial transition burst and subsequent Home routing. On the new physical Fire TV, the animated tile opened the fully rendered Amazon Home screen. Home from another app returned to Fire TV UI. A shell-injected Home press while already on Amazon Home did not produce another Home window transition and did not return to Fire TV UI. The equivalent physical remote check remains a release gate.

## Native Live TV tile

On supported Fire TVs, **Live TV** opens the built-in channel guide through its public `livetv://showGuideButtonContent` entry. It retains the native antenna and streaming channel experience. The launcher resolves an exported system activity with no activity permission requirement, then launches that explicit resolved component. It does not request the privileged guide permission or open TV input setup.

The regular, animated and **Open** menu launch paths use the same reviewed native targets. On the new physical Fire TV, the Live TV tile opened the fully rendered native channel guide. Back and Home returned to Fire TV UI.
