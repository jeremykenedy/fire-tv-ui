package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.content.Context;

public final class RadarPreferencesTest {
    public static void main(String[] args) {
        Context context = new Context();
        check(RadarPreferences.load(context)==null,"No default area or inferred location");
        context.prefs.values.put("unrelated_owner_choice",17);
        RadarPreferences.save(context,"region:PACNORTHWEST");
        check(RadarPreferences.load(context).id.equals("region:PACNORTHWEST"),"Explicit area persists");
        check(context.prefs.values.get("unrelated_owner_choice").equals(17),"Unrelated choices untouched");
        context.prefs.values.put("source",99);
        try{RadarPreferences.load(context);throw new AssertionError("Malformed value reported");}catch(IllegalStateException expected){}
        check(context.prefs.values.get("source").equals(99),"Malformed raw choice retained");
        context.prefs.fail=true;
        try{RadarPreferences.save(context,"station:KRTX");throw new AssertionError("Failed save reported");}catch(IllegalStateException expected){}
        check(context.prefs.values.get("source").equals(99),"Failed save rolls back original typed value");
        context.prefs.fail=false;RadarPreferences.save(context,"");
        check(RadarPreferences.load(context)==null,"Clear selection returns to explicit setup state");
        try{RadarPreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("source","https://example.invalid"));throw new AssertionError("Unsafe setup rejected");}catch(IllegalArgumentException expected){}
        System.out.println("Radar explicit selection and preservation checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
