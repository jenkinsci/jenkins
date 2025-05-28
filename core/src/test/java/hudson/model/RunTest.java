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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.AnnotatedLargeText;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.XMLOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.mockito.Mockito;

class RunTest {
    private static final String SAMPLE_BUILD_OUTPUT = "Sample build output abc123.\n";

    @TempDir
    private File tmp;

    @Issue("JENKINS-15816")
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void timezoneOfID() throws Exception {
        TimeZone origTZ = TimeZone.getDefault();
        try {
            final Run r;
            String id;
            TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
            ExecutorService svc = Executors.newSingleThreadExecutor();
            try {
                r = svc.submit((Callable<Run>) () -> new Run(new StubJob(), 1234567890) {}).get();
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                id = r.getId();
                // explicitly cast to callable to make the Eclipse compiler happy
                assertEquals(id, svc.submit((Callable) r::getId).get());
            } finally {
                svc.shutdown();
            }
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            svc = Executors.newSingleThreadExecutor();
            try {
                assertEquals(id, r.getId());
                // explicitly cast to callable to make the Eclipse compiler happy
                assertEquals(id, svc.submit((Callable) r::getId).get());
            } finally {
                svc.shutdown();
            }
        } finally {
            TimeZone.setDefault(origTZ);
        }
    }


    private List<? extends Run<?, ?>.Artifact> createArtifactList(String... paths) {
        Run r = new Run(new StubJob(), 0) {};
        Run.ArtifactList list = r.new ArtifactList();
        for (String p : paths) {
            list.add(r.new Artifact(p, p, p, String.valueOf(p.length()), "n" + list.size()));  // Assuming all test inputs don't need urlencoding
        }
        list.computeDisplayName();
        return list;
    }

    @Test
    void artifactListDisambiguation1() {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/j.xml");
        assertEquals("c.xml", a.get(0).getDisplayPath());
        assertEquals("g.xml", a.get(1).getDisplayPath());
        assertEquals("j.xml", a.get(2).getDisplayPath());
    }

    @Test
    void artifactListDisambiguation2() {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/g.xml");
        assertEquals("c.xml", a.get(0).getDisplayPath());
        assertEquals("f/g.xml", a.get(1).getDisplayPath());
        assertEquals("i/g.xml", a.get(2).getDisplayPath());
    }

    @Test
    void artifactListDisambiguation3() {
        List<? extends Run<?, ?>.Artifact> a = createArtifactList("a.xml", "a/a.xml");
        assertEquals("a.xml", a.get(0).getDisplayPath());
        assertEquals("a/a.xml", a.get(1).getDisplayPath());
    }

    @Issue("JENKINS-26777")
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    @Test
    void getDurationString() {
      LocaleProvider providerToRestore = LocaleProvider.getProvider();
      try {
        // This test expects English texts.
        LocaleProvider.setProvider(new LocaleProvider() {
            @Override
            public Locale get() {
                return Locale.ENGLISH;
            }
        });

        Run r = new Run(new StubJob(), 0) {};
        assertEquals("Not started yet", r.getDurationString());
        r.onStartBuilding();
        String msg;
        msg = r.getDurationString();
        assertTrue(msg.endsWith(" and counting"), msg);
        r.onEndBuilding();
        msg = r.getDurationString();
        assertFalse(msg.endsWith(" and counting"), msg);
      } finally {
        LocaleProvider.setProvider(providerToRestore);
      }
    }

    @Issue("JENKINS-27441")
    @SuppressWarnings("deprecation")
    @Test
    void getLogReturnsAnEmptyListWhenCalledWith0() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = newFolder(tmp, "junit");
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8);
        w.println("dummy");
        w.close();
        List<String> logLines = r.getLog(0);
        assertTrue(logLines.isEmpty());
    }

    @SuppressWarnings("deprecation")
    @Test
    void getLogReturnsAnRightOrder() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = newFolder(tmp, "junit");
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8);
        for (int i = 0; i < 20; i++) {
            w.println("dummy" + i);
        }

        w.close();
        List<String> logLines = r.getLog(10);
        assertFalse(logLines.isEmpty());

        for (int i = 1; i < 10; i++) {
            assertEquals("dummy" + (10 + i), logLines.get(i));
        }
        int truncatedCount = 10 * ("dummyN".length() + System.lineSeparator().length()) - 2;
        assertEquals("[...truncated " + truncatedCount + " B...]", logLines.get(0));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getLogReturnsAllLines() throws Exception {
        Job j = Mockito.mock(Job.class);
        File tempBuildDir = newFolder(tmp, "junit");
        Mockito.when(j.getBuildDir()).thenReturn(tempBuildDir);
        Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(j, 0) {};
        File f = r.getLogFile();
        f.getParentFile().mkdirs();
        PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8);
        w.print("a1\nb2\n\nc3");
        w.close();
        List<String> logLines = r.getLog(10);
        assertFalse(logLines.isEmpty());

        assertEquals("a1", logLines.get(0));
        assertEquals("b2", logLines.get(1));
        assertEquals("", logLines.get(2));
        assertEquals("c3", logLines.get(3));
    }

    @Test
    void compareRunsFromSameJobWithDifferentNumbers() throws Exception {
        final Jenkins group = Mockito.mock(Jenkins.class);
        Mockito.when(group.getFullName()).thenReturn("j");
        final Job j = Mockito.mock(Job.class);

        Mockito.when(j.getParent()).thenReturn(group);
        Mockito.when(j.getFullName()).thenReturn("Mock job");
        Mockito.when(j.assignBuildNumber()).thenReturn(1, 2);

        Run r1 = new Run(j) {};
        Run r2 = new Run(j) {};

        final Set<Run> treeSet = new TreeSet<>();
        treeSet.add(r1);
        treeSet.add(r2);

        assertTrue(r1.compareTo(r2) < 0);
        assertEquals(2, treeSet.size());
    }

    @Issue("JENKINS-42319")
    @Test
    void compareRunsFromDifferentParentsWithSameNumber() throws Exception {
        final Jenkins group1 = Mockito.mock(Jenkins.class);
        final Jenkins group2 = Mockito.mock(Jenkins.class);
        final Job j1 = Mockito.mock(Job.class);
        final Job j2 = Mockito.mock(Job.class);
        Mockito.when(j1.getParent()).thenReturn(group1);
        Mockito.when(j1.getFullName()).thenReturn("Mock job");
        Mockito.when(j2.getParent()).thenReturn(group2);
        Mockito.when(j2.getFullName()).thenReturn("Mock job2");
        Mockito.when(group1.getFullName()).thenReturn("g1");
        Mockito.when(group2.getFullName()).thenReturn("g2");
        Mockito.when(j1.assignBuildNumber()).thenReturn(1);
        Mockito.when(j2.assignBuildNumber()).thenReturn(1);

        Run r1 = new Run(j1) {};
        Run r2 = new Run(j2) {};

        final Set<Run> treeSet = new TreeSet<>();
        treeSet.add(r1);
        treeSet.add(r2);

        assertNotEquals(0, r1.compareTo(r2));
        assertEquals(2, treeSet.size());
    }

    @Test
    void willTriggerLogToStartWithNextFullLine() throws Exception {
        assertWriteLogToEquals(new String(new char[2]).replace("\0", SAMPLE_BUILD_OUTPUT) + "Finished: SUCCESS.\n", 2 * SAMPLE_BUILD_OUTPUT.length() + 10);
    }

    @Test
    void wontPushOffsetOnRenderingFromBeginning() throws Exception {
        assertWriteLogToEquals(new String(new char[5]).replace("\0", SAMPLE_BUILD_OUTPUT) + "Finished: SUCCESS.\n", 0);
    }

    @Test
    void wontPushOffsetOnRenderingFromBeginningOfLine() throws Exception {
        assertWriteLogToEquals(new String(new char[3]).replace("\0", SAMPLE_BUILD_OUTPUT) + "Finished: SUCCESS.\n", 2 * SAMPLE_BUILD_OUTPUT.length());
    }

    @Test
    void willRenderNothingIfOffsetSetOnLastLine() throws Exception {
        assertWriteLogToEquals("", 5 * SAMPLE_BUILD_OUTPUT.length() + 6);
    }

    private void assertWriteLogToEquals(String expectedOutput, long offset) throws Exception {
        try (
            ByteBuffer buf = new ByteBuffer();
            PrintStream ps = new PrintStream(buf, true);
            StringWriter writer = new StringWriter()
        ) {
            for (int i = 0; i < 5; i++) {
                ps.print(SAMPLE_BUILD_OUTPUT);
            }
            ps.print("Finished: SUCCESS.\n");

            final Run<? extends Job<?, ?>, ? extends Run<?, ?>> r = new Run(Mockito.mock(Job.class)) {
                @NonNull
                @Override
                public AnnotatedLargeText<?> getLogText() {
                    return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, null);
                }

                @NonNull
                @Override
                public InputStream getLogInputStream() {
                    return buf.newInputStream();
                }
            };
            final XMLOutput xmlOutput = Mockito.mock(XMLOutput.class);
            Mockito.when(xmlOutput.asWriter()).thenReturn(writer);
            r.writeLogTo(offset, xmlOutput);
            assertEquals(expectedOutput, writer.toString());
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
