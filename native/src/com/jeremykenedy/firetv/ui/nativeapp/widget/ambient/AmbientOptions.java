package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

public final class AmbientOptions {
    public final String uri;
    public final int volume, stopSeconds;
    public AmbientOptions(String uri,int volume,int stopSeconds) {
        if(uri==null||uri.length()>4096||!uri.isEmpty()&&!uri.matches("content://[^\\s?#]+(?:\\?[^\\s#]*)?"))throw new IllegalArgumentException("Choose an Android audio document.");
        if(volume<0||volume>100||stopSeconds<0||stopSeconds>86400)throw new IllegalArgumentException("Invalid ambient playback settings.");
        this.uri=uri;this.volume=volume;this.stopSeconds=stopSeconds;
    }
    public static AmbientOptions defaults(){return new AmbientOptions("",50,0);}
    public AmbientOptions source(String uri){return new AmbientOptions(uri,volume,stopSeconds);}
    public AmbientOptions volume(int volume){return new AmbientOptions(uri,volume,stopSeconds);}
    public AmbientOptions timer(int seconds){return new AmbientOptions(uri,volume,seconds);}
}
