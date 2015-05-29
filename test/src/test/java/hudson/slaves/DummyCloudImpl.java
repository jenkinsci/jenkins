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
package hudson.slaves;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner.PlannedNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * {@link Cloud} implementation useful for testing.
 *
 * <p>
 * This implementation launches "java -jar slave.jar" on the localhost when provisioning a new slave.
 *
 * @author Kohsuke Kawaguchi
*/
public class DummyCloudImpl extends Cloud {
    private final transient JenkinsRule rule;

    /**
     * Configurable delay between the {@link Cloud#provision(Label,int)} and the actual launch of a slave,
     * to emulate a real cloud that takes some time for provisioning a new system.
     *
     * <p>
     * Number of milliseconds.
     */
    private final int delay;

    // stats counter to perform assertions later
    public int numProvisioned;

    /**
     * Only reacts to provisioning for this label.
     */
    public Label label;

    public DummyCloudImpl(JenkinsRule rule, int delay) {
        super("test");
        this.rule = rule;
        this.delay = delay;
    }

    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        List<PlannedNode> r = new ArrayList<PlannedNode>();
        if(label!=this.label)   return r;   // provisioning impossible

        while(excessWorkload>0) {
            System.out.println("Provisioning");
            numProvisioned++;
            Future<Node> f = Computer.threadPoolForRemoting.submit(new Launcher(delay));
            r.add(new PlannedNode(name+" #"+numProvisioned,f,1));
            excessWorkload-=1;
        }
        return r;
    }

    public boolean canProvision(Label label) {
        return label==this.label;
    }

    private final class Launcher implements Callable<Node> {
        private final long time;
        /**
         * This is so that we can find out the status of Callable from the debugger.
         */
        private volatile Computer computer;

        private Launcher(long time) {
            this.time = time;
        }

        public Node call() throws Exception {
            // simulate the delay in provisioning a new slave,
            // since it's normally some async operation.
            Thread.sleep(time);
            
            System.out.println("launching slave");
            DumbSlave slave = rule.createSlave(label);
            computer = slave.toComputer();
            computer.connect(false).get();
            synchronized (DummyCloudImpl.this) {
                System.out.println(computer.getName()+" launch"+(computer.isOnline()?"ed successfully":" failed"));
                System.out.println(computer.getLog());
            }
            return slave;
        }
    }

    public Descriptor<Cloud> getDescriptor() {
        throw new UnsupportedOperationException();
    }
}
