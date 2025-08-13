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
import hudson.PluginManager;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Data;
import hudson.util.EditDistance;
import hudson.util.VersionNumber;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Installs a plugin either from a file, an URL, or from update center.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.331
 */
@Extension
public class InstallPluginCommand extends CLICommand {

    @Override
    public String getShortDescription() {
        return Messages.InstallPluginCommand_ShortDescription();
    }

    @Argument(metaVar = "SOURCE", required = true, usage =
            "If this is an URL, Jenkins downloads the URL and installs that as a plugin. " +
            "If it is the string ‘=’, the file will be read from standard input of the command. " +
            "Otherwise the name is assumed to be the short name of the plugin in the existing update center (like ‘findbugs’), " +
            "and the plugin will be installed from the update center. If the short name includes a minimum version number " +
            "(like ‘findbugs:1.4’), and there are multiple update centers publishing different versions, the update centers " +
            "will be searched in order for the first one publishing a version that is at least the specified version.")
    public List<String> sources = new ArrayList<>();

    @Deprecated
    @Option(name = "-name", usage = "No longer used.")
    public String name;

    @Option(name = "-restart", usage = "Restart Jenkins upon successful installation.")
    public boolean restart;

    @Option(name = "-deploy", usage = "Deploy plugins right away without postponing them until the reboot.")
    public boolean dynamicLoad;

    @Override
    protected int run() throws Exception {
        Jenkins h = Jenkins.get();
        h.checkPermission(Jenkins.ADMINISTER);
        PluginManager pm = h.getPluginManager();

        if (name != null) {
            stderr.println("-name is deprecated; it is no longer necessary nor honored.");
        }

        for (String source : sources) {
            if (source.equals("=")) {
                stdout.println(Messages.InstallPluginCommand_InstallingPluginFromStdin());
                File f = getTmpFile();
                FileUtils.copyInputStreamToFile(stdin, f);
                f = moveToFinalLocation(f);
                if (dynamicLoad) {
                    pm.dynamicLoad(f);
                }
                continue;
            }

            // is this an URL?
            try {
                URL u = new URL(source);
                stdout.println(Messages.InstallPluginCommand_InstallingPluginFromUrl(u));
                File f = getTmpFile();
                FileUtils.copyURLToFile(u, f); // TODO JENKINS-58248 proxy
                f = moveToFinalLocation(f);
                if (dynamicLoad) {
                    pm.dynamicLoad(f);
                }
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
                p = h.getUpdateCenter().getPlugin(source.substring(0, index), version);
                if (p == null) {
                    p = h.getUpdateCenter().getPlugin(source);
                }
            }
            if (p != null) {
                stdout.println(Messages.InstallPluginCommand_InstallingFromUpdateCenter(source));
                Throwable e = p.deploy(dynamicLoad).get().getError();
                if (e != null) {
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
                    Set<String> candidates = new HashSet<>();
                    for (UpdateSite s : h.getUpdateCenter().getSites()) {
                        Data dt = s.getData();
                        if (dt == null)
                            stdout.println(Messages.InstallPluginCommand_NoUpdateDataRetrieved(s.getUrl()));
                        else
                            candidates.addAll(dt.plugins.keySet());
                    }
                    stdout.println(Messages.InstallPluginCommand_DidYouMean(source, EditDistance.findNearest(source, candidates)));
                }
            }

            throw new AbortException("Error occurred, see previous output.");
        }

        if (restart)
            h.safeRestart();
        return 0; // all success
    }

    private static File getTmpFile() throws Exception {
        return Files.createTempFile(Jenkins.get().getPluginManager().rootDir.toPath(), "download", ".jpi.tmp").toFile();
    }

    private static File moveToFinalLocation(File tmpFile) throws Exception {
        String pluginName;
        try (JarFile jf = new JarFile(tmpFile)) {
            Manifest mf = jf.getManifest();
            if (mf == null) {
                throw new IllegalArgumentException("JAR lacks a manifest");
            }
            pluginName = mf.getMainAttributes().getValue("Short-Name");
        }
        if (pluginName == null) {
            throw new IllegalArgumentException("JAR manifest lacks a Short-Name attribute and so does not look like a plugin");
        }
        File target = new File(Jenkins.get().getPluginManager().rootDir, pluginName + ".jpi");
        Files.move(tmpFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
