/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.node_monitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Callable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Discovers the architecture of the system to display in the agent list page.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArchitectureMonitor extends NodeMonitor {

    @DataBoundConstructor
    public ArchitectureMonitor() {
    }

    @Extension @Symbol("architecture")
    public static final class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<ArchitectureMonitor.Info> {
        @Override
        protected Callable<Info, IOException> createCallable(Computer c) {
            return new GetArchTask();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ArchitectureMonitor_DisplayName();
        }

        @Override
        public boolean canTakeOffline() {
            return false;
        }
    }

    /**
     * Architecture of a node: a short summary and a longer description of the OS.
     */
    public static final class Info implements Serializable {
        private final String summary;
        private final String details;

        Info(String summary, String details) {
            this.summary = summary;
            this.details = details;
        }

        /** Short form, such as {@code Linux (amd64)}. */
        @Override
        public String toString() {
            return summary;
        }

        /** Longer form, such as {@code Ubuntu 24.04 · kernel 6.8.0-45 · amd64}. */
        public String getDetails() {
            return details;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Obtains the information that represents the architecture.
     */
    private static class GetArchTask extends MasterToSlaveCallable<Info, IOException> {
        @Override
        public Info call() {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            String version = System.getProperty("os.version");
            String lower = os.toLowerCase(Locale.ENGLISH);
            List<String> parts = new ArrayList<>();
            try {
                if (lower.startsWith("windows")) {
                    // "Microsoft Windows [Version 10.0.26200.1234]"
                    String ver = run("cmd", "/c", "ver");
                    String build = null;
                    if (ver != null) {
                        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(ver);
                        if (m.find()) {
                            build = m.group(3);
                        }
                    }
                    String name = os;
                    // ProductName still says "Windows 10 Pro" on Windows 11, so only take the edition from it
                    String product = run("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "/v", "ProductName");
                    if (product != null) {
                        Matcher m = Pattern.compile("ProductName\\s+REG_SZ\\s+(.+)").matcher(product);
                        if (m.find()) {
                            String full = m.group(1).trim();
                            if (full.startsWith(os)) {
                                name = full;
                            } else {
                                String edition = full.replaceFirst("^Windows( \\d+)?\\s*", "");
                                if (!edition.isEmpty()) {
                                    name = os + " " + edition;
                                }
                            }
                        }
                    }
                    parts.add(build != null ? name + " Build " + build : name + (version != null ? " " + version : ""));
                } else if (lower.contains("mac")) {
                    String product = run("sw_vers", "-productVersion");
                    parts.add("macOS " + (product != null ? product : version));
                    String kernel = run("uname", "-r");
                    if (kernel != null) {
                        parts.add("kernel " + kernel);
                    }
                } else {
                    String pretty = prettyName();
                    parts.add(pretty != null ? pretty : os);
                    if (version != null) {
                        parts.add("kernel " + version);
                    }
                }
            } catch (RuntimeException x) {
                // best effort only
                parts.clear();
                parts.add(os);
            }
            parts.add(arch);
            return new Info(os + " (" + arch + ')', String.join(" · ", parts));
        }

        private static String prettyName() {
            for (String file : new String[] {"/etc/os-release", "/usr/lib/os-release"}) {
                Path p = Paths.get(file);
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                try {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (line.startsWith("PRETTY_NAME=")) {
                            String value = line.substring("PRETTY_NAME=".length()).trim();
                            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value.isEmpty() ? null : value;
                        }
                    }
                } catch (IOException | RuntimeException x) {
                    // try next
                }
            }
            return null;
        }

        /** Runs a short command, returning its trimmed last non-blank output line or null on any failure. */
        private static String run(String... command) {
            try {
                Process proc = new ProcessBuilder(command).redirectErrorStream(true).start();
                proc.getOutputStream().close();
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    return null;
                }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    String last = null;
                    while ((line = r.readLine()) != null) {
                        if (!line.isBlank()) {
                            last = line.trim();
                        }
                    }
                    return last;
                }
            } catch (IOException x) {
                // ignore
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}
