package com.jeremykenedy.firetv.ui.seed;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import com.jeremykenedy.firetv.ui.nativeapp.migration.JournalFixture;

/** Provider-free seed. Refuses any existing synthetic lab data. */
public final class SeedInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try {
        Context context=getTargetContext();check("com.jeremykenedy.firetv.ui".equals(context.getPackageName()),"Unexpected package");
        for(String name:new String[]{"shared_prefs","files","no_backup"}) { File dir=new File(context.getDataDir(),name);String[] files=dir.list();check(!dir.exists()||(dir.isDirectory()&&files!=null&&files.length==0),"Nonempty lab root refused: "+name); }
        check(context.getSharedPreferences("source_appearance",0).edit().putString("base","stars").putString("base_palette","christmas").putBoolean("base_frozen",true).putString("scene","").putString("fixture_unrelated","keep").commit(),"Appearance seed failed");
        check(context.getSharedPreferences("native_focus",0).edit().putInt("border_rgb",0xffffff).putBoolean("border_enabled",true).putInt("border_opacity",100).putBoolean("fill_enabled",false).commit(),"Focus seed failed");
        check(context.getSharedPreferences("native_icon_packs",0).edit().putString("package","builtin:holiday:christmas").commit(),"Pack seed failed");
        JournalFixture.seed(context);
        File marker=new File(context.getNoBackupFilesDir(),"source-startup-v1");try(FileOutputStream output=new FileOutputStream(marker)){output.write(1);output.getFD().sync();}
        check(marker.isFile()&&new File(context.getNoBackupFilesDir(),"holiday-preset-v1.snapshot").isFile(),"Seed readback failed");
        result.putString("result","PASS: synthetic partial holiday transaction and journal seeded");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
