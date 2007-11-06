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
                Proc proc = new Proc.LocalProc(cmd,getEnvVars(),System.in,new DualOutputStream(System.out,listener.getLogger()));
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
