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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.PluginManager;
import jenkins.model.Jenkins;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Data;
import hudson.util.EditDistance;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Installs a plugin either from a file, an URL, or from update center.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.331
 */
@Extension
public class InstallPluginCommand extends CLICommand {
    public String getShortDescription() {
        return Messages.InstallPluginCommand_ShortDescription();
    }

    @Argument(metaVar="SOURCE",required=true,usage="If this points to a local file, that file will be installed. " +
            "If this is an URL, Jenkins downloads the URL and installs that as a plugin." +
            "Otherwise the name is assumed to be the short name of the plugin in the existing update center (like \"findbugs\")," +
            "and the plugin will be installed from the update center.")
    public List<String> sources = new ArrayList<String>();

    @Option(name="-name",usage="If specified, the plugin will be installed as this short name (whereas normally the name is inferred from the source name automatically).")
    public String name;

    @Option(name="-restart",usage="Restart Jenkins upon successful installation.")
    public boolean restart;

    @Option(name="-deploy",usage="Deploy plugins right away without postponing them until the reboot.")
    public boolean dynamicLoad;

    protected int run() throws Exception {
        Jenkins h = Jenkins.getActiveInstance();
        h.checkPermission(PluginManager.UPLOAD_PLUGINS);
        PluginManager pm = h.getPluginManager();

        for (String source : sources) {
            // is this a file?
            if (channel!=null) {
                FilePath f = new FilePath(channel, source);
                if (f.exists()) {
                    stdout.println(Messages.InstallPluginCommand_InstallingPluginFromLocalFile(f));
                    if (name==null)
                        name = f.getBaseName();
                    f.copyTo(getTargetFilePath());
                    if (dynamicLoad)
                        pm.dynamicLoad(getTargetFile());
                    continue;
                }
            }

            // is this an URL?
            try {
                URL u = new URL(source);
                stdout.println(Messages.InstallPluginCommand_InstallingPluginFromUrl(u));
                if (name==null) {
                    name = u.getPath();
                    name = name.substring(name.lastIndexOf('/')+1);
                    name = name.substring(name.lastIndexOf('\\')+1);
                    int idx = name.lastIndexOf('.');
                    if (idx>0)  name = name.substring(0,idx);
                }
                getTargetFilePath().copyFrom(u);
                if (dynamicLoad)
                    pm.dynamicLoad(getTargetFile());
                continue;
            } catch (MalformedURLException e) {
                // not an URL
            }

            // is this a plugin the update center?
            UpdateSite.Plugin p = h.getUpdateCenter().getPlugin(source);
            if (p!=null) {
                stdout.println(Messages.InstallPluginCommand_InstallingFromUpdateCenter(source));
                Throwable e = p.deploy(dynamicLoad).get().getError();
                if (e!=null) {
                    AbortException myException = new AbortException("Failed to install plugin " + source);
                    myException.initCause(e);
                    throw myException;
                }
                continue;
            }

            stdout.println(Messages.InstallPluginCommand_NotAValidSourceName(source));

            if (!source.contains(".") && !source.contains(":") && !source.contains("/") && !source.contains("\\")) {
                // looks like a short plugin name. Why did we fail to find it in the update center?
                if (h.getUpdateCenter().getSites().isEmpty()) {
                    stdout.println(Messages.InstallPluginCommand_NoUpdateCenterDefined());
                } else {
                    Set<String> candidates = new HashSet<String>();
                    for (UpdateSite s : h.getUpdateCenter().getSites()) {
                        Data dt = s.getData();
                        if (dt==null)
                            stdout.println(Messages.InstallPluginCommand_NoUpdateDataRetrieved(s.getUrl()));
                        else
                            candidates.addAll(dt.plugins.keySet());
                    }
                    stdout.println(Messages.InstallPluginCommand_DidYouMean(source,EditDistance.findNearest(source,candidates)));
                }
            }

            throw new AbortException("Error occurred, see previous output.");
        }

        if (restart)
            h.safeRestart();
        return 0; // all success
    }

    private FilePath getTargetFilePath() {
        return new FilePath(getTargetFile());
    }

    private File getTargetFile() {
        return new File(Jenkins.getActiveInstance().getPluginManager().rootDir,name+".jpi");
    }
}
