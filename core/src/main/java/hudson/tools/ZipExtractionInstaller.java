/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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

package hudson.tools;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Installs a tool into the Hudson working area by downloading and unpacking a ZIP file.
 * @since 1.305
 */
public class ZipExtractionInstaller extends ToolInstaller {

    /**
     * URL of a ZIP file which should be downloaded in case the tool is missing.
     */
    private final String url;
    /**
     * Optional subdir to extract.
     */
    private final String subdir;

    @DataBoundConstructor
    public ZipExtractionInstaller(String label, String url, String subdir) {
        super(label);
        this.url = url;
        this.subdir = Util.fixEmptyAndTrim(subdir);
    }

    public String getUrl() {
        return url;
    }

    public String getSubdir() {
        return subdir;
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath dir = preferredLocation(tool, node);
        if (dir.installIfNecessaryFrom(new URL(url), log, "Unpacking " + url + " to " + dir + " on " + node.getDisplayName())) {
            dir.act(new ChmodRecAPlusX());
        }
        if (subdir == null) {
            return dir;
        } else {
            return dir.child(subdir);
        }
    }

    @Extension @Symbol("zip")
    public static class DescriptorImpl extends ToolInstallerDescriptor<ZipExtractionInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ZipExtractionInstaller_DescriptorImpl_displayName();
        }

        @RequirePOST
        public FormValidation doCheckUrl(@QueryParameter String value) throws InterruptedException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.ok();
            }

            URI uri;
            try {
                uri = new URI(value);
            } catch (URISyntaxException e) {
                return FormValidation.error(e, Messages.ZipExtractionInstaller_malformed_url());
            }
            HttpClient httpClient = ProxyConfiguration.newHttpClient();
            HttpRequest httpRequest;
            try {
                httpRequest = ProxyConfiguration.newHttpRequestBuilder(uri)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e, Messages.ZipExtractionInstaller_malformed_url());
            }
            try {
                HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                if (httpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                    return FormValidation.ok();
                }
                return FormValidation.error(Messages.ZipExtractionInstaller_bad_connection());
            } catch (IOException e) {
                return FormValidation.error(e, Messages.ZipExtractionInstaller_could_not_connect());
            }
        }

    }

    /**
     * Sets execute permission on all files, since unzip etc. might not do this.
     * Hackish, is there a better way?
     */
    static class ChmodRecAPlusX extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File d, VirtualChannel channel) throws IOException {
            if (!Functions.isWindows())
                process(d);
            return null;
        }

        private void process(File f) {
            if (f.isFile()) {
                f.setExecutable(true, false);
            } else {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File kid : kids) {
                        process(kid);
                    }
                }
            }
        }
    }

}
