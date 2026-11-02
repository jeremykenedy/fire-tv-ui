package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.*;

public final class AmbientWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM=new LayoutItem("ambient-audio",LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item(){return ITEM;}
    @Override public String title(){return "Ambient audio";}
    @Override public State state(Context context){
        try{AmbientOptions value=new AmbientStore(context).read();if(value.uri.isEmpty())return State.SETUP_REQUIRED;return hasGrant(context,Uri.parse(value.uri))?State.READY:State.UNAVAILABLE;}
        catch(Exception invalid){return State.UNAVAILABLE;}
    }
    @Override public String message(Context context){State state=state(context);return state==State.SETUP_REQUIRED?"Choose your own audio. Playback never starts automatically.":state==State.UNAVAILABLE?"Saved audio access is unavailable. Open to reconnect; the selection was kept.":"Selected audio is checked when you press Play. Playback stops when the audio page closes.";}
    @Override public int widthDp(){return 360;}
    @Override public int heightDp(){return 200;}
    @Override public View createView(Context context){return new TextView(context);}
    @Override public void configure(Activity activity){activity.startActivity(new Intent(activity,AmbientAudioActivity.class));}
    @Override public void onStart(View view){if(view instanceof TextView){TextView text=(TextView)view;text.setText(title()+"\n"+message(view.getContext()));text.setTextColor(0xffffffff);text.setTextSize(18);text.setPadding(16,16,16,16);}}
    @Override public void onStop(View view){}
    public static boolean hasGrant(Context context,Uri uri){for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())if(permission.isReadPermission()&&uri.equals(permission.getUri()))return true;return false;}
}
