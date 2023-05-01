/*
 * The MIT License
 *
 * Copyright 2021 Tim Jacomb.
 * Copyright 2023 Mark Waite.
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

package jenkins.monitor;

import hudson.Extension;
import hudson.security.Permission;
import java.io.File;
import java.time.LocalDate;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
@Symbol("endOfLifeDebian10AdminMonitor")
public class EndOfLifeDebian10AdminMonitor extends EndOfLifeAdminMonitor {
    public EndOfLifeDebian10AdminMonitor() {
        super(EndOfLifeDebian10AdminMonitor.class.getName(),
              "Debian 10",
              LocalDate.of(2024, 1, 1),
              LocalDate.of(2024, 6, 30),
              "https://www.jenkins.io/redirect/operating-system-end-of-life",
              new File("/etc/os-release"),
              Pattern.compile(".*Debian.* 10.*")
              );
    }

    /* Package protected for use by tests */
    EndOfLifeDebian10AdminMonitor(File dataFile) {
        super(EndOfLifeDebian10AdminMonitor.class.getName(),
              "Debian 10",
              LocalDate.of(2024, 1, 1),
              LocalDate.of(2024, 6, 30),
              "https://www.jenkins.io/redirect/operating-system-end-of-life",
              dataFile,
              Pattern.compile(".*Debian.* 10.*")
              );
    }

    public String getDependencyName() {
        return dependencyName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public String getBeginDisplayDate() {
        return beginDisplayDate.toString();
    }

    public String getEndOfSupportDate() {
        return endOfSupportDate.toString();
    }

    public String getDocumentationURL() {
        return documentationURL;
    }

    public boolean isUnsupported() {
        unsupported = LocalDate.now().isAfter(endOfSupportDate);
        return unsupported;
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }
}
