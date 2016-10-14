package hudson.cli.util;

import hudson.AbortException;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Reads a file (either a path or URL) over a channel.
 *
 * @author vjuranek
 */
public class ScriptLoader extends MasterToSlaveCallable<String,IOException> {
    
    private final String script;
    
    public ScriptLoader(String script){
        this.script = script;
    }
    
    public String call() throws IOException {
        File f = new File(script);
        if(f.exists())
            return FileUtils.readFileToString(f);

        URL url;
        try {
            url = new URL(script);
        } catch (MalformedURLException e) {
            throw new AbortException("Unable to find a script "+script);
        }
        try (InputStream s = url.openStream()) {
            return IOUtils.toString(s);
        }
    }
}
