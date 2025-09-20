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

import com.thoughtworks.xstream.core.util.Base64Encoder;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.util.SystemProperties;

/**
 * Entry point to Hudson from command line.
 *
 * <p>
 * This tool runs another process and sends its result to Hudson.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {

    /** @see #remotePost */
    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /** @see #remotePost */
    public static int run(String[] args) throws Exception {
        String home = getHudsonHome();
        if (home == null) {
            System.err.println("JENKINS_HOME is not set.");
            return -1;
        }
        if (args.length < 2) {
            System.err.println("Usage: <job-name> <command> <args..>");
            return -1;
        }

        return remotePost(args);
    }

    private static String getHudsonHome() {
        String home = EnvVars.masterEnvVars.get("JENKINS_HOME");
        if (home != null) return home;
        return EnvVars.masterEnvVars.get("HUDSON_HOME");
    }

    /**
     * Run command and send result to {@code ExternalJob} in the {@code external-monitor-job} plugin.
     * Obsoleted by {@code SetExternalBuildResultCommand} but kept here for compatibility.
     */
    public static int remotePost(String[] args) throws Exception {
        String projectName = args[0];

        String home = getHudsonHome();
        if (!home.endsWith("/"))     home = home + '/';  // make sure it ends with '/'

        // check for authentication info
        String auth = new URL(home).getUserInfo();
        if (auth != null) auth = "Basic " + new Base64Encoder().encode(auth.getBytes(StandardCharsets.UTF_8));

        { // check if the home is set correctly
            HttpURLConnection con = open(new URL(home));
            if (auth != null) con.setRequestProperty("Authorization", auth);
            con.connect();
            if (con.getResponseCode() != 200
            || con.getHeaderField("X-Hudson") == null) {
                System.err.println(home + " is not Hudson (" + con.getResponseMessage() + ")");
                return -1;
            }
        }

        URL jobURL = new URL(home + "job/" + Util.encode(projectName).replace("/", "/job/") + "/");

        { // check if the job name is correct
            HttpURLConnection con = open(new URL(jobURL, "acceptBuildResult"));
            if (auth != null) con.setRequestProperty("Authorization", auth);
            con.connect();
            if (con.getResponseCode() != 200) {
                System.err.println(jobURL + " is not a valid external job (" + con.getResponseCode() + " " + con.getResponseMessage() + ")");
                return -1;
            }
        }

        // get a crumb to pass the csrf check
        String crumbField = null, crumbValue = null, sessionCookies = null;
        try {
            HttpURLConnection con = open(new URL(home +
                    "crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)"));
            if (auth != null) con.setRequestProperty("Authorization", auth);
            String line = IOUtils.readFirstLine(con.getInputStream(), "UTF-8");
            String[] components = line.split(":");
            if (components.length == 2) {
                crumbField = components[0];
                crumbValue = components[1];
            }
            sessionCookies = con.getHeaderField("Set-Cookie");
        } catch (IOException e) {
            // presumably this Hudson doesn't use CSRF protection
        }

        // write the output to a temporary file first.
        File tmpFile = File.createTempFile("jenkins", "log");
        try {
            int ret;
            try (OutputStream os = Files.newOutputStream(tmpFile.toPath());
                 Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                w.write("<?xml version='1.1' encoding='UTF-8'?>");
                w.write("<run><log encoding='hexBinary' content-encoding='" + Charset.defaultCharset().name() + "'>");
                w.flush();

                // run the command
                long start = System.currentTimeMillis();

                List<String> cmd = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                Proc proc = new Proc.LocalProc(cmd.toArray(new String[0]), (String[]) null, System.in,
                    new DualOutputStream(System.out, new EncodingStream(os)));

                ret = proc.join();

                w.write("</log><result>" + ret + "</result><duration>" + (System.currentTimeMillis() - start) + "</duration></run>");
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }

            URL location = new URL(jobURL, "postBuildResult");
            while (true) {
                try {
                    // start a remote connection
                    HttpURLConnection con = open(location);
                    if (auth != null) con.setRequestProperty("Authorization", auth);
                    if (crumbField != null && crumbValue != null) {
                        con.setRequestProperty(crumbField, crumbValue);
                        con.setRequestProperty("Cookie", sessionCookies);
                    }
                    con.setDoOutput(true);
                    // this tells HttpURLConnection not to buffer the whole thing
                    con.setFixedLengthStreamingMode((int) tmpFile.length());
                    con.setRequestProperty("Content-Type", "application/xml");
                    con.connect();
                    // send the data
                    try (InputStream in = Files.newInputStream(tmpFile.toPath())) {
                        org.apache.commons.io.IOUtils.copy(in, con.getOutputStream());
                    } catch (InvalidPathException e) {
                        throw new IOException(e);
                    }

                    if (con.getResponseCode() != 200) {
                        org.apache.commons.io.IOUtils.copy(con.getErrorStream(), System.err);
                    }

                    return ret;
                } catch (HttpRetryException e) {
                    if (e.getLocation() != null) {
                        // retry with the new location
                        location = new URL(e.getLocation());
                        continue;
                    }
                    // otherwise failed for reasons beyond us.
                    throw e;
                }
            }
        } finally {
            Files.delete(Util.fileToPath(tmpFile));
        }
    }

    /**
     * Connects to the given HTTP URL and configure time out, to avoid infinite hang.
     */
    private static HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setReadTimeout(TIMEOUT);
        c.setConnectTimeout(TIMEOUT);
        return c;
    }

    /**
     * Set to true if we are running unit tests.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for debugging")
    public static boolean isUnitTest = false;

    /**
     * Set to true if we are running inside {@code mvn jetty:run}.
     * This is also set if running inside {@code mvn hpi:run} since plugins parent POM 2.30.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for debugging")
    public static boolean isDevelopmentMode = SystemProperties.getBoolean(Main.class.getName() + ".development");

    /**
     * Time out for socket connection to Hudson.
     */
    public static final int TIMEOUT = SystemProperties.getInteger(Main.class.getName() + ".timeout", 15000);
}
