package com.jeremykenedy.firetv.ui.nativeapp.widget.ambient;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

/** Selected document identity stays private and is outside portable/setup snapshots. */
public final class AmbientStore {
    private final AtomicFile file;
    public AmbientStore(Context context){file=new AtomicFile(new File(context.getNoBackupFilesDir(),"ambient-audio-v1.snapshot"));}
    public AmbientOptions read() throws IOException {
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return AmbientOptions.defaults();
        try(DataInputStream input=new DataInputStream(file.openRead())){
            if(input.readInt()!=0x46544141||input.readInt()!=1)throw new IOException("Unsupported saved ambient settings.");
            AmbientOptions value=new AmbientOptions(input.readUTF(),input.readInt(),input.readInt());
            if(input.read()!=-1)throw new IOException("Invalid saved ambient settings.");return value;
        }catch(IllegalArgumentException invalid){throw new IOException("Saved ambient settings could not be read. The original file was kept.",invalid);}
    }
    public void save(AmbientOptions options) throws IOException {
        FileOutputStream output=null;
        try{
            output=file.startWrite();DataOutputStream data=new DataOutputStream(output);
            data.writeInt(0x46544141);data.writeInt(1);data.writeUTF(options.uri);data.writeInt(options.volume);data.writeInt(options.stopSeconds);data.flush();file.finishWrite(output);
        }catch(IOException failed){if(output!=null)file.failWrite(output);throw failed;}
        AmbientOptions actual=read();
        if(!actual.uri.equals(options.uri)||actual.volume!=options.volume||actual.stopSeconds!=options.stopSeconds)throw new IOException("Ambient settings could not be verified.");
    }
}
