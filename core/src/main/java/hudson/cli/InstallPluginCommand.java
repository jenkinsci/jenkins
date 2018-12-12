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
import hudson.util.VersionNumber;
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
import org.apache.commons.io.FileUtils;

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

    @Argument(metaVar="SOURCE",required=true,usage="If this points to a local file (‘-remoting’ mode only), that file will be installed. " +
            "If this is an URL, Jenkins downloads the URL and installs that as a plugin. " +
            "If it is the string ‘=’, the file will be read from standard input of the command, and ‘-name’ must be specified. " +
            "Otherwise the name is assumed to be the short name of the plugin in the existing update center (like ‘findbugs’), " +
            "and the plugin will be installed from the update center. If the short name includes a minimum version number " +
            "(like ‘findbugs:1.4’), and there are multiple update centers publishing different versions, the update centers " +
            "will be searched in order for the first one publishing a version that is at least the specified version.")
    public List<String> sources = new ArrayList<String>();

    @Option(name="-name",usage="If specified, the plugin will be installed as this short name (whereas normally the name is inferred from the source name automatically).")
    public String name; // TODO better to parse out Short-Name from the manifest and deprecate this option

    @Option(name="-restart",usage="Restart Jenkins upon successful installation.")
    public boolean restart;

    @Option(name="-deploy",usage="Deploy plugins right away without postponing them until the reboot.")
    public boolean dynamicLoad;

    protected int run() throws Exception {
        Jenkins h = Jenkins.getActiveInstance();
        h.checkPermission(PluginManager.UPLOAD_PLUGINS);
        PluginManager pm = h.getPluginManager();

        if (sources.size() > 1 && name != null) {
            throw new IllegalArgumentException("-name is incompatible with multiple sources");
        }

        for (String source : sources) {
            if (source.equals("=")) {
                if (name == null) {
                    throw new IllegalArgumentException("-name required when using -source -");
                }
                stdout.println(Messages.InstallPluginCommand_InstallingPluginFromStdin());
                File f = getTargetFile(name);
                FileUtils.copyInputStreamToFile(stdin, f);
                if (dynamicLoad) {
                    pm.dynamicLoad(f);
                }
                continue;
            }

            // is this a file?
            if (channel!=null) {
                FilePath f = new FilePath(channel, source);
                if (f.exists()) {
                    stdout.println(Messages.InstallPluginCommand_InstallingPluginFromLocalFile(f));
                    String n = name != null ? name : f.getBaseName();
                    f.copyTo(getTargetFilePath(n));
                    if (dynamicLoad)
                        pm.dynamicLoad(getTargetFile(n));
                    continue;
                }
            }

            // is this an URL?
            try {
                URL u = new URL(source);
                stdout.println(Messages.InstallPluginCommand_InstallingPluginFromUrl(u));
                String n;
                if (name != null) {
                    n = name;
                } else {
                    n = u.getPath();
                    n = n.substring(n.lastIndexOf('/') + 1);
                    n = n.substring(n.lastIndexOf('\\') + 1);
                    int idx = n.lastIndexOf('.');
                    if (idx > 0) {
                        n = n.substring(0, idx);
                    }
                }
                getTargetFilePath(n).copyFrom(u);
                if (dynamicLoad)
                    pm.dynamicLoad(getTargetFile(n));
                continue;
            } catch (MalformedURLException e) {
                // not an URL
            }

            // is this a plugin the update center?
            int index = source.lastIndexOf(':');
            UpdateSite.Plugin p;
            if (index == -1) {
                p = h.getUpdateCenter().getPlugin(source);
            } else {
                // try to find matching min version number
                VersionNumber version = new VersionNumber(source.substring(index + 1));
                p = h.getUpdateCenter().getPlugin(source.substring(0,index), version);
                if (p == null) {
                    p = h.getUpdateCenter().getPlugin(source);
                }
            }
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

    private static FilePath getTargetFilePath(String name) {
        return new FilePath(getTargetFile(name));
    }

    private static File getTargetFile(String name) {
        return new File(Jenkins.getActiveInstance().getPluginManager().rootDir,name+".jpi");
    }
}
