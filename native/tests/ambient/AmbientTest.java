package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

public final class AmbientTest {
    private static int checks;
    public static void main(String[] args){
        AmbientOptions defaults=AmbientOptions.defaults();
        check(defaults.uri.isEmpty()&&defaults.volume==50&&defaults.stopSeconds==0,"Empty defaults never imply playback");
        AmbientOptions selected=defaults.source("content://fixture.audio/document/one").volume(20).timer(300);
        check(selected.uri.equals("content://fixture.audio/document/one")&&selected.volume==20&&selected.stopSeconds==300,"Independent source/volume/timer choices");
        for(String uri:new String[]{"https://example.org/audio.mp3","file:///private/audio.mp3","content://","content://invalid source"})reject(uri,50,0);
        reject("",-1,0);reject("",101,0);reject("",50,-1);reject("",50,86401);
        AmbientRun run=new AmbientRun();long probe=run.begin(false);
        check(!run.mayStart(probe)&&!run.started(probe,1000,0)&&!run.playing(),"Document validation never starts audio");
        long play=run.begin(true);check(run.mayStart(play)&&run.started(play,1000,5),"Only an explicit Play request starts");
        check(!run.expired(5999)&&run.expired(6000),"Stop deadline uses exact monotonic milliseconds");
        run.stop();check(!run.playing()&&!run.mayStart(play)&&!run.started(play,10000,0),"Stop/lifecycle/focus loss invalidates stale preparation");
        long next=run.begin(true);check(!run.current(play)&&run.current(next),"Source replacement ignores stale callbacks");
        run.started(next,1000,0);check(!run.expired(Long.MAX_VALUE),"Off means no timer");
        run.timer(2000,10);check(!run.expired(11999)&&run.expired(12000),"Explicit timer change starts from current monotonic time");
        run.stop();long reopened=run.begin(false);check(!run.mayStart(reopened),"Reopen/focus gain cannot synthesize Play");
        System.out.println("Ambient audio model checks passed: "+checks);
    }
    private static void reject(String uri,int volume,int seconds){try{new AmbientOptions(uri,volume,seconds);throw new AssertionError("Invalid audio options accepted");}catch(IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
