/*
 * The MIT License
 *
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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

public final class EndOfLifeOperatingSystemAdminMonitor extends EndOfLifeAdminMonitor {

    private static class EndOfLifeData {

        final Pattern pattern;
        final LocalDate startDate;
        final LocalDate effectiveDate;

        public EndOfLifeData(Pattern pattern, LocalDate startDate, LocalDate effectiveDate) {
            this.pattern = pattern;
            this.startDate = startDate;
            this.effectiveDate = effectiveDate;
        }
    }

    private final List<EndOfLifeData> data = new ArrayList<>();

    public EndOfLifeOperatingSystemAdminMonitor(String identifier, String dependencyName, LocalDate beginDisplayDate, LocalDate endOfSupportDate, File dataFile, Pattern dataPattern) {
        super(identifier, dependencyName, beginDisplayDate, endOfSupportDate, dataFile, dataPattern);
    }

    EndOfLifeOperatingSystemAdminMonitor() throws IOException {
        super("identifier", "dependencyName", null, null, new File("."), null);
        fillOperatingSystemList();
    }

    /**
     * Gets the suggested operating system list from the JSON file.
     *
     * @return JSON array with the operating system list
     */
    @NonNull
    private JSONArray getOperatingSystemList() throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        URL localOperatingSystemData = cl.getResource("jenkins/monitor/EndOfLifeAdminMonitor/end-of-life-data.json");
        String initialOperatingSystemJson = IOUtils.toString(localOperatingSystemData.openStream(), StandardCharsets.UTF_8);
        JSONArray initialOperatingSystemList = JSONArray.fromObject(initialOperatingSystemJson);
        return initialOperatingSystemList;
    }

    private void fillOperatingSystemList() throws IOException {
        JSONArray systems = getOperatingSystemList();
        for (Object systemObj : systems) {
            if (!(systemObj instanceof JSONObject)) {
                LOGGER.log(Level.SEVERE, "Wrong object type in operating system end of life monitor data file");
                break;
            }
            JSONObject system = (JSONObject) systemObj;
            Pattern pattern;
            LocalDate startDate;
            LocalDate effectiveDate;
            if (system.has("pattern")) {
                pattern = Pattern.compile(system.getString("pattern"));
            } else {
                LOGGER.log(Level.SEVERE, "No pattern to be matched in operating system end of life monitor");
                break;
            }
            if (system.has("start")) {
                startDate = LocalDate.parse(system.getString("start"));
            } else {
                LOGGER.log(Level.SEVERE, "No start date for operating system in end of life monitor for pattern {0}", pattern);
                break;
            }
            if (system.has("effective")) {
                effectiveDate = LocalDate.parse(system.getString("effective"));
            } else {
                LOGGER.log(Level.SEVERE, "No effective date for operating system in end of life monitor for pattern {0}", pattern);
                break;
            }
            LOGGER.log(Level.FINE, "Pattern {0} starts {1} and is effective {2}",
                    new Object[]{pattern, startDate, effectiveDate});
            data.add(new EndOfLifeData(pattern, startDate, effectiveDate));
        }

    }
}
