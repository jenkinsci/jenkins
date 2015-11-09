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

import hudson.Extension;
import hudson.cli.CLI;
import hudson.cli.CLICommand;
import hudson.cli.CliPort;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.PermalinkProjectAction;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.util.IOUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.security.ysoserial.payloads.CommonsCollections1;
import jenkins.security.ysoserial.payloads.CommonsCollections2;
import jenkins.security.ysoserial.payloads.Groovy1;
import jenkins.security.ysoserial.payloads.ObjectPayload;
import jenkins.security.ysoserial.payloads.Payload;
import jenkins.security.ysoserial.payloads.Spring1;
import jenkins.util.Timer;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.PresetData;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

public class Security218BlackBoxTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY) // allow who-am-i to run all the way to completion
    @Test
    public void probeNoPayload() throws Exception {
        probe(null);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY) // allow who-am-i to run all the way to completion
    @Test
    public void probeCommonsCollections1() throws Exception {
        probe(Payload.CommonsCollections1);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY) // allow who-am-i to run all the way to completion
    @Test
    public void probeCommonsCollections2() throws Exception {
        probe(Payload.CommonsCollections2);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY) // allow who-am-i to run all the way to completion
    @Test
    public void probeGroovy1() throws Exception {
        probe(Payload.Groovy1);
    }
    
    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY) // allow who-am-i to run all the way to completion
    @Test
    public void probeSpring1() throws Exception {
        probe(Payload.Spring1);
    }
    
    private void probe(Payload payload) throws Exception {
        final ServerSocket proxySocket = new ServerSocket(0);
        final String localhost = r.getURL().getHost();
        
        File file = File.createTempFile("security-218", payload + "-payload");
        
        // Bypassing _main because it does nothing interesting here.
        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
        int exitCode = new CLI(r.getURL()).execute("send-payload", 
                payload.toString(), "rm " + file.getAbsolutePath());
        assertEquals("CLI Command execution failed", exitCode, 0);
        assertTrue("Payload should not delete the file " + file, file.exists());
        file.delete();
    }
    
    @TestExtension("probeCommonsCollections1")
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
            channel.call(callable);
            return 0;
        }

        @Override
        protected void printUsageSummary(PrintStream stderr) {
            stderr.println("Sends a payload over the channel");
        }
    }
    
    public static class PayloadCaller implements Callable<Void, Exception> {

        private final Payload payload;
        private final String command;

        public PayloadCaller(Payload payload, String command) {
            this.payload = payload;
            this.command = command;
        }
        
        @Override
        public Void call() throws Exception {
            final Object ysoserial = payload.getPayloadClass().newInstance().getObject(command);
            
            // Invoke backward call
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
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // Do nothing
        }
        
    }

}
