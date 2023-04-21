package hudson.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import hudson.ChannelRule;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTree.OSProcess;
import hudson.util.ProcessTree.ProcessCallable;
import java.io.IOException;
import java.io.Serializable;
import jenkins.security.MasterToSlaveCallable;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProcessTreeTest {

    @Rule public ChannelRule channels = new ChannelRule();

    static class  Tag implements Serializable {
        ProcessTree tree;
        OSProcess p;
        int id;
        private static final long serialVersionUID = 1L;
    }

    @Test public void remoting() throws Exception {
        Assume.assumeFalse("on some platforms where we fail to list any processes", ProcessTree.get() == ProcessTree.DEFAULT);

        Tag t = channels.french.call(new MyCallable());

        // make sure the serialization preserved the reference graph
        assertSame(t.p.getTree(), t.tree);

        // verify that some remote call works
        t.p.getEnvironmentVariables();

        // it should point to the same object
        assertEquals(t.id, t.p.getPid());

        t.p.act(new ProcessCallableImpl());
    }

    private static class MyCallable extends MasterToSlaveCallable<Tag, IOException> implements Serializable {
        @Override
        public Tag call() {
            Tag t = new Tag();
            t.tree = ProcessTree.get();
            t.p = t.tree.iterator().next();
            t.id = t.p.getPid();
            return t;
        }

        private static final long serialVersionUID = 1L;
    }

    private static class ProcessCallableImpl implements ProcessCallable<Void> {
        @Override
        public Void invoke(OSProcess process, VirtualChannel channel) {
            assertNotNull(process);
            assertNotNull(channel);
            return null;
        }
    }
}
