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
package hudson.model;

import hudson.Proc;
import hudson.util.DecodingStream;
import hudson.util.DualOutputStream;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

/**
 * {@link Run} for {@link ExternalJob}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ExternalRun extends Run<ExternalJob,ExternalRun> {
    /**
     * Loads a run from a log file.
     */
    ExternalRun(ExternalJob owner, File runDir) throws IOException {
        super(owner,runDir);
    }

    /**
     * Creates a new run.
     */
    ExternalRun(ExternalJob project) throws IOException {
        super(project);
    }

    /**
     * Instead of performing a build, run the specified command,
     * record the log and its exit code, then call it a build.
     */
    public void run(final String[] cmd) {
        run(new Runner() {
            public Result run(BuildListener listener) throws Exception {
                Proc proc = new Proc.LocalProc(cmd,getEnvironment(listener),System.in,new DualOutputStream(System.out,listener.getLogger()));
                return proc.join()==0?Result.SUCCESS:Result.FAILURE;
            }

            public void post(BuildListener listener) {
                // do nothing
            }

            public void cleanUp(BuildListener listener) {
                // do nothing
            }
        });
    }

    /**
     * Instead of performing a build, accept the log and the return code
     * from a remote machine.
     *
     * <p>
     * The format of the XML is:
     *
     * <pre><xmp>
     * <run>
     *  <log>...console output...</log>
     *  <result>exit code</result>
     * </run>
     * </xmp></pre>
     */
    public void acceptRemoteSubmission(final Reader in) throws IOException {
        final long[] duration = new long[1];
        run(new Runner() {
            public Result run(BuildListener listener) throws Exception {
                PrintStream logger = new PrintStream(new DecodingStream(listener.getLogger()));

                XmlPullParser xpp = new MXParser();
                xpp.setInput(in);
                xpp.nextTag();  // get to the <run>
                xpp.nextTag();  // get to the <log>
                charset=xpp.getAttributeValue(null,"content-encoding");
                while(xpp.nextToken()!=XmlPullParser.END_TAG) {
                    int type = xpp.getEventType();
                    if(type==XmlPullParser.TEXT
                    || type==XmlPullParser.CDSECT)
                        logger.print(xpp.getText());
                }
                xpp.nextTag(); // get to <result>

                Result r = Integer.parseInt(xpp.nextText())==0?Result.SUCCESS:Result.FAILURE;

                xpp.nextTag();  // get to <duration> (optional)
                if(xpp.getEventType()==XmlPullParser.START_TAG
                && xpp.getName().equals("duration")) {
                    duration[0] = Long.parseLong(xpp.nextText());
                }

                return r;
            }

            public void post(BuildListener listener) {
                // do nothing
            }

            public void cleanUp(BuildListener listener) {
                // do nothing
            }
        });

        if(duration[0]!=0) {
            super.duration = duration[0];
            // save the updated duration
            save();
        }
    }

}
