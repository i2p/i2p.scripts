package updatemonotone;

import java.io.File;

public class Main {
    
    public static void main(String[] args) {
        String[] modules = {
            "i2p","www",
            "scripts","Seedless",
            "i2p-bote",
            "plugins.jIRCii",
            "translator"
        };
        final File top = new File(new File(System.getenv("HOME"),"packages"),"mtn");
        top.mkdirs();
        Updater monotone = new Updater(top,prefix("i2p",modules),new Range(8996,8999));
        if(System.getenv("NOSYNC")==null) monotone.sync();
        monotone.checkout();
	System.exit(0);
    }

    private static String[] prefix(String string, String[] modules) {
        for(int i=0;i<modules.length;++i) {
            modules[i] = string + "." + modules[i];
        }
        return modules;
    }

}
