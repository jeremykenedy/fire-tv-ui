package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.view.*;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Emulator-only silent WAV fixture. Never changes the system stream volume. */
public final class AmbientInstrumentation extends Instrumentation {
    private Activity activity;
    private volatile int pickerMode;
    private volatile boolean documentIntent;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();final Context context=getTargetContext();
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"ambient-audio-v1.snapshot"));byte[] original=null;boolean stateCaptured=false;
        Set<Uri> grants=new HashSet<>();for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())grants.add(permission.getUri());
        final AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int streamVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        AudioFocusRequest interrupter=null;
        ActivityMonitor picker=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){
            if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){documentIntent="audio/*".equals(intent.getType())&&intent.hasCategory(Intent.CATEGORY_OPENABLE)&&(intent.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0;intent.setComponent(new ComponentName("proof.ambient.fixture","proof.ambient.fixture.PickerActivity"));intent.putExtra("fixture_mode",pickerMode);}return null;
        }};addMonitor(picker);
        try{
            if(file.getBaseFile().exists())try(InputStream input=file.openRead()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(bytes.size()+count>32768)throw new IOException("Unexpected original audio fixture size.");bytes.write(buffer,0,count);}original=bytes.toByteArray();}
            stateCaptured=true;
            for(Uri uri:grants)check(!"proof.ambient.fixture".equals(uri.getAuthority()),"Use a fresh audio fixture authority.");
            AmbientStore store=new AmbientStore(context);store.save(AmbientOptions.defaults());AmbientWidgetProvider widget=new AmbientWidgetProvider();
            check(!widget.defaultVisible()&&widget.state(context)==WidgetProvider.State.SETUP_REQUIRED,"Ambient audio starts hidden and needs explicit source setup.");
            open();check(!playing(),"Opening setup never starts playback.");click("choose");await("Audio selected.");
            check(documentIntent&&AmbientWidgetProvider.hasGrant(context,Uri.parse(store.read().uri)),"Explicit SAF source retains real durable read permission.");
            check(!playing()&&widget.state(context)==WidgetProvider.State.READY,"Source verification never autoplays.");
            String selected=store.read().uri;
            pickerMode=1;click("choose");await("Audio selection canceled.");check(selected.equals(store.read().uri),"Picker cancellation preserves selected source.");
            pickerMode=2;click("choose");await("Selected audio is unavailable");check(selected.equals(store.read().uri),"Invalid audio cannot replace valid selection.");
            click("play");await("Playing selected audio.");check(playing(),"Actual MediaPlayer starts only after Play.");
            click("quieter");check(store.read().volume==40&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)==streamVolume,"Local player volume persists without changing TV stream volume.");
            click("stop");check(!playing(),"Stop releases audible playback.");
            click("play");await("Playing selected audio.");
            interrupter=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener(){@Override public void onAudioFocusChange(int change){}},new Handler(Looper.getMainLooper())).build();
            check(audio.requestAudioFocus(interrupter)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED,"Fixture audio focus is granted.");await("Stopped because another app needs audio.");check(!playing(),"Audio focus loss stops playback.");audio.abandonAudioFocusRequest(interrupter);interrupter=null;SystemClock.sleep(200);check(!playing(),"Focus regain never auto-resumes.");
            store.save(store.read().timer(1));finishPage();open();check(!playing(),"Recreated page remains stopped.");click("play");await("Playing selected audio.");await("Stopped by the audio stop timer.");check(!playing(),"Actual stop timer releases playback.");
            store.save(store.read().timer(0));finishPage();open();click("play");await("Playing selected audio.");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();check(!playing(),"Leaving the audio Activity releases playback.");
            open();check(!playing(),"Reopening selected audio never autoplays.");
            context.getContentResolver().releasePersistableUriPermission(Uri.parse(store.read().uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            check(widget.state(context)==WidgetProvider.State.UNAVAILABLE&&selected.equals(store.read().uri),"Revoked access is unavailable without deleting the selection.");
            result.putString("result","PASS: silent native SAF selection, persisted grants, cancel/invalid preservation, no autoplay, MediaPlayer Play/Stop/local volume, audio focus loss/no resume, stop timer and lifecycle release.");
        }catch(Throwable failure){result.putString("result","FAIL: "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally{
            try{
                finishPage();if(interrupter!=null)audio.abandonAudioFocusRequest(interrupter);removeMonitor(picker);
                if(stateCaptured){if(original==null)file.delete();else{FileOutputStream output=file.startWrite();output.write(original);file.finishWrite(output);}}
                for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())if(!grants.contains(permission.getUri())&&"proof.ambient.fixture".equals(permission.getUri().getAuthority()))context.getContentResolver().releasePersistableUriPermission(permission.getUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
                check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==streamVolume,"System volume was preserved.");
                if(stateCaptured&&original==null)check(!file.getBaseFile().exists(),"Original absent private audio state restored.");else if(stateCaptured)try(InputStream input=file.openRead()){for(byte item:original)check(input.read()==(item&255),"Original private audio state restored.");check(input.read()==-1,"Original private audio length restored.");}
                Set<Uri> restored=new HashSet<>();for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())restored.add(permission.getUri());check(restored.equals(grants),"Exact original URI grants restored.");result.putString("restoration",stateCaptured?"verified private audio state, URI grants and system stream volume":"private audio state left untouched because capture did not complete; URI grants and system volume verified");
            }catch(Throwable restore){result.putString("result","FAIL: ambient fixture restoration could not be verified: "+restore.getClass().getSimpleName());}
        }
        finish(result.getString("result").startsWith("PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void open(){activity=startActivitySync(new Intent(getTargetContext(),AmbientAudioActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void finishPage(){if(activity!=null){final Activity old=activity;runOnMainSync(new Runnable(){@Override public void run(){old.finish();}});waitForIdleSync();}}
    private void click(final String key){runOnMainSync(new Runnable(){@Override public void run(){View button=activity.getWindow().getDecorView().findViewWithTag("ambient:"+key);check(button!=null,"Native audio action exists.");button.requestFocus();}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();}
    private void await(final String prefix){long deadline=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<deadline){final boolean[] found={false};runOnMainSync(new Runnable(){@Override public void run(){TextView status=(TextView)activity.getWindow().getDecorView().findViewWithTag("ambient:status");found[0]=status.getText().toString().startsWith(prefix);}});if(found[0])return;SystemClock.sleep(50);}throw new AssertionError("Native audio state unavailable: "+prefix);}
    private boolean playing()throws Exception{final boolean[] found={false};Field field=AmbientAudioActivity.class.getDeclaredField("playback");field.setAccessible(true);final AmbientPlayback playback=(AmbientPlayback)field.get(activity);runOnMainSync(new Runnable(){@Override public void run(){found[0]=playback.playing();}});return found[0];}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
