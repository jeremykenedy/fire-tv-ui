package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

/** Foreground-only playback. All MediaPlayer operations run on the main looper. */
public final class AmbientPlayback {
    public interface Listener { void status(String message); }
    public interface Probe { void finished(boolean supported); }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1));
    private final AmbientRun run=new AmbientRun();
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private CancellationSignal cancellation;
    private Probe probe;
    private int volume=50,timerSeconds;
    private boolean closed;
    private final Runnable timeout=new Runnable(){@Override public void run(){stop("Audio could not be prepared in time. The selected source was kept.");}};
    private final Runnable timer=new Runnable(){@Override public void run(){if(run.expired(SystemClock.elapsedRealtime()))stop("Stopped by the audio stop timer.");}};
    public AmbientPlayback(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;worker.allowCoreThreadTimeOut(true);}
    public boolean playing(){try{return run.playing()&&player!=null&&player.isPlaying();}catch(RuntimeException unavailable){return false;}}
    public void play(AmbientOptions options){prepare(options,true,null);}
    public void probe(AmbientOptions options,Probe callback){prepare(options,false,callback);}
    private void prepare(final AmbientOptions options,final boolean userPlay,Probe callback){
        stop(null);
        if(closed||options.uri.isEmpty()){listener.status("Choose an audio document first.");if(callback!=null)callback.finished(false);return;}
        probe=callback;volume=options.volume;timerSeconds=options.stopSeconds;
        final long token=run.begin(userPlay);cancellation=new CancellationSignal();final CancellationSignal signal=cancellation;
        listener.status(userPlay?"Preparing selected audio...":"Checking selected audio. Playback stays off.");
        main.postDelayed(timeout,15000);
        try{worker.execute(new Runnable(){@Override public void run(){
            AssetFileDescriptor opened=null;
            try{
                Uri uri=Uri.parse(options.uri);
                opened=context.getContentResolver().openAssetFileDescriptor(uri,"r",signal);
                if(opened==null||opened.getLength()>256L*1024*1024)throw new java.io.IOException();
                final AssetFileDescriptor source=opened;opened=null;
                main.post(new Runnable(){@Override public void run(){attach(source,token,userPlay);}});
            }catch(Exception failure){main.post(new Runnable(){@Override public void run(){if(run.current(token))stop("Selected audio is unavailable or unsupported. Your previous selection was kept.");}});}
            finally{if(opened!=null)try{opened.close();}catch(java.io.IOException ignored){}}
        }});}catch(RejectedExecutionException unavailable){stop("Audio provider is still busy. Try again after it responds.");}
    }
    private void attach(AssetFileDescriptor source,final long token,final boolean userPlay){
        try{
            if(!run.current(token)||closed)return;
            final MediaPlayer candidate=new MediaPlayer();player=candidate;
            candidate.setAudioAttributes(attributes());
            if(source.getDeclaredLength()<0)candidate.setDataSource(source.getFileDescriptor());
            else candidate.setDataSource(source.getFileDescriptor(),source.getStartOffset(),source.getDeclaredLength());
            candidate.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){@Override public void onPrepared(MediaPlayer value){
                if(!run.current(token)||player!=value)return;
                main.removeCallbacks(timeout);
                boolean audio=false;
                try{for(MediaPlayer.TrackInfo track:candidate.getTrackInfo()){
                    if(track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO){stop("Choose an audio-only document. The previous selection was kept.");return;}
                    if(track.getTrackType()==MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO)audio=true;
                }}catch(RuntimeException invalid){stop("Selected audio could not be verified. The previous selection was kept.");return;}
                if(!audio){stop("Selected document has no supported audio track. The previous selection was kept.");return;}
                if(!userPlay){Probe callback=probe;probe=null;stop(null);if(callback!=null)callback.finished(true);return;}
                if(!requestFocus(token)){stop("Audio focus was unavailable. Press Play when other audio has finished.");return;}
                try{
                    candidate.setVolume(volume/100f,volume/100f);
                    if(!run.started(token,SystemClock.elapsedRealtime(),timerSeconds)){stop(null);return;}
                    candidate.start();scheduleTimer();listener.status("Playing selected audio. Stop or leave this page to stop playback.");
                }catch(RuntimeException failed){stop("Selected audio could not start. The selection was kept.");}
            }});
            candidate.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){@Override public void onCompletion(MediaPlayer value){if(run.current(token))stop("Audio finished. Press Play to play again.");}});
            candidate.setOnErrorListener(new MediaPlayer.OnErrorListener(){@Override public boolean onError(MediaPlayer value,int what,int extra){if(run.current(token))stop("Selected audio is unavailable or unsupported. The selection was kept.");return true;}});
            candidate.prepareAsync();
        }catch(Exception failed){if(run.current(token))stop("Selected audio is unavailable or unsupported. The selection was kept.");}
        finally{try{source.close();}catch(java.io.IOException ignored){}}
    }
    private static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private boolean requestFocus(final long token){
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(manager==null)return false;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener(){@Override public void onAudioFocusChange(int change){
                    if(run.current(token)&&change!=AudioManager.AUDIOFOCUS_GAIN)stop("Stopped because another app needs audio. Press Play to restart.");
                }},main).build();
        try{return manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
        catch(RuntimeException unavailable){return false;}
    }
    public void volume(int value){
        if(value<0||value>100)throw new IllegalArgumentException();volume=value;
        if(player!=null&&run.playing())try{player.setVolume(value/100f,value/100f);}catch(RuntimeException failed){stop("Audio became unavailable. Press Play to retry.");}
    }
    public void timer(int seconds){timerSeconds=seconds;run.timer(SystemClock.elapsedRealtime(),seconds);scheduleTimer();}
    private void scheduleTimer(){main.removeCallbacks(timer);if(run.playing()&&timerSeconds>0)main.postDelayed(timer,timerSeconds*1000L);}
    public void stop(String message){
        run.stop();main.removeCallbacks(timeout);main.removeCallbacks(timer);
        if(cancellation!=null){cancellation.cancel();cancellation=null;}
        if(player!=null){player.release();player=null;}
        if(focus!=null){AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);try{if(manager!=null)manager.abandonAudioFocusRequest(focus);}catch(RuntimeException ignored){}focus=null;}
        Probe callback=probe;probe=null;if(callback!=null)callback.finished(false);
        if(message!=null)listener.status(message);
    }
    public void close(){closed=true;stop(null);worker.shutdownNow();}
}
