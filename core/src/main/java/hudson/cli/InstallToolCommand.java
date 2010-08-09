/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.cli;

import hudson.Extension;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Hudson;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.EnvironmentSpecific;
import hudson.model.Item;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.util.EditDistance;
import hudson.util.StreamTaskListener;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import org.kohsuke.args4j.Argument;

/**
 * Performs automatic tool installation on demand.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class InstallToolCommand extends CLICommand {
    @Argument(index=0,metaVar="KIND",usage="The type of the tool to install, such as 'Ant'")
    public String toolType;

    @Argument(index=1,metaVar="NAME",usage="The name of the tool to install, as you've entered in the Hudson system configuration")
    public String toolName;

    public String getShortDescription() {
        return "Performs automatic tool installation, and print its location to stdout. Can be only called from inside a build";
    }

    protected int run() throws Exception {
        Hudson h = Hudson.getInstance();
        h.checkPermission(Hudson.READ);

        // where is this build running?
        BuildIDs id = channel.call(new BuildIDs());

        if (!id.isComplete())
            throw new AbortException("This command can be only invoked from a build executing inside Hudson");

        AbstractProject p = Hudson.getInstance().getItemByFullName(id.job, AbstractProject.class);
        if (p==null)
            throw new AbortException("No such job found: "+id.job);
        p.checkPermission(Item.CONFIGURE);

        List<String> toolTypes = new ArrayList<String>();
        for (ToolDescriptor<?> d : ToolInstallation.all()) {
            toolTypes.add(d.getDisplayName());
            if (d.getDisplayName().equals(toolType)) {
                List<String> toolNames = new ArrayList<String>();
                for (ToolInstallation t : d.getInstallations()) {
                    toolNames.add(t.getName());
                    if (t.getName().equals(toolName))
                        return install(t, id, p);
                }

                // didn't find the right tool name
                error(toolNames, toolName, "name");
            }
        }

        // didn't find the tool type
        error(toolTypes, toolType, "type");

        // will never be here
        throw new AssertionError();
    }

    private int error(List<String> candidates, String given, String noun) throws AbortException {
        if (given ==null)
            throw new AbortException("No tool "+ noun +" was specified. Valid values are "+candidates.toString());
        else
            throw new AbortException("Unrecognized tool "+noun+". Perhaps you meant '"+ EditDistance.findNearest(given,candidates)+"'?");
    }

    /**
     * Performs an installation.
     */
    private int install(ToolInstallation t, BuildIDs id, AbstractProject p) throws IOException, InterruptedException {

        Run b = p.getBuildByNumber(Integer.parseInt(id.number));
        if (b==null)
            throw new AbortException("No such build: "+id.number);

        Executor exec = b.getExecutor();
        if (exec==null)
            throw new AbortException(b.getFullDisplayName()+" is not building");

        Node node = exec.getOwner().getNode();

        if (t instanceof NodeSpecific) {
            NodeSpecific n = (NodeSpecific) t;
            t = (ToolInstallation)n.forNode(node,new StreamTaskListener(stderr));
        }
        if (t instanceof EnvironmentSpecific) {
            EnvironmentSpecific e = (EnvironmentSpecific) t;
            t = (ToolInstallation)e.forEnvironment(EnvVars.getRemote(channel));
        }
        stdout.println(t.getHome());
        return 0;
    }

    private static final class BuildIDs implements Callable<BuildIDs, IOException> {
        String job,number,id;

        public BuildIDs call() throws IOException {
            job = System.getenv("JOB_NAME");
            number = System.getenv("BUILD_NUMBER");
            id = System.getenv("BUILD_ID");
            return this;
        }

        boolean isComplete() {
            return job!=null && number!=null && id!=null;
        }

        private static final long serialVersionUID = 1L;
    }
}
