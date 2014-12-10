package hudson.util;

import hudson.ChannelTestCase;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTree.OSProcess;
import hudson.util.ProcessTree.ProcessCallable;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProcessTreeTest extends ChannelTestCase {
    static class  Tag implements Serializable {
        ProcessTree tree;
        OSProcess p;
        int id;
        private static final long serialVersionUID = 1L;
    }
    
    public void testRemoting() throws Exception {
        // on some platforms where we fail to list any processes, this test will just not work
        if (ProcessTree.get()==ProcessTree.DEFAULT)
            return;

        Tag t = french.call(new MyCallable());

        // make sure the serialization preserved the reference graph
        assertSame(t.p.getTree(), t.tree);

        // verify that some remote call works
        t.p.getEnvironmentVariables();

        // it should point to the same object
        assertEquals(t.id,t.p.getPid());

        t.p.act(new ProcessCallableImpl());
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
