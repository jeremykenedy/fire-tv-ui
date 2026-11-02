package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;

public final class RadarTest {
    private static final long NOW = 1800000000000L;
    private static final RadarSource SOURCE = RadarSource.find("station:KRTX");
    public static void main(String[] args) throws Exception {
        check(RadarSource.REGIONS.size() == 16 && RadarSource.STATIONS.size() == 156, "Verified standard-image coverage");
        check(SOURCE.imageUrl().equals("https://radar.weather.gov/ridge/standard/KRTX_0.gif"), "Exact official fixed station URL");
        check(RadarSource.find("station:../../etc/passwd") == null && RadarSource.find("https://attacker.invalid/") == null, "URLs and traversal never accepted as area IDs");
        check(RadarSource.find("region:CONUS-LARGE") == null, "Oversized product not offered");
        policy(); http(); cache(); repository();
        System.out.println("Radar source, freshness, bounds, cache, conditional requests, offline and cancellation checks passed");
    }
    private static void policy() {
        RadarCache.Entry current = new RadarCache.Entry(SOURCE, gif(600,550), NOW, NOW-60000, "tag");
        check(RadarSnapshot.show(SOURCE,current,NOW,false).state==RadarSnapshot.State.READY,"Recent source file and download");
        check(RadarSnapshot.show(SOURCE,current,NOW,true).state==RadarSnapshot.State.STALE,"Failed refresh is visibly stale");
        RadarCache.Entry unknown = new RadarCache.Entry(SOURCE,gif(600,550),NOW,0,"");
        check(RadarSnapshot.show(SOURCE,unknown,NOW,false).state==RadarSnapshot.State.TIMESTAMP_UNKNOWN,"Missing file time never called current");
        RadarCache.Entry old = new RadarCache.Entry(SOURCE,gif(600,550),NOW,NOW-20*60000,"");
        check(RadarSnapshot.show(SOURCE,old,NOW,false).state==RadarSnapshot.State.STALE,"New download cannot disguise old source file");
        check(RadarSnapshot.show(SOURCE,current,NOW+RadarCache.MAX_AGE_MILLIS,true).image==null,"Hard-expired image is withheld");
        check(RadarSnapshot.show(SOURCE,current,NOW-1,false).image==null,"Clock rollback does not make a future download fresh");
        check(RadarSnapshot.show(SOURCE,current,NOW+4*60000,false).refreshAt<=NOW+5*60000,"Opening cache does not postpone five-minute refresh");
    }
    private static void http() throws Exception {
        final Fake connection = new Fake(200,gif(600,550));
        RadarHttp client = new RadarHttp(new RadarHttp.Connections(){public HttpURLConnection open(URL url){check(url.getHost().equals("radar.weather.gov"),"Official host only");return connection;}});
        RadarCache.Entry first = client.get(SOURCE,null,NOW,new RadarRequest());
        check(first.bytes.length==10 && connection.disconnected && !connection.getInstanceFollowRedirects(),"Bounded image accepted and connection closed without redirect following");
        connection.code=304;
        RadarCache.Entry reused=client.get(SOURCE,first,NOW+60000,new RadarRequest());
        check(reused.modifiedAt==first.modifiedAt&&reused.fetchedAt==NOW+60000,"304 updates fetch time without inventing source time");
        check("tag".equals(connection.getRequestProperty("If-None-Match")),"Conditional ETag used");
        connection.code=200;connection.control="max-age=111";connection.age="11";
        check(client.get(SOURCE,null,NOW,new RadarRequest()).freshUntil==NOW+100000,"Server max-age and Age bound cache freshness");
        connection.control="no-store";connection.age=null;
        check(!client.get(SOURCE,null,NOW,new RadarRequest()).cacheable,"No-store is respected");
        connection.control=null;
        connection.code=302;
        expectFailure(client,"Redirect refused");
        connection.code=200;connection.body=gif(5000,5000);
        expectFailure(client,"Oversized decoded image refused");
        connection.body=new byte[RadarCache.MAX_BYTES+1];
        expectFailure(client,"Oversized encoded response refused");
        connection.body=gif(600,550);connection.type="text/html";
        expectFailure(client,"Non-image response refused");
        RadarRequest cancelled=new RadarRequest();cancelled.cancel();
        try {client.get(SOURCE,null,NOW,cancelled);throw new AssertionError("Cancelled work rejected");}catch(CancellationException expected){}
    }
    private static void expectFailure(RadarHttp client,String message)throws Exception{try{client.get(SOURCE,null,NOW,new RadarRequest());throw new AssertionError(message);}catch(IOException expected){}}
    private static void cache() throws Exception {
        File folder=Files.createTempDirectory("radar-cache-proof").toFile();
        try {
            RadarCache cache=new RadarCache(folder);
            cache.write(new RadarCache.Entry(SOURCE,gif(600,550),NOW,NOW-60000,"tag"));
            RadarCache.Entry restored=new RadarCache(folder).read(SOURCE);
            check(restored!=null&&restored.modifiedAt==NOW-60000,"Cache survives repository restart with original metadata");
            cache.write(new RadarCache.Entry(SOURCE,gif(600,550),NOW,NOW-60000,"tag",NOW,false));
            check(cache.read(SOURCE)==null&&new RadarCache(folder).read(SOURCE)==null,"No-store removes retained body from both memory and disk");
            cache.write(new RadarCache.Entry(SOURCE,gif(600,550),NOW,NOW-60000,"tag"));
            cache.write(new RadarCache.Entry(RadarSource.REGIONS.get(0),gif(600,550),NOW,0,""));
            cache.write(new RadarCache.Entry(RadarSource.REGIONS.get(1),gif(600,550),NOW,0,""));
            check(folder.listFiles().length<=2,"Disk cache limited to two images");
        } finally {for(File file:folder.listFiles())file.delete();folder.delete();}
    }
    private static void repository() throws Exception {
        File folder=Files.createTempDirectory("radar-read-proof").toFile();
        final int[] requests={0}; final Fake connection=new Fake(200,gif(600,550));
        RadarHttp http=new RadarHttp(new RadarHttp.Connections(){public HttpURLConnection open(URL url){requests[0]++;return connection;}});
        try {
            RadarRepository repo=new RadarRepository(folder,http);
            check(repo.read(SOURCE,NOW,new RadarRequest()).state==RadarSnapshot.State.READY,"First request fetches source");
            repo.read(SOURCE,NOW+1000,new RadarRequest());check(requests[0]==1,"Repeated visible reload does not refetch");
            connection.code=503;
            check(repo.read(SOURCE,NOW+6*60000,new RadarRequest()).state==RadarSnapshot.State.STALE,"Offline keeps eligible cached image with stale state");
            repo.read(SOURCE,NOW+6*60000+1000,new RadarRequest());check(requests[0]==2,"Failed source retry limited to at least one minute");
            check(repo.read(SOURCE,NOW+7*3600000L,new RadarRequest()).state==RadarSnapshot.State.OFFLINE,"Expired offline image is not displayed");
        } finally {for(File file:folder.listFiles())file.delete();folder.delete();}
    }
    private static byte[] gif(int width,int height){return new byte[]{'G','I','F','8','9','a',(byte)width,(byte)(width>>8),(byte)height,(byte)(height>>8)};}
    private static final class Fake extends HttpURLConnection {
        int code;byte[] body;String type="image/gif",control,age;boolean disconnected;
        Fake(int code,byte[]body)throws Exception{super(new URL("https://radar.weather.gov/ridge/standard/KRTX_0.gif"));this.code=code;this.body=body;}
        @Override public int getResponseCode(){return code;}
        @Override public String getContentType(){return type;}
        @Override public long getContentLengthLong(){return body.length;}
        @Override public String getHeaderField(String key){return key.equals("ETag")?"tag":key.equals("Cache-Control")?control:key.equals("Age")?age:null;}
        @Override public long getLastModified(){return NOW-60000;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
