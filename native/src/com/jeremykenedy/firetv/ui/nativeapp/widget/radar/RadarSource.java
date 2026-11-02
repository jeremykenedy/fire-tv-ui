package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed standard-image products from the official NWS directory. No caller supplied URLs. */
public final class RadarSource {
    public final String id, stem, label;
    public final boolean station;
    private RadarSource(String stem, String label, boolean station) {
        this.stem=stem; this.label=label; this.station=station;
        this.id=(station ? "station:" : "region:")+stem;
    }
    public String imageUrl() { return "https://radar.weather.gov/ridge/standard/"+stem+"_0.gif"; }
    public String pageUrl() { return "https://radar.weather.gov/"+(station?"station/":"region/")+(station?stem:stem.toLowerCase(java.util.Locale.US))+"/standard"; }
    public static final List<RadarSource> REGIONS;
    public static final List<RadarSource> STATIONS;
    static {
        List<RadarSource> regions=new ArrayList<>();
        regions.add(new RadarSource("ALASKA","Alaska",false));
        regions.add(new RadarSource("CARIB","Caribbean",false));
        regions.add(new RadarSource("CENTGRLAKES","Central Great Lakes",false));
        regions.add(new RadarSource("CONUS","Contiguous United States",false));
        regions.add(new RadarSource("GUAM","Guam",false));
        regions.add(new RadarSource("HAWAII","Hawaii",false));
        regions.add(new RadarSource("MIDATLANTIC","Mid Atlantic",false));
        regions.add(new RadarSource("NORTHEAST","Northeast",false));
        regions.add(new RadarSource("NORTHROCKIES","Northern Rockies",false));
        regions.add(new RadarSource("PACNORTHWEST","Pacific Northwest",false));
        regions.add(new RadarSource("PACSOUTHWEST","Pacific Southwest",false));
        regions.add(new RadarSource("SOUTHEAST","Southeast",false));
        regions.add(new RadarSource("SOUTHMISSVLY","Southern Mississippi Valley",false));
        regions.add(new RadarSource("SOUTHPLAINS","Southern Plains",false));
        regions.add(new RadarSource("SOUTHROCKIES","Southern Rockies",false));
        regions.add(new RadarSource("UPPERMISSVLY","Upper Mississippi Valley",false));
        REGIONS=Collections.unmodifiableList(regions);
        List<RadarSource> stations=new ArrayList<>();
        for(String stem: ("KABR KABX KAKQ KAMA KAMX KAPX KARX KATX KBBX KBGM KBHX KBIS KBLX KBMX KBOX KBRO KBUF KBYX KCAE KCBW KCBX KCCX KCLE KCLX KCRP KCXX KCYS KDAX KDDC KDFX KDGX KDIX KDLH KDMX KDOX KDTX KDVN KDYX KEAX KEMX KENX KEOX KEPZ KESX KEVX KEWX KEYX KFCX KFDR KFDX KFFC KFSD KFSX KFTG KFWS KGGW KGJX KGLD KGRB KGRK KGRR KGSP KGWX KGYX KHDC KHDX KHGX KHNX KHPX KHTX KICT KICX KILN KILX KIND KINX KIWA KIWX KJAX KJGX KJKL KLBB KLCH KLGX KLNX KLOT KLRX KLSX KLTX KLVX KLWX KLZK KMAF KMAX KMBX KMHX KMKX KMLB KMOB KMPX KMQT KMRX KMSX KMTX KMUX KMVX KMXX KNKX KNQA KOAX KOHX KOKX KOTX KPAH KPBZ KPDT KPOE KPUX KRAX KRGX KRIW KRLX KRTX KSFX KSGF KSHV KSJT KSOX KSRX KTBW KTFX KTLH KTLX KTWX KTYX KUDX KUEX KVAX KVBX KVNX KVTX KVWX KYUX PABC PACG PAEC PAHG PAIH PAKC PAPD PGUA PHKI PHKM PHMO PHWA TJUA").split(" ")) {
            stations.add(new RadarSource(stem,"Radar station "+stem,true));
        }
        STATIONS=Collections.unmodifiableList(stations);
    }
    public static RadarSource find(String id) {
        for(RadarSource source:REGIONS) if(source.id.equals(id)) return source;
        for(RadarSource source:STATIONS) if(source.id.equals(id)) return source;
        return null;
    }
}
