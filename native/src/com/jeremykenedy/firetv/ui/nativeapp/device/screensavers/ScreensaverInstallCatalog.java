package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import java.net.URL;

/** Reviewed source metadata from app/screensavers.json; no remote catalog can replace trust anchors. */
public final class ScreensaverInstallCatalog {
    private ScreensaverInstallCatalog() {}
    public static final class Entry {
        public final String name, packageName, repository, tag, url, sha256, description;
        public final long bytes;
        Entry(String name, String packageName, String repository, String tag, String url, String sha256, String description, long bytes) {
            try {
                URL location = new URL(url);
                if (!(repository.matches("jeremykenedy/[A-Za-z0-9_.-]+") || repository.equals("theothernt/AerialViews"))
                        || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
                        || !sha256.matches("[a-f0-9]{64}") || bytes <= 0 || bytes > 250L * 1048576
                        || !"https".equals(location.getProtocol()) || !"github.com".equals(location.getHost())
                        || location.getUserInfo() != null || location.getPort() != -1
                        || location.getQuery() != null || location.getRef() != null
                        || !location.getPath().startsWith("/" + repository + "/releases/download/" + tag + "/")) {
                    throw new IllegalArgumentException("Invalid reviewed screensaver release.");
                }
            } catch (java.net.MalformedURLException invalid) { throw new IllegalArgumentException(invalid); }
            this.name = name; this.packageName = packageName; this.repository = repository;
            this.tag = tag; this.url = url; this.sha256 = sha256; this.description = description; this.bytes = bytes;
        }
    }
    public static final Entry[] ENTRIES = {
        new Entry("Aerial Views", "com.neilturner.aerialviews", "theothernt/AerialViews", "1.8.5", "https://github.com/theothernt/AerialViews/releases/download/1.8.5/aerial-views-1.8.5.apk", "a20b4bc748da5ce890d96536494952aa4f50a6b589c50dee176415c021407070", "", 9147039L),
        new Entry("AndroSaver", "com.androsaver", "jeremykenedy/androsaver", "mirror-stable", "https://github.com/jeremykenedy/androsaver/releases/download/mirror-stable/androsaver.apk", "2a1230dce85938b482a31ad9fe8f6589d05aa2018e9a1f2bba4e407f8f990370", "", 6208952L),
        new Entry("Snoozy", "com.overdevs.snoozy", "jeremykenedy/Snoozy", "mirror-v1.0", "https://github.com/jeremykenedy/Snoozy/releases/download/mirror-v1.0/app-release.apk", "d7eea09b88899bbeaddd84da5fb89f2350baae2ca52d8772baf652c63ba37781", "", 39665219L),
        new Entry("Aquarium Live", "com.jeremykenedy.aquariumlive", "jeremykenedy/aquarium-live", "v1.0.3", "https://github.com/jeremykenedy/aquarium-live/releases/download/v1.0.3/aquarium-live.apk", "4fd6d949f2a6090787b00b1584510307260133361248fbfb77ada01b5841db4a", "", 107269L),
        new Entry("Aquarium 4K", "com.jeremykenedy.firetv.aquarium", "jeremykenedy/fire-tv-aquarium", "v1.3.6", "https://github.com/jeremykenedy/fire-tv-aquarium/releases/download/v1.3.6/aquarium-4k.apk", "5c515e907e5747420da97ba598cdb6a770280d6101a345eab114a2e9032b4628", "", 104036933L),
        new Entry("Jellyfish Drift", "com.jeremykenedy.jellyfishdrift", "jeremykenedy/jellyfish-drift", "v3.0.0", "https://github.com/jeremykenedy/jellyfish-drift/releases/download/v3.0.0/jellyfish-drift.apk", "2e3a8e5ad912b40bb614568e83c0f752b26e5be3bf9e8f06bb02e917063e3cb2", "", 1242197L),
        new Entry("Jellyfish Drift V2", "com.jeremykenedy.jellyfishdriftv2", "jeremykenedy/jellyfish-drift-v2", "v2.0.0", "https://github.com/jeremykenedy/jellyfish-drift-v2/releases/download/v2.0.0/jellyfish-drift-v2.apk", "90697388e2719412136ecf9ee0859f9245a17fb22188e9cfeb14d685a9e5b2ed", "", 35292388L),
        new Entry("Firefly Grove", "com.jeremykenedy.fireflygrove", "jeremykenedy/firefly-grove", "v1.0.0", "https://github.com/jeremykenedy/firefly-grove/releases/download/v1.0.0/firefly-grove.apk", "737dadedfad696ad03de96821b75c43c33e2e6ca7c2a7dfde812e987256f628a", "", 74445L),
        new Entry("Neon Corridor", "com.jeremykenedy.neoncorridor", "jeremykenedy/neon-corridor", "v1.0.0", "https://github.com/jeremykenedy/neon-corridor/releases/download/v1.0.0/neon-corridor.apk", "883626ba2660a719672a2ef30bbeecd852c3c65ee7c2c34984b9a1c035e9a924", "", 172749L),
        new Entry("Starfield Drift", "com.jeremykenedy.starfielddrift", "jeremykenedy/starfield-drift", "v1.1.0", "https://github.com/jeremykenedy/starfield-drift/releases/download/v1.1.0/starfield-drift.apk", "ecc33ebde27ab9e88edff58a0163f16c09b6d867fb12a919102686e97a012a50", "", 33485L),
        new Entry("Twilight Hearth", "com.jeremykenedy.twilighthearth", "jeremykenedy/twilight-hearth", "v1.1.0", "https://github.com/jeremykenedy/twilight-hearth/releases/download/v1.1.0/twilight-hearth.apk", "dd39810f8bf2a67af7f133554897741323d9a14d606b3fc1a441285646b440f5", "", 27207363L),
        new Entry("Nebula Drift", "com.jeremykenedy.nebuladrift", "jeremykenedy/nebula-drift", "v1.1.0", "https://github.com/jeremykenedy/nebula-drift/releases/download/v1.1.0/nebula-drift.apk", "4d2d7db1d07fb7a317d4461c8946969ba83b949b7a38801d9377b4275a039f30", "", 29368L),
        new Entry("Vortex Spiral", "com.jeremykenedy.vortexspiral", "jeremykenedy/vortex-spiral", "v1.0.0", "https://github.com/jeremykenedy/vortex-spiral/releases/download/v1.0.0/vortex-spiral.apk", "dc689afed574024d97fc40ae40912cfc2d3d5ad6db3c1008ce2273118c6e72af", "", 49869L),
        new Entry("Signal Rain", "com.jeremykenedy.signalrain", "jeremykenedy/signal-rain", "v1.0.0", "https://github.com/jeremykenedy/signal-rain/releases/download/v1.0.0/signal-rain.apk", "39a05659217658d39d44200d931ace8fb4edfe5034372ee35c945159fe392615", "", 33485L),
        new Entry("Retro Flight", "com.jeremykenedy.retroflight", "jeremykenedy/retro-flight", "v1.0.0", "https://github.com/jeremykenedy/retro-flight/releases/download/v1.0.0/retro-flight.apk", "7845db142ef5f4cb4e92d96c1b6fa92ee2f7bd86fe7c47cd52627b37cc92cbf2", "", 53965L),
        new Entry("Pipeworks Dream", "com.jeremykenedy.pipeworksdream", "jeremykenedy/pipeworks-dream", "v1.1.1", "https://github.com/jeremykenedy/pipeworks-dream/releases/download/v1.1.1/pipeworks-dream.apk", "62a038159d8db3b04346e23b0ae68707781fa48299ae37a6f77626f9ae646082", "", 62276L),
        new Entry("Aqua Surface Drift", "com.jeremykenedy.aquasurfacedrift", "jeremykenedy/aqua-surface-drift", "v1.1.0", "https://github.com/jeremykenedy/aqua-surface-drift/releases/download/v1.1.0/aqua-surface-drift.apk", "b0aa42f82a1561e7c27fba8229c83d00eff93498a4e3e404c580137333021049", "", 45773L),
        new Entry("Cloud Drift Clock", "com.jeremykenedy.clouddriftclock", "jeremykenedy/cloud-drift-clock", "v2.0.0", "https://github.com/jeremykenedy/cloud-drift-clock/releases/download/v2.0.0/cloud-drift-clock.apk", "abb8fdf3abdd1c94b95106b6bd0cbdf85822ccffd4caec9cdbd6dcb99919fae8", "Real sunset cloud flight with playback speed and an optional clock. Legacy palette and density values are retained but inactive.", 40297575L),
        new Entry("Earthbound Orbit", "com.jeremykenedy.earthboundorbit", "jeremykenedy/earthbound-orbit", "v1.0.0", "https://github.com/jeremykenedy/earthbound-orbit/releases/download/v1.0.0/earthbound-orbit.apk", "b8ff3f377513360bd246ae569ac0872cfd9ed06d75a90d7f13199ae0715b21b9", "", 582419L),
        new Entry("Perseid Passage", "com.jeremykenedy.perseidpassage", "jeremykenedy/perseid-passage", "v1.0.0", "https://github.com/jeremykenedy/perseid-passage/releases/download/v1.0.0/perseid-passage.apk", "9fa45a8f906fd9783d2831dcc594d3387e8f6cc5f95900be1b2447b450df4799", "", 33652L),
        new Entry("Split-Flap Drift", "com.jeremykenedy.splitflapdrift", "jeremykenedy/split-flap-drift", "v1.1.0", "https://github.com/jeremykenedy/split-flap-drift/releases/download/v1.1.0/split-flap-drift.apk", "9cbf91acf586bdf0cb056fef5d53e204ddc9be38f9e5c994a603bbd666cbb351", "", 29368L),
        new Entry("Canopy Drift", "com.jeremykenedy.canopydrift", "jeremykenedy/canopy-drift", "v1.1.0", "https://github.com/jeremykenedy/canopy-drift/releases/download/v1.1.0/canopy-drift.apk", "5175918229ce69cc423bfeb35daa0729ecc7be853801fa7e367a5de06ca4e14c", "", 197325L),
        new Entry("Contour Flow", "com.jeremykenedy.contourflow", "jeremykenedy/contour-flow", "v1.1.0", "https://github.com/jeremykenedy/contour-flow/releases/download/v1.1.0/contour-flow.apk", "6b4302ee3220c25df5b40c3e7ff6d72d6c859b4403203f0577a01a9b7cc2ebf6", "", 66572L),
        new Entry("Skyburst Nocturne", "com.jeremykenedy.skyburstnocturne", "jeremykenedy/skyburst-nocturne", "v1.0.0", "https://github.com/jeremykenedy/skyburst-nocturne/releases/download/v1.0.0/skyburst-nocturne.apk", "2ce571933d6f4a8017ada3c5598d21edc363b51598f4d4c302bafebb0863a57a", "", 37748L),
        new Entry("Pulse Circuit", "com.jeremykenedy.pulsecircuit", "jeremykenedy/pulse-circuit", "v1.0.0", "https://github.com/jeremykenedy/pulse-circuit/releases/download/v1.0.0/pulse-circuit.apk", "5e71095d0b2966d1cf803ad9299ad7a233309848d1aea8e27423cfcf8ff96c95", "", 33485L),
        new Entry("Rain on Glass", "com.jeremykenedy.rainonglass", "jeremykenedy/rain-on-glass", "v1.0.0", "https://github.com/jeremykenedy/rain-on-glass/releases/download/v1.0.0/rain-on-glass.apk", "99c7c1c130644c8057db65a6a2398c98d69daa3f20bacc77588c4870624e340e", "", 33485L),
        new Entry("Rainforest Cascade", "com.jeremykenedy.rainforestcascade", "jeremykenedy/rainforest-cascade", "v1.1.0", "https://github.com/jeremykenedy/rainforest-cascade/releases/download/v1.1.0/rainforest-cascade.apk", "88602f840deae1e9a7fa8de5b1dda03601c70d0d70399ab11262e1817e7de31f", "", 72053957L),
        new Entry("Blue Meridian", "com.jeremykenedy.bluemeridian", "jeremykenedy/blue-meridian", "v1.0.1", "https://github.com/jeremykenedy/blue-meridian/releases/download/v1.0.1/blue-meridian.apk", "7f3b575f45d7afa5ca68ff8510d84f5b42c6ac6c3c21903628898f6a5dff5ec2", "Earth day and night from archival satellite imagery, with cloud cover, city lights and gentle screen drift.", 2569417L),
        new Entry("Helios Lightfield", "com.jeremykenedy.helioslightfield", "jeremykenedy/helios-lightfield", "v1.0.1", "https://github.com/jeremykenedy/helios-lightfield/releases/download/v1.0.1/helios-lightfield.apk", "2716752c678916f947dec465213f23be20af4eb224272a54e9ae500c5bed6eb2", "", 33485L),
        new Entry("Hearth Presence", "com.jeremykenedy.hearthpresence", "jeremykenedy/hearth-presence", "v1.1.0", "https://github.com/jeremykenedy/hearth-presence/releases/download/v1.1.0/hearth-presence.apk", "9541eb4fe2f4d7d69ac1f85b38144f5b5926274291ebec796d8150d27e07b781", "", 121223466L),
        new Entry("Slot Vision", "com.jeremykenedy.reelatrium", "jeremykenedy/slot-vision", "v1.0.0", "https://github.com/jeremykenedy/slot-vision/releases/download/v1.0.0/slot-vision.apk", "0ee21b586f3b306e6da57cacc149bebece35a01e6ef2c7af1512c0ddc51354e4", "", 62076478L),
        new Entry("Prism Clock", "com.jeremykenedy.prismclock", "jeremykenedy/prism-clock", "v1.1.0", "https://github.com/jeremykenedy/prism-clock/releases/download/v1.1.0/prism-clock.apk", "6d1a3204ca648033c3c0c1d68adcceb29e90ed1ff61ec24b7f65e1326b7621e8", "", 127947L),
        new Entry("Black Screen", "com.jeremykenedy.blackscreendream", "jeremykenedy/black-screen-dream", "v1.0.1", "https://github.com/jeremykenedy/black-screen-dream/releases/download/v1.0.1/black-screen-dream.apk", "3281db249bcf776ffcd04e55213d86dcda0efc1626881c8895051602567da97c", "", 21234L),
        new Entry("Skycast Weather", "com.jeremykenedy.skycastweather", "jeremykenedy/skycast-weather", "v1.0.0", "https://github.com/jeremykenedy/skycast-weather/releases/download/v1.0.0/skycast-weather.apk", "b8680ebc7314e8687281e5d405aa84d1b0f38d1f6505193f652b96fc9cf0e7e0", "Real sunset cloud flight with optional weather text, explicit city setup and one to seven forecast days. Hide weather for a text-free scene. The backdrop is not the city or current conditions.", 39552020L),
        new Entry("Nibbles HD", "com.jeremykenedy.nibbleshd", "jeremykenedy/nibbles-hd", "v1.0.0", "https://github.com/jeremykenedy/nibbles-hd/releases/download/v1.0.0/nibbles-hd.apk", "c49bc22e5cbf5067da4bbcb1cd63140d31b0a49a83f6826e111986ee4b7d7971", "", 41727L),
        new Entry("Lumen Breaker", "com.jeremykenedy.lumenbreaker", "jeremykenedy/lumen-breaker", "v1.0.0", "https://github.com/jeremykenedy/lumen-breaker/releases/download/v1.0.0/lumen-breaker.apk", "fc0513f85fc8563cd8463546108bbf5ea9c459ab72465c669b126fcc6c14269c", "Autonomous brick breaking with varied stages, up to three balls and restrained matte materials.", 74551L),
    };
}
