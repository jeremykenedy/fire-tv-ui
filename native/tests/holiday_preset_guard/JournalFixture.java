package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic values for a disposable same-signer lab only. */
public final class JournalFixture {
    private JournalFixture() {}
    public static void seed(Context context) throws Exception {
        Map<String,Object> values=new LinkedHashMap<>();
        old(values,"source_appearance","base","aurora");
        old(values,"source_appearance","base_palette",null);
        old(values,"source_appearance","base_frozen",false);
        old(values,"source_appearance","scene","halloween");
        old(values,"native_focus","legacy_app_default",true);
        old(values,"native_focus","border_rgb",0x112233);
        old(values,"native_focus","border_enabled",null);
        old(values,"native_focus","border_opacity",null);
        old(values,"native_focus","fill_enabled",null);
        old(values,"native_icon_packs","package","original");
        byte[] data=new PreferenceSnapshot(values).encode();
        File file=new File(context.getNoBackupFilesDir(),"holiday-preset-v1.snapshot");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(data);out.write(MessageDigest.getInstance("SHA-256").digest(data));out.getFD().sync();}
    }
    private static void old(Map<String,Object> values,String file,String key,Object value){
        String path=file+"/"+key; values.put("present/"+path,value!=null);if(value!=null)values.put("value/"+path,value);
    }
}
