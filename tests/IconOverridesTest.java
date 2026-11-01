package com.jeremykenedy.firetv.ui;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.*;

public final class IconOverridesTest {
    static final class Prefs implements SharedPreferences {
        final Map<String,Object> values = new HashMap<>();
        boolean fail;
        public String getString(String key,String fallback) { return values.containsKey(key) ? (String)values.get(key) : fallback; }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback) { return values.containsKey(key) ? (Set<String>)values.get(key) : fallback; }
        public Editor edit() { return new Editor() {
            final Map<String,Object> changes = new HashMap<>();
            public Editor putString(String k,String v) {changes.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v) {changes.put(k,new HashSet<>(v));return this;}
            public Editor remove(String k) {changes.put(k,null);return this;}
            public boolean commit() {for(Map.Entry<String,Object> e:changes.entrySet()) {if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return !fail;}
            public void apply() {commit();}
        }; }
    }
    static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Prefs prefs = new Prefs();
        JSONObject apps = new JSONObject()
                .put("app.one",new JSONObject().put("hidden",true).put("hideWatchNext",true).put("customIconUri","file:///one.png").put("future","preserved"))
                .put("tvinput_name:HDMI 3",new JSONObject().put("customIconUri","file:///sony.png"))
                .put("app.two",new JSONObject().put("customIconUri","file:///two.png"));
        prefs.values.put("app_visibility",apps.toString());
        prefs.values.put("icon_pack_package","builtin:clean_line");
        prefs.values.put("app_order","app.two,app.one");
        android.app.Activity context = new android.app.Activity();context.prefs=prefs;
        IconOverrides.save(prefs,"app.one",true);
        JSONObject saved = new JSONObject(prefs.getString("app_visibility",""));
        check(!saved.getJSONObject("app.one").has("customIconUri"),"selected custom icon removed");
        check(saved.getJSONObject("app.one").getBoolean("hidden") && saved.getJSONObject("app.one").getBoolean("hideWatchNext"),"visibility unchanged");
        check(saved.getJSONObject("app.one").getString("future").equals("preserved"),"unknown per-app setting retained");
        check(saved.getJSONObject("app.two").toString().equals(apps.getJSONObject("app.two").toString()),"other app untouched");
        check(IconOverrides.pack(context,"app.one")==null,"factory bypasses active pack");
        check("builtin:clean_line".equals(IconOverrides.pack(context,"app.two")),"other tile keeps pack");
        check("builtin:clean_line".equals(prefs.values.get("icon_pack_package")) && "app.two,app.one".equals(prefs.values.get("app_order")),"global pack and order preserved");
        IconOverrides.save(prefs,"tvinput_name:HDMI 3",true);
        check(!new JSONObject(prefs.getString("app_visibility","")).getJSONObject("tvinput_name:HDMI 3").has("customIconUri"),"input resets independently");
        IconOverrides.save(prefs,"app.one",false);
        check("builtin:clean_line".equals(IconOverrides.pack(context,"app.one")),"theme can be reapplied");
        check(IconOverrides.pack(context,"tvinput_name:HDMI 3")==null,"other factory choice preserved");
        IconOverrides.save(prefs,"tvinput_name:HDMI 3",false);
        check(!prefs.values.containsKey(IconOverrides.FACTORY),"empty override setting removed");
        String before = prefs.getString("app_visibility","");
        prefs.fail=true;
        try {IconOverrides.save(prefs,"app.two",true);throw new AssertionError("write failure ignored");}catch(IllegalStateException expected){}
        check(before.equals(prefs.getString("app_visibility","")),"failed store did not clear artwork");
        check(!prefs.values.containsKey(IconOverrides.FACTORY),"failed store did not retain factory override");
        prefs.fail=false;prefs.values.put("app_visibility","bad json");
        try {IconOverrides.save(prefs,"app.two",true);throw new AssertionError("malformed settings overwritten");}catch(org.json.JSONException expected){}
        check(prefs.values.get("app_visibility").equals("bad json"),"malformed state preserved");
        check(!IconOverrides.consumeRefresh(),"no unnecessary refresh");
        IconOverrides.refresh(context);
        check(context.refreshes==1 && IconOverrides.consumeRefresh() && !IconOverrides.consumeRefresh(),"one explicit refresh consumed once");
        System.out.println("Icon reset, per-tile factory/theme choice and preservation checks passed");
    }
}
