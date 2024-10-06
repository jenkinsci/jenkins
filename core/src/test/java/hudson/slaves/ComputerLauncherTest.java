/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

public class ComputerLauncherTest {

    @Test public void jdk7() {
        assertThrows(
                IOException.class,
                () ->
                        assertChecked(
                                "java version \"1.7.0_05\"\n"
                                    + "Java(TM) SE Runtime Environment (build 1.7.0_05-b05)\n"
                                    + "Java HotSpot(TM) Server VM (build 23.1-b03, mixed mode)\n",
                                "1.7.0"));
    }

    @Test public void openJDK7() {
        assertThrows(
                IOException.class,
                () ->
                        assertChecked(
                                "openjdk version \"1.7.0-internal\"\n"
                                    + "OpenJDK Runtime Environment (build"
                                    + " 1.7.0-internal-pkgsrc_2010_01_03_06_54-b00)\n"
                                    + "OpenJDK 64-Bit Server VM (build 17.0-b04, mixed mode)\n",
                                "1.7.0"));
    }

    @Test public void jdk6() {
        assertThrows(
                IOException.class,
                () ->
                        assertChecked(
                                "java version \"1.6.0_33\"\n"
                                    + "Java(TM) SE Runtime Environment (build 1.6.0_33-b03)\n"
                                    + "Java HotSpot(TM) Server VM (build 20.8-b03, mixed mode)\n",
                                "1.6.0"));
    }

    @Test public void jdk5() {
        assertThrows(
            IOException.class,
            () ->
                ComputerLauncher.checkJavaVersion(
                    new PrintStream(OutputStream.nullOutputStream()),
                    "-",
                    new BufferedReader(
                        new StringReader(
                            "java version \"1.5.0_22\"\n"
                                + "Java(TM) 2 Runtime Environment, Standard Edition (build 1.5.0_22-b03)\n"
                                + "Java HotSpot(TM) Server VM (build 1.5.0_22-b03, mixed mode)\n"))));
    }

    @Test public void j2sdk4() {
        assertThrows(
            IOException.class,
            () ->
                ComputerLauncher.checkJavaVersion(
                    new PrintStream(OutputStream.nullOutputStream()),
                    "-",
                    new BufferedReader(
                        new StringReader(
                            "java version \"1.4.2_19\"\n"
                                + "Java(TM) 2 Runtime Environment, Standard Edition (build 1.4.2_19-b04)\n"
                                + "Java HotSpot(TM) Client VM (build 1.4.2_19-b04, mixed mode)\n"))));
    }

    @Test public void jdk8() throws IOException {
        assertChecked("java version \"1.8.0_05\"\nJava(TM) SE Runtime Environment (build 1.8.0_05-b05)\nJava HotSpot(TM) Server VM (build 23.1-b03, mixed mode)\n", "1.8.0");
    }

    @Test public void openJDK8() throws IOException {
        assertChecked("openjdk version \"1.8.0-internal\"\nOpenJDK Runtime Environment (build 1.8.0-internal-pkgsrc_2015_01_03_06_54-b00)\nOpenJDK 64-Bit Server VM (build 17.0-b04, mixed mode)\n", "1.8.0");
    }

    @Test public void jdk10() throws IOException { // predicted
        assertChecked("java version \"1.10.0_02\"\nJava(TM) SE Runtime Environment (build 1.10.0_02-b01)\nJava HotSpot(TM) Server VM (build 23.1-b03, mixed mode)\n", "1.10.0");
    }

    @Issue("JENKINS-60678")
    @Test public void adoptOpenJDK11() throws IOException {
        assertChecked("openjdk version \"11.0.5\" 2019-10-15\n" +
                "OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.5+10)\n" +
                "OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.5+10, mixed mode)", "11.0.5");
    }

    @Issue("JENKINS-60678")
    @Test public void amazonCorrettoJDK11() throws IOException {
        assertChecked("openjdk version \"11.0.6\" 2020-01-14 LTS\n" +
                "OpenJDK Runtime Environment Corretto-11.0.6.10.1 (build 11.0.6+10-LTS)\n" +
                "OpenJDK 64-Bit Server VM Corretto-11.0.6.10.1 (build 11.0.6+10-LTS, mixed mode)", "11.0.6");
    }

    @Issue("JENKINS-60678")
    @Test public void openJDK1106() throws IOException {
        assertChecked("openjdk version \"11.0.6\" 2020-01-14\n" +
                "OpenJDK Runtime Environment 18.9 (build 11.0.6+10)\n" +
                "OpenJDK 64-Bit Server VM 18.9 (build 11.0.6+10, mixed mode)", "11.0.6");
    }

    @Issue("JENKINS-60678")
    @Test public void azulZuluOpenJDK11() throws IOException {
        assertChecked("openjdk version \"11.0.5\" 2019-10-15 LTS\n" +
                "OpenJDK Runtime Environment Zulu11.35+15-CA (build 11.0.5+10-LTS)\n" +
                "OpenJDK 64-Bit Server VM Zulu11.35+15-CA (build 11.0.5+10-LTS, mixed mode)", "11.0.5");
    }

    private static void assertChecked(String text, String spec) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ComputerLauncher.checkJavaVersion(new PrintStream(os, false, Charset.defaultCharset()), "bin/java", new BufferedReader(new StringReader(text)));
        String logged = os.toString(Charset.defaultCharset());
        assertTrue(logged.contains(Messages.ComputerLauncher_JavaVersionResult("bin/java", spec)), logged);
    }
}
