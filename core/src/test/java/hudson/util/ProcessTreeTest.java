package hudson.util;

import hudson.ChannelRule;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTree.OSProcess;
import hudson.util.ProcessTree.ProcessCallable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import jenkins.security.MasterToSlaveCallable;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProcessTreeTest {

    @Rule public ChannelRule channels = new ChannelRule();

    static class Tag implements Serializable {
        ProcessTree tree;
        OSProcess p;
        int id;
        private static final long serialVersionUID = 1L;
    }

    private Process process;

    @After
    public void tearDown() throws Exception {
        if (null != process)
            process.destroy();
    }

    @Test public void remoting() throws Exception {
        // on some platforms where we fail to list any processes, this test will just not work
        if (ProcessTree.get()==ProcessTree.DEFAULT)
            return;

        Tag t = channels.french.call(new MyCallable());

        // make sure the serialization preserved the reference graph
        assertSame(t.p.getTree(), t.tree);

        // verify that some remote call works
        t.p.getEnvironmentVariables();

        // it should point to the same object
        assertEquals(t.id,t.p.getPid());

        t.p.act(new ProcessCallableImpl());
    }

    @Test
    public void testKillingWhiteList() throws Exception {
        // on some platforms where we fail to list any processes, this test will
        // just not work
        if (ProcessTree.get() == ProcessTree.DEFAULT)
            return;

        // kick off a process we (shouldn't) kill
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().put("cookie", "testKeepDaemonsAlive");
        List<String> whitelist;
        if (File.pathSeparatorChar == ';') {
            pb.command("cmd");
            whitelist = ImmutableList.of("cmd");
        } else {
            pb.command("sleep", "5m");
            whitelist = ImmutableList.of("sleep");
        }

        process = pb.start();

        ProcessTree processTree = ProcessTree.get();
        processTree.killAll(ImmutableMap.of("cookie", "testKeepDaemonsAlive"),whitelist);
        try {
            process.exitValue();
            fail("Process should have been excluded from the killing");
        } catch (IllegalThreadStateException e) {
            // Means the process is still running
        }

        processTree.killAll(ImmutableMap.of("cookie", "testKeepDaemonsAlive"),ImmutableList.of("nothing"));
        try {
            process.exitValue();
        } catch (IllegalThreadStateException e) {
            // Means the process is still running
            fail("Process should have been killed this time (" + e.getMessage() + ")");
        }
    }

    private static class MyCallable extends MasterToSlaveCallable<Tag, IOException> implements Serializable {
        public Tag call() throws IOException {
            Tag t = new Tag();
            t.tree = ProcessTree.get();
            t.p = t.tree.iterator().next();
            t.id = t.p.getPid();
            return t;
        }

        private static final long serialVersionUID = 1L;
    }

    private static class ProcessCallableImpl implements ProcessCallable<Void> {
        public Void invoke(OSProcess process, VirtualChannel channel) throws IOException {
            assertNotNull(process);
            assertNotNull(channel);
            return null;
        }
    }
}
