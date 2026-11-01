package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;

public final class AndroidIconsTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fail(Run run)throws Exception{try{run.run();throw new AssertionError("Expected index failure");}catch(IOException expected){}}
    interface Run{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        AssetManager.root=new File(args[0]);
        int iconCount=new JSONArray(Files.readString(AssetManager.root.toPath().resolve("input-icons/catalog.json"))).length();
        check(iconCount>0,"Catalog contains icons");
        Context context=new Context();
        for(int api:new int[]{28,30}){
            Build.VERSION.SDK_INT=api;context.allowed=false;Environment.allowed=false;
            Environment.root=new File(args[1],"no-access-"+api);
            int before=AssetManager.reads;
            check(!BundledIcons.install(context,1000).access,"Respect existing storage denial");
            check(AssetManager.reads==before&&!Environment.root.exists(),"No denied asset reads or shared writes");
            context.allowed=true;Environment.allowed=true;
            Environment.root=new File(args[1],"normal-"+api);
            ContentResolver.pending.clear();ContentResolver.updates=0;
            IconFiles.Result result=BundledIcons.install(context,5000);
            check(result.copied==iconCount&&result.indexed==iconCount,"All bundled canonical files published");
            check(ContentResolver.updates==(api==28?0:iconCount),"Pending publication only where supported");
            Path icons=Environment.root.toPath().resolve("Pictures/Icons");
            check(Files.list(icons).count()==iconCount,"Only canonical files added");
            Files.writeString(icons.resolve("pc.png"),"owner-custom");
            MediaScannerConnection.paths.clear();
            result=BundledIcons.install(context,5000);
            check(result.copied==0&&result.preserved==iconCount&&result.indexed==iconCount-1,"Keep custom and reindex exact existing");
            check(Files.readString(icons.resolve("pc.png")).equals("owner-custom"),"Custom file unchanged");
            for(String path:MediaScannerConnection.paths)check(!path.endsWith("/pc.png"),"Custom owner row not published");
        }
        Environment.root=new File(args[1],"wrong-index");
        ContentResolver.wrongPath=true;
        fail(()->BundledIcons.install(context,5000));
        ContentResolver.wrongPath=false;
        IconFiles.Result retry=BundledIcons.install(context,5000);
        check(retry.copied==0&&retry.indexed==iconCount,"Wrong provider row never accepted; safe retry indexes existing copies");
        MediaScannerConnection.noCallbacks=true;
        fail(()->BundledIcons.install(context,40));
        MediaScannerConnection.noCallbacks=false;
        check(BundledIcons.install(context,5000).indexed==iconCount,"Timeout does not destroy data or prevent retry");
        System.out.println("Android28/30 existing permissions, MediaStore columns/publication, identity and retry passed");
    }
}
