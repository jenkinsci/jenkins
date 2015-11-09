/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package jenkins.security;

import hudson.cli.CLI;
import hudson.cli.CLICommand;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import java.io.File;
import java.io.PrintStream;
import jenkins.security.ysoserial.payloads.Payload;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.PresetData;
import org.kohsuke.args4j.Argument;

public class Security218BlackBoxTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeCommonsCollections1() throws Exception {
        probe(Payload.CommonsCollections1, PayloadCaller.EXIT_CODE_REJECTED);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeCommonsCollections2() throws Exception {
        //TODO: Payload content issue
        probe(Payload.CommonsCollections2, -1);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    public void probeGroovy1() throws Exception {
        probe(Payload.Groovy1, PayloadCaller.EXIT_CODE_REJECTED);
    }
    
    //TODO: Fix the conversion layer (not urgent)
    // There is an issue in the conversion layer after the migration to another XALAN namespace
    // with newer libs. SECURITY-218 does not apper in this case OOTB anyway
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test
    @Issue("SECURITY-218")
    @Ignore
    public void probeSpring1() throws Exception {
        probe(Payload.Spring1, PayloadCaller.EXIT_CODE_OK);
    }
    
    private void probe(Payload payload, int expectedResultCode) throws Exception {
        File file = File.createTempFile("security-218", payload + "-payload");
        File moved = new File(file.getAbsolutePath() + "-moved");
        
        // Bypassing _main because it does nothing interesting here.
        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
        int exitCode = new CLI(r.getURL()).execute("send-payload",
                payload.toString(), "mv " + file.getAbsolutePath() + " " + moved.getAbsolutePath());
        assertEquals("Unexpected result code.", expectedResultCode, exitCode);
        assertTrue("Payload should not invoke the move operation " + file, !moved.exists());
        file.delete();
    }
    
    @TestExtension()
    public static class SendPayloadCommand extends CLICommand {

        @Override
        public String getShortDescription() {
            return hudson.cli.Messages.ConsoleCommand_ShortDescription();
        }

        @Argument(metaVar = "payload", usage = "ID of the payload", required = true, index = 0)
        public String payload;
        
        @Argument(metaVar = "command", usage = "Command to be launched by the payload", required = true, index = 1)
        public String command;
        

        protected int run() throws Exception {
            Payload payloadItem = Payload.valueOf(this.payload);
            PayloadCaller callable = new PayloadCaller(payloadItem, command);
            return channel.call(callable);
        }

        @Override
        protected void printUsageSummary(PrintStream stderr) {
            stderr.println("Sends a payload over the channel");
        }
    }

    public static class PayloadCaller implements Callable<Integer, Exception> {

        private final Payload payload;
        private final String command;

        public static final int EXIT_CODE_OK = 0;
        public static final int EXIT_CODE_REJECTED = 42;
        public static final int EXIT_CODE_ASSIGNMENT_ISSUE = 43;

        public PayloadCaller(Payload payload, String command) {
            this.payload = payload;
            this.command = command;
        }
        
        @Override
        public Integer call() throws Exception {
            final Object ysoserial = payload.getPayloadClass().newInstance().getObject(command);
            
            // Invoke backward call
            try {
                Channel.current().call(new Callable<String, Exception>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String call() throws Exception {
                        // We don't care what happens here. Object should be sent over the channel
                        return ysoserial.toString();
                    }

                    @Override
                    public void checkRoles(RoleChecker checker) throws SecurityException {
                        // do nothing
                    }
                });
            } catch (Exception ex) {
                Throwable cause = ex;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (cause instanceof SecurityException) {
                    // It should happen if the remote chanel reject a class.
                    // That's what we have done in SECURITY-218 => may be OK
                    if (cause.getMessage().contains("Rejected")) {
                        // OK
                        return PayloadCaller.EXIT_CODE_REJECTED;
                    } else {
                        // Something wrong
                        throw ex;
                    }
                }

                if (cause.getMessage().contains("cannot be cast to java.util.Set")) {
                    // We ignore this exception, because there is a known issue in the test payload
                    // CommonsCollections1, CommonsCollections2 and Groovy1 fail witth this error,
                    // but actually it means that the conversion has been triggered
                    return EXIT_CODE_ASSIGNMENT_ISSUE;
                } else {
                    throw ex;
                }
            }
            return EXIT_CODE_OK;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // Do nothing
        }
        
    }

}
