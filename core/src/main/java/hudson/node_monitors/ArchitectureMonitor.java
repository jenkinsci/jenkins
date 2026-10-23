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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Callable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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
    public static final class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<String> {
        /**
         * Longer OS descriptions from the last monitoring round, kept apart from the monitoring data
         * so that {@link #get(Computer)} and the remote API keep returning the short string.
         */
        private volatile Map<Computer, String> details = Collections.emptyMap();

        @Override
        protected Callable<String, IOException> createCallable(Computer c) {
            return new GetArchTask();
        }

        @Override
        protected Map<Computer, String> monitor() throws InterruptedException {
            Map<Computer, String> data = new HashMap<>(super.monitor());
            Map<Computer, String> newDetails = new HashMap<>();
            for (Map.Entry<Computer, String> e : data.entrySet()) {
                String value = e.getValue();
                int idx = value == null ? -1 : value.indexOf('\n');
                if (idx >= 0) {
                    e.setValue(value.substring(0, idx));
                    newDetails.put(e.getKey(), value.substring(idx + 1));
                }
            }
            details = newDetails;
            return data;
        }

        /**
         * Longer description of the operating system of the given computer,
         * such as {@code Ubuntu 24.04.1 LTS · kernel 6.8.0-45-generic · amd64}.
         */
        @Restricted(NoExternalUse.class)
        @CheckForNull
        public String getDetails(Computer c) {
            return details.get(c);
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
     * Obtains the string that represents the architecture,
     * followed by a newline and a longer description of the operating system.
     */
    private static class GetArchTask extends MasterToSlaveCallable<String, IOException> {
        @Override
        public String call() {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            return os + " (" + arch + ')' + '\n' + OsDetails.get(os, arch);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Describes the operating system in more detail than the {@code os.*} system properties.
     * Runs on the agent, so it only uses JDK classes.
     */
    static final class OsDetails {
        private OsDetails() {
        }

        /**
         * The OS details only change across a reboot, which also restarts this JVM,
         * so compute them once instead of spawning processes on every monitoring round.
         */
        private static volatile String cachedDetails;

        static String get(String os, String arch) {
            String d = cachedDetails;
            if (d == null) {
                String name;
                try {
                    name = describeOs(os, System.getProperty("os.version"));
                } catch (RuntimeException x) {
                    LOGGER.log(Level.FINE, "Failed to describe the operating system", x);
                    name = os;
                }
                // newlines would break the format returned by GetArchTask
                d = (name + " · " + arch).replaceAll("\\s+", " ");
                cachedDetails = d;
            }
            return d;
        }

        private static String describeOs(String os, String version) {
            String lower = os.toLowerCase(Locale.ENGLISH);
            if (lower.startsWith("windows")) {
                return describeWindows(os);
            }
            List<String> parts = new ArrayList<>();
            if (lower.startsWith("mac")) {
                String product = run("/usr/bin/sw_vers", "-productVersion");
                parts.add("macOS " + (product != null ? product : version));
                String kernel = run("/usr/bin/uname", "-r");
                if (kernel != null) {
                    parts.add("kernel " + kernel);
                }
            } else {
                String pretty = readOsRelease();
                if (pretty != null) {
                    parts.add(pretty);
                    parts.add("kernel " + version);
                } else {
                    parts.add(os + " " + version);
                }
            }
            return String.join(" · ", parts);
        }

        private static final String WINDOWS_CURRENT_VERSION = "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";

        private static String describeWindows(String os) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) {
                return os;
            }
            // use the full path so that no other reg.exe from the working directory or PATH is run
            String reg = new File(systemRoot, "System32\\reg.exe").getPath();
            return describeWindows(os,
                    parseRegistryValue(run(reg, "query", WINDOWS_CURRENT_VERSION, "/v", "ProductName")),
                    parseRegistryValue(run(reg, "query", WINDOWS_CURRENT_VERSION, "/v", "DisplayVersion")),
                    parseRegistryValue(run(reg, "query", WINDOWS_CURRENT_VERSION, "/v", "CurrentBuildNumber")),
                    parseRegistryValue(run(reg, "query", WINDOWS_CURRENT_VERSION, "/v", "UBR")));
        }

        /**
         * Builds a description such as {@code Windows 11 Pro 25H2 · Build 26200.6899}.
         */
        static String describeWindows(String os, @CheckForNull String productName, @CheckForNull String displayVersion,
                                      @CheckForNull String build, @CheckForNull String ubr) {
            String name = productName != null ? productName : os;
            int buildNumber = -1;
            if (build != null) {
                try {
                    buildNumber = Integer.parseInt(build);
                } catch (NumberFormatException x) {
                    build = null;
                }
            }
            // ProductName still says "Windows 10" on Windows 11, which is told apart by its build number
            if (buildNumber >= 22000 && name.startsWith("Windows 10")) {
                name = "Windows 11" + name.substring("Windows 10".length());
            }
            if (displayVersion != null) {
                name += " " + displayVersion;
            }
            if (build == null) {
                return name;
            }
            if (ubr != null) {
                try {
                    // REG_DWORD values are printed in hex, such as 0x1ae3
                    build += "." + Long.decode(ubr);
                } catch (NumberFormatException x) {
                    // leave out the revision
                }
            }
            return name + " · Build " + build;
        }

        private static final Pattern REGISTRY_VALUE = Pattern.compile("^\\s*\\S+\\s+REG_\\w+\\s+(.+?)\\s*$");

        /**
         * Extracts the data from a {@code reg query /v} output line such as {@code ProductName    REG_SZ    Windows 10 Pro}.
         */
        @CheckForNull
        static String parseRegistryValue(@CheckForNull String line) {
            if (line == null) {
                return null;
            }
            Matcher m = REGISTRY_VALUE.matcher(line);
            return m.matches() ? m.group(1) : null;
        }

        @CheckForNull
        private static String readOsRelease() {
            // https://www.freedesktop.org/software/systemd/man/latest/os-release.html
            for (String file : new String[] {"/etc/os-release", "/usr/lib/os-release"}) {
                try {
                    String pretty = parsePrettyName(Files.readAllLines(Path.of(file), StandardCharsets.UTF_8));
                    if (pretty != null) {
                        return pretty;
                    }
                } catch (IOException | RuntimeException x) {
                    // try the next one
                }
            }
            return null;
        }

        /**
         * Extracts {@code PRETTY_NAME} from the lines of an {@code os-release} file, falling back to {@code NAME VERSION}.
         */
        @CheckForNull
        static String parsePrettyName(List<String> lines) {
            String name = null;
            String version = null;
            for (String line : lines) {
                int idx = line.indexOf('=');
                if (idx < 0) {
                    continue;
                }
                String key = line.substring(0, idx).trim();
                String value = unquote(line.substring(idx + 1).trim());
                if (value.isEmpty()) {
                    continue;
                }
                switch (key) {
                    case "PRETTY_NAME":
                        return value;
                    case "NAME":
                        name = value;
                        break;
                    case "VERSION":
                        version = value;
                        break;
                    default:
                        break;
                }
            }
            if (name == null) {
                return null;
            }
            return version != null ? name + " " + version : name;
        }

        /**
         * Removes shell-style quotes and backslash escapes from an {@code os-release} value.
         */
        private static String unquote(String value) {
            if (value.length() >= 2) {
                char first = value.charAt(0);
                if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                    value = value.substring(1, value.length() - 1);
                    if (first == '\'') {
                        return value;
                    }
                    // within double quotes only these characters are escaped
                    return value.replaceAll("\\\\([\\\\\"$`])", "$1");
                }
            }
            return value.replaceAll("\\\\(.)", "$1");
        }

        /**
         * Runs a short command, returning its trimmed last non-blank output line,
         * or null if it fails, exits with an error or does not finish in time.
         */
        @CheckForNull
        @SuppressFBWarnings(value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"}, justification = "Only fixed commands from absolute paths are run.")
        private static String run(String... command) {
            if (!new File(command[0]).isFile()) {
                return null;
            }
            Process proc = null;
            try {
                proc = new ProcessBuilder(command).redirectErrorStream(true).start();
                proc.getOutputStream().close();
                // the commands only print a line or two, which fits in the pipe buffer, so it is safe to wait first
                if (!proc.waitFor(5, TimeUnit.SECONDS) || proc.exitValue() != 0) {
                    return null;
                }
                String last = null;
                for (String line : readAll(proc.getInputStream()).split("\\R")) {
                    if (!line.isBlank()) {
                        last = line.trim();
                    }
                }
                return last;
            } catch (IOException x) {
                LOGGER.log(Level.FINE, "Failed to run " + command[0], x);
                return null;
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                if (proc != null) {
                    proc.destroyForcibly();
                }
            }
        }

        private static String readAll(InputStream in) throws IOException {
            try (in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                return out.toString(Charset.defaultCharset());
            }
        }

        private static final Logger LOGGER = Logger.getLogger(OsDetails.class.getName());
    }
}
