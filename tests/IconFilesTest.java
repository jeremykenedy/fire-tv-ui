package com.jeremykenedy.firetv.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class IconFilesTest {
    private static final IconFiles.Cancelled RUN = () -> false;
    private static byte[] png() {
        byte[] bytes = new byte[24000];
        byte[] header = {(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82};
        System.arraycopy(header,0,bytes,0,header.length);
        ByteBuffer.wrap(bytes,16,8).putInt(1920).putInt(1080);
        return bytes;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void failure(Run run) throws Exception {
        try { run.run(); throw new AssertionError("Failure was accepted"); } catch (IOException expected) {}
    }
    interface Run { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        byte[] data = png();
        IconFiles.Entry entry = new IconFiles.Entry("one.png", data.length, IconFiles.digest(data));
        List<IconFiles.Entry> pack = Arrays.asList(entry);
        IconFiles.Source source = name -> new ByteArrayInputStream(data);
        IconFiles.Indexer index = (files, cancel) -> {
            for (File file : files) check(Arrays.equals(Files.readAllBytes(file.toPath()),data), "Only canonical files indexed");
        };
        Path target = root.resolve("no-access");
        IconFiles.Result denied = IconFiles.install(false,target.toFile(),pack,name -> {throw new AssertionError("No asset reads without access");},index,RUN);
        check(!denied.access && !Files.exists(target),"No writes without access");
        Path folder = root.resolve("normal");
        IconFiles.Result first = IconFiles.install(true,folder.toFile(),pack,source,index,RUN);
        check(first.copied==1 && first.indexed==1,"Initial copy and index");
        IconFiles.Result again = IconFiles.install(true,folder.toFile(),pack,name -> {throw new AssertionError("Existing canonical needs no copy");},index,RUN);
        check(again.copied==0 && again.preserved==1 && again.indexed==1,"Reindex existing canonical");
        Files.writeString(folder.resolve(entry.name),"owner-custom");
        IconFiles.Result custom = IconFiles.install(true,folder.toFile(),pack,source,(files,cancel)->check(files.isEmpty(),"Do not index custom files"),RUN);
        check(custom.preserved==1 && Files.readString(folder.resolve(entry.name)).equals("owner-custom"),"Keep custom bytes");
        Path race = root.resolve("race");
        IconFiles.Source racing = name -> {Files.writeString(race.resolve(name),"concurrent-owner");return new ByteArrayInputStream(data);};
        IconFiles.install(true,race.toFile(),pack,racing,(files,cancel)->check(files.isEmpty(),"Race file not canonical"),RUN);
        check(Files.readString(race.resolve(entry.name)).equals("concurrent-owner"),"Exclusive create preserves a racing owner file");
        Path corrupt = root.resolve("corrupt");
        failure(()->IconFiles.install(true,corrupt.toFile(),pack,name->new ByteArrayInputStream(new byte[data.length]),index,RUN));
        check(!Files.exists(corrupt.resolve(entry.name)),"Integrity rejection happens before file creation");
        Path sourceFailure = root.resolve("source-failure");
        failure(()->IconFiles.install(true,sourceFailure.toFile(),pack,name->{throw new IOException("read failed");},index,RUN));
        check(!Files.exists(sourceFailure.resolve(entry.name)),"Read failure leaves no partial");
        Path partial = root.resolve("partial");
        failure(()->IconFiles.install(true,partial.toFile(),pack,source,index,()->{
            try {return Files.exists(partial.resolve(entry.name)) && Files.size(partial.resolve(entry.name))>0;}catch(IOException e){return true;}
        }));
        check(Files.size(partial.resolve(entry.name))>0 && Files.size(partial.resolve(entry.name))<data.length,"Cancellation leaves its partial shared file without unsafe deletion");
        byte[] interruptedBytes=Files.readAllBytes(partial.resolve(entry.name));
        IconFiles.install(true,partial.toFile(),pack,source,(files,cancel)->check(files.isEmpty(),"Do not publish incomplete files"),RUN);
        check(Arrays.equals(interruptedBytes,Files.readAllBytes(partial.resolve(entry.name))),"Retry preserves incomplete or custom existing files for explicit owner recovery");
        Path replaced = root.resolve("replaced");
        failure(()->IconFiles.install(true,replaced.toFile(),pack,source,index,()->{
            Path file = replaced.resolve(entry.name);
            try {if(Files.exists(file) && Files.size(file)>0){Files.delete(file);Files.writeString(file,"replacement-custom");return true;}}catch(IOException e){throw new AssertionError(e);}
            return false;
        }));
        check(Files.readString(replaced.resolve(entry.name)).equals("replacement-custom"),"Cancel never deletes a replacement owner file");
        Path indexFailure = root.resolve("index-failure");
        failure(()->IconFiles.install(true,indexFailure.toFile(),pack,source,(files,cancel)->{throw new IOException("index unavailable");},RUN));
        check(Arrays.equals(Files.readAllBytes(indexFailure.resolve(entry.name)),data),"Keep completed bytes after index failure");
        check(IconFiles.install(true,indexFailure.toFile(),pack,source,index,RUN).indexed==1,"Retry publishes prior successful copy");
        Path cancelEarly = root.resolve("cancel-early");
        failure(()->IconFiles.install(true,cancelEarly.toFile(),pack,source,index,()->true));
        check(!Files.exists(cancelEarly),"Early cancellation writes nothing");
        failure(()->new IconFiles.Entry("../owner.png",data.length,entry.sha256));
        failure(()->IconFiles.install(true,root.resolve("duplicate").toFile(),Arrays.asList(entry,entry),source,index,RUN));
        System.out.println("Missing-only icon integrity, filesystem races, cancellation and retry passed");
    }
}
