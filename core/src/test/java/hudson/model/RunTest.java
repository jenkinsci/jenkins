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

import hudson.Util;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.stream.events.Characters;

import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class RunTest {

    @Bug(15816)
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test public void timezoneOfID() throws Exception {
        TimeZone origTZ = TimeZone.getDefault();
        try {
            final Run r;
            String id;
            TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
            ExecutorService svc = Executors.newSingleThreadExecutor();
            try {
                r = svc.submit(new Callable<Run>() {
                    @Override public Run call() throws Exception {
                        return new Run(new StubJob(), 1234567890) {};
                    }
                }).get();
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                id = r.getId();
                assertEquals(id, svc.submit(new Callable<String>() {
                    @Override public String call() throws Exception {
                        return r.getId();
                    }
                }).get());
            } finally {
                svc.shutdown();
            }
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            svc = Executors.newSingleThreadExecutor();
            try {
                assertEquals(id, r.getId());
                assertEquals(id, svc.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return r.getId();
                    }
                }).get());
            } finally {
                svc.shutdown();
            }
        } finally {
            TimeZone.setDefault(origTZ);
        }
    }
    

    @Bug(15587)
    @Test 
    public void testParseTimestampFromBuildDir() throws Exception {
        //Assume.assumeTrue(!Functions.isWindows() || (NTFS && JAVA7) || ...);
        
        String buildDateTime = "2012-12-21_04-02-28";
        int buildNumber = 155;
        
        StreamTaskListener l = StreamTaskListener.fromStdout();

        File tempDir = Util.createTempDir();    
        File buildDir = new File(tempDir, buildDateTime);
        assertEquals(true, buildDir.mkdir());
        File buildDirSymLink = new File(tempDir, Integer.toString(buildNumber));
        
        try {
        	buildDir.mkdir();
         
            Util.createSymlink(tempDir, buildDir.getAbsolutePath(), buildDirSymLink.getName(), l);
            long time = Run.parseTimestampFromBuildDir(buildDirSymLink);
            assertEquals(buildDateTime, Run.ID_FORMATTER.get().format(new Date(time)));
        } finally {
            Util.deleteRecursive(tempDir);
        }
    }

}
