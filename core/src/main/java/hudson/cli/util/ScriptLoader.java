package hudson.cli.util;

import hudson.AbortException;
import hudson.remoting.Callable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * 
 * @author vjuranek
 *
 */
public class ScriptLoader implements Callable<String,IOException> {
    
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
        InputStream s = url.openStream();
        try {
            return IOUtils.toString(s);
        } finally {
            s.close();
        }
    }

}
