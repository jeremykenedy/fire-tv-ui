package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

/** A source check, restored page or focus gain can never become a Play request. */
public final class AmbientRun {
    private long generation, stopAt;
    private boolean requested, playing;
    public long begin(boolean userPlay){generation++;requested=userPlay;playing=false;stopAt=0;return generation;}
    public boolean current(long token){return token==generation;}
    public boolean mayStart(long token){return current(token)&&requested;}
    public boolean started(long token,long now,int seconds){
        if(!mayStart(token)||now<0||seconds<0||seconds>86400)return false;
        requested=false;playing=true;timer(now,seconds);return true;
    }
    public void timer(long now,int seconds){if(now<0||seconds<0||seconds>86400)throw new IllegalArgumentException();stopAt=playing&&seconds>0?Math.addExact(now,seconds*1000L):0;}
    public boolean expired(long now){return playing&&stopAt>0&&now>=stopAt;}
    public boolean playing(){return playing;}
    public void stop(){generation++;requested=false;playing=false;stopAt=0;}
}
