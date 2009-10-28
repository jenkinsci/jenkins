package hudson.cli;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Hudson;
import hudson.model.UpdateCenter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;

/**
 * Installs a plugin either from a file, an URL, or from update center.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.331
 */
@Extension
public class InstallPluginCommand extends CLICommand {
    public String getShortDescription() {
        return "Installs a plugin either from a file, an URL, or from update center";
    }

    @Argument(metaVar="SOURCE",required=true)
    public List<String> sources = new ArrayList<String>();

    @Option(name="-name",usage="If specified, the plugin will be installed as this short name (whereas normally the name is inferred from the source name automatically.)")
    public String name;

    @Option(name="-restart",usage="Restart Hudson upon successful installation")
    public boolean restart;

    protected int run() throws Exception {
        for (String source : sources) {
            // is this a file?
            FilePath f = new FilePath(channel, source);
            if (f.exists()) {
                stdout.println("Installing a plugin from local file: "+f);
                if (name==null)
                    name = f.getBaseName();
                f.copyTo(getTargetFile());
                continue;
            }

            // is this an URL?
            try {
                URL u = new URL(source);
                stdout.println("Installing a plugin from "+u);
                if (name==null) {
                    name = u.getPath();
                    name = name.substring(name.indexOf('/')+1);
                    name = name.substring(name.indexOf('\\')+1);
                    int idx = name.lastIndexOf('.');
                    if (idx>0)  name = name.substring(0,idx);
                }
                getTargetFile().copyFrom(u);
                continue;
            } catch (MalformedURLException e) {
                // not an URL
            }

            // is this a plugin the update center?
            UpdateCenter.Plugin p = Hudson.getInstance().getUpdateCenter().getPlugin(source);
            if (p!=null) {
                stdout.println("Installing "+source+" from update center");
                p.deploy().get();
                continue;
            }

            stdout.println(source+" is neither a valid file, URL, nor a plugin artifact name in the update center");
            return 1;
        }

        if (restart)
            Hudson.getInstance().restart();
        return 0; // all success
    }

    private FilePath getTargetFile() {
        return new FilePath(new File(Hudson.getInstance().getPluginManager().rootDir,name+".hpi"));
    }
}
