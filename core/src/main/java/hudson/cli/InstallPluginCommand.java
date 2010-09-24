/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.cli;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Hudson;
import hudson.model.UpdateSite;
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

    @Argument(metaVar="SOURCE",required=true,usage="If this points to a local file, that file will be installed. " +
            "If this is an URL, Hudson downloads the URL and installs that as a plugin." +
            "Otherwise the name is assumed to be the short name of the plugin in the existing update center (like \"findbugs\")," +
            "and the plugin will be installed from the update center")
    public List<String> sources = new ArrayList<String>();

    @Option(name="-name",usage="If specified, the plugin will be installed as this short name (whereas normally the name is inferred from the source name automatically.)")
    public String name;

    @Option(name="-restart",usage="Restart Hudson upon successful installation")
    public boolean restart;

    protected int run() throws Exception {
        Hudson h = Hudson.getInstance();
        h.checkPermission(Hudson.ADMINISTER);

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
            UpdateSite.Plugin p = h.getUpdateCenter().getPlugin(source);
            if (p!=null) {
                stdout.println("Installing "+source+" from update center");
                p.deploy().get();
                continue;
            }

            stdout.println(source+" is neither a valid file, URL, nor a plugin artifact name in the update center");
            return 1;
        }

        if (restart)
            h.restart();
        return 0; // all success
    }

    private FilePath getTargetFile() {
        return new FilePath(new File(Hudson.getInstance().getPluginManager().rootDir,name+".hpi"));
    }
}
