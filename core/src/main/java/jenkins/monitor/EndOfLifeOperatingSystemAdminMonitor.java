/*
 * The MIT License
 *
 * Copyright 2023 mwaite.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import org.apache.commons.io.IOUtils;

public final class EndOfLifeOperatingSystemAdminMonitor extends EndOfLifeAdminMonitor {

    private final JSONArray data;

    public EndOfLifeOperatingSystemAdminMonitor(String identifier, String dependencyName, LocalDate beginDisplayDate, LocalDate endOfSupportDate, File dataFile, Pattern dataPattern) {
        super(identifier, dependencyName, beginDisplayDate, endOfSupportDate, dataFile, dataPattern);
        data = getOperatingSystemList();
    }

    EndOfLifeOperatingSystemAdminMonitor() {
        super("identifier", "dependencyName", null, null, new File("."), null);
        data = getOperatingSystemList();
    }

    /**
     * Gets the suggested operating system list from the JSON file.
     *
     * @return JSON array with the operating system list
     */
    @CheckForNull
    /*package*/ JSONArray getOperatingSystemList() {
        // Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONArray initialOperatingSystemList = null;
        try {
            ClassLoader cl = getClass().getClassLoader();
            URL localOperatingSystemData = cl.getResource("jenkins/monitor/EndOfLifeAdminMonitor/end-of-life-data.json");
            String initialOperatingSystemJson = IOUtils.toString(localOperatingSystemData.openStream(), StandardCharsets.UTF_8);
            initialOperatingSystemList = JSONArray.fromObject(initialOperatingSystemJson);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
        return initialOperatingSystemList;
    }

}
