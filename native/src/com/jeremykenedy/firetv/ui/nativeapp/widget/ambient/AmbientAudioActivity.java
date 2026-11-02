package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.io.IOException;

public final class AmbientAudioActivity extends Activity {
    private boolean startupBlocked;
    private static final int PICK_AUDIO=63;
    private AmbientOptions options;
    private AmbientStore store;
    private AmbientPlayback playback;
    private TextView status,details;
    private boolean visible;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }store=new AmbientStore(this);
        LinearLayout page=Pages.page(this);
        page.addView(Pages.text(this,"Ambient audio",28));
        page.addView(Pages.text(this,"Choose your own supported audio document. Nothing plays automatically. Playback stops when this page closes or another app needs audio. Source access and settings stay private on this TV.",17));
        details=Pages.text(this,"",17);page.addView(details);
        status=Pages.text(this,"",17);status.setTag("ambient:status");page.addView(status);
        playback=new AmbientPlayback(this,new AmbientPlayback.Listener(){@Override public void status(String message){status.setText(message);}});
        try{options=store.read();status.setText(options.uri.isEmpty()?"Choose an audio document to set up ambient audio.":"Selected audio is stopped. Press Play to check and play it.");}
        catch(IOException invalid){options=AmbientOptions.defaults();status.setText("Saved settings could not be read. The file was kept. Choose new audio to replace it.");}
        ScrollView scroll=new ScrollView(this);LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);scroll.addView(controls);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add(controls,"choose","Choose audio document",new Runnable(){@Override public void run(){choose();}});
        add(controls,"play","Play",new Runnable(){@Override public void run(){playback.play(options);}});
        add(controls,"stop","Stop",new Runnable(){@Override public void run(){playback.stop("Stopped. Press Play to start again.");}});
        add(controls,"quieter","Quieter",new Runnable(){@Override public void run(){change(options.volume(Math.max(0,options.volume-10)));}});
        add(controls,"louder","Louder",new Runnable(){@Override public void run(){change(options.volume(Math.min(100,options.volume+10)));}});
        add(controls,"timer","Stop timer",new Runnable(){@Override public void run(){timer();}});
        add(controls,"forget","Forget selected audio",new Runnable(){@Override public void run(){playback.stop(null);if(change(options.source("")))status.setText("Audio selection removed. Document grants used by other features were preserved.");}});
        update();controls.findViewWithTag("ambient:"+(options.uri.isEmpty()?"choose":"play")).requestFocus();
    }
    @Override protected void onStart(){
        if (startupBlocked) { super.onStart(); return; }super.onStart();visible=true;}
    @Override protected void onStop(){
        if (startupBlocked) { super.onStop(); return; }visible=false;playback.stop("Stopped because the audio page left the foreground.");super.onStop();}
    @Override protected void onDestroy(){
        if (startupBlocked) { super.onDestroy(); return; }playback.close();super.onDestroy();}
    private void add(LinearLayout controls,String key,String label,final Runnable action){Button button=Pages.button(this,label,new View.OnClickListener(){@Override public void onClick(View view){action.run();}});button.setTag("ambient:"+key);controls.addView(button);}
    private void update(){details.setText((options.uri.isEmpty()?"No audio selected":"One private audio document selected")+" · Local volume "+options.volume+"% · "+(options.stopSeconds==0?"No stop timer":"Stop after "+options.stopSeconds/60+" minutes from Play or timer change"));}
    private boolean change(AmbientOptions next){
        try{boolean timerChanged=options.stopSeconds!=next.stopSeconds;store.save(next);options=next;playback.volume(next.volume);if(timerChanged)playback.timer(next.stopSeconds);update();return true;}
        catch(IOException failed){status.setText("Ambient settings could not be saved. Previous choices were kept.");return false;}
    }
    private void timer(){
        final int[] seconds={0,300,900,1800,3600};
        new AlertDialog.Builder(this).setTitle("Audio stop timer").setItems(new String[]{"Off","5 minutes","15 minutes","30 minutes","1 hour"},new DialogInterface.OnClickListener(){@Override public void onClick(DialogInterface dialog,int which){change(options.timer(seconds[which]));}}).setNegativeButton("Cancel",null).show();
    }
    private void choose(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_AUDIO);}catch(RuntimeException unavailable){status.setText("No compatible audio document picker is available.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        if (startupBlocked) { super.onActivityResult(request, result, data); return; }
        super.onActivityResult(request,result,data);if(request!=PICK_AUDIO)return;
        if(result!=RESULT_OK){status.setText("Audio selection canceled. Previous selection was kept.");return;}
        final Uri uri=data==null?null:data.getData();
        if(uri==null||!"content".equals(uri.getScheme())||uri.getAuthority()==null
                ||(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0||(data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0){status.setText("Choose an audio document with durable read access. Previous selection was kept.");return;}
        final boolean alreadyGranted=AmbientWidgetProvider.hasGrant(this,uri);
        final AmbientOptions selected;
        try{
            selected=options.source(uri.toString());
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(RuntimeException denied){status.setText("Audio document access could not be retained. Previous selection was kept.");return;}
        playback.probe(selected,new AmbientPlayback.Probe(){@Override public void finished(boolean supported){
            if(supported&&visible){
                try{store.save(selected);options=selected;update();status.setText("Audio selected. Playback is off until you press Play.");return;}
                catch(IOException failed){status.setText("Audio selection could not be saved. Previous selection was kept.");}
            }
            if(!alreadyGranted)try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(RuntimeException ignored){}
        }});
    }
}
