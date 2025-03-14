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

package hudson;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.DualOutputStream;
import hudson.util.EncodingStream;
import hudson.util.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.util.SystemProperties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point to Hudson from command line.
 * <p>
 * This tool runs another process and sends its result to Hudson.
 * </p>
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int MAX_RETRIES = 3; // Prevent infinite retry loops
    private static final int TIMEOUT = SystemProperties.getInteger(Main.class.getName() + ".timeout", 15000);

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error", e);
            System.exit(-1);
        }
    }

    public static int run(String[] args) throws Exception {
        String home = getHudsonHome();
        if (home == null) {
            LOGGER.severe("JENKINS_HOME is not set.");
            return -1;
        }
        if (args.length < 2) {
            LOGGER.severe("Usage: <job-name> <command> <args..>");
            return -1;
        }
        return remotePost(args);
    }

    private static String getHudsonHome() {
        return EnvVars.masterEnvVars.getOrDefault("JENKINS_HOME", EnvVars.masterEnvVars.get("HUDSON_HOME"));
    }

    public static int remotePost(String[] args) throws Exception {
        String projectName = args[0];
        String home = getHudsonHome();
        if (!home.endsWith("/")) home += '/';  

        // Secure authentication using API token
        String apiToken = System.getenv("JENKINS_API_TOKEN");
        if (apiToken == null || apiToken.isEmpty()) {
            LOGGER.severe("JENKINS_API_TOKEN is not set. Authentication required.");
            return -1;
        }
        String authHeader = "Bearer " + apiToken;

        if (!isValidJenkinsInstance(home, authHeader)) {
            return -1;
        }

        URL jobURL = new URL(home + "job/" + Util.encode(projectName).replace("/", "/job/") + "/");

        if (!isValidJob(jobURL, authHeader)) {
            return -1;
        }

        // Get CSRF token
        String[] csrfData = getCSRFToken(home, authHeader);
        String crumbField = csrfData[0];
        String crumbValue = csrfData[1];
        String sessionCookies = csrfData[2];

        // Write output to a secure temporary file
        File tmpFile = Files.createTempFile("jenkins", "log", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).toFile();
        
        try {
            int ret;
            try (OutputStream os = Files.newOutputStream(tmpFile.toPath());
                 Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                
                w.write("<?xml version='1.1' encoding='UTF-8'?>");
                w.write("<run><log encoding='hexBinary' content-encoding='" + StandardCharsets.UTF_8.name() + "'>");
                w.flush();

                long start = System.currentTimeMillis();

                List<String> cmd = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                Proc proc = new Proc.LocalProc(cmd.toArray(new String[0]), null, System.in, new DualOutputStream(System.out, new EncodingStream(os)));

                ret = proc.join();

                w.write("</log><result>" + ret + "</result><duration>" + (System.currentTimeMillis() - start) + "</duration></run>");
            }

            URL location = new URL(jobURL, "postBuildResult");
            return sendBuildResult(location, authHeader, crumbField, crumbValue, sessionCookies, tmpFile, ret);

        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }

    private static boolean isValidJenkinsInstance(String home, String authHeader) throws IOException {
        HttpURLConnection con = open(new URL(home));
        con.setRequestProperty("Authorization", authHeader);
        con.connect();

        if (con.getResponseCode() != 200 || con.getHeaderField("X-Hudson") == null) {
            LOGGER.severe(home + " is not a valid Jenkins instance (" + con.getResponseMessage() + ")");
            return false;
        }
        return true;
    }

    private static boolean isValidJob(URL jobURL, String authHeader) throws IOException {
        HttpURLConnection con = open(new URL(jobURL, "acceptBuildResult"));
        con.setRequestProperty("Authorization", authHeader);
        con.connect();

        if (con.getResponseCode() != 200) {
            LOGGER.severe(jobURL + " is not a valid external job (" + con.getResponseCode() + " " + con.getResponseMessage() + ")");
            return false;
        }
        return true;
    }

    private static String[] getCSRFToken(String home, String authHeader) {
        try {
            HttpURLConnection con = open(new URL(home + "crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"));
            con.setRequestProperty("Authorization", authHeader);
            String line = IOUtils.readFirstLine(con.getInputStream(), StandardCharsets.UTF_8.name());
            String[] components = line.split(":");
            return components.length == 2 ? new String[]{components[0], components[1], con.getHeaderField("Set-Cookie")} : new String[]{null, null, null};
        } catch (IOException e) {
            LOGGER.warning("Failed to retrieve CSRF token. This Jenkins instance may not require one.");
            return new String[]{null, null, null};
        }
    }

    private static int sendBuildResult(URL location, String authHeader, String crumbField, String crumbValue, String sessionCookies, File tmpFile, int ret) throws IOException {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                HttpURLConnection con = open(location);
                con.setRequestProperty("Authorization", authHeader);
                if (crumbField != null && crumbValue != null) {
                    con.setRequestProperty(crumbField, crumbValue);
                    con.setRequestProperty("Cookie", sessionCookies);
                }
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode((int) tmpFile.length());
                con.setRequestProperty("Content-Type", "application/xml");
                con.connect();

                try (InputStream in = Files.newInputStream(tmpFile.toPath())) {
                    org.apache.commons.io.IOUtils.copy(in, con.getOutputStream());
                }

                if (con.getResponseCode() == 200) {
                    return ret;
                } else {
                    LOGGER.warning("Failed to post build result: " + con.getResponseCode() + " " + con.getResponseMessage());
                }
            } catch (HttpRetryException e) {
                if (e.getLocation() != null) {
                    location = new URL(e.getLocation());
                } else {
                    throw e;
                }
            }
            attempts++;
        }
        return -1;
    }

    private static HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setReadTimeout(TIMEOUT);
        c.setConnectTimeout(TIMEOUT);
        return c;
    }
}
