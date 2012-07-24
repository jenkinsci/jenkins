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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause.UserIdCause;
import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.ParameterDefinition;
import hudson.Extension;
import hudson.AbortException;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult.Change;
import hudson.util.EditDistance;
import hudson.util.StreamTaskListener;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.io.PrintStream;

import jenkins.model.Jenkins;

/**
 * Builds a job, and optionally waits until its completion.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class BuildCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.BuildCommand_ShortDescription();
    }

    @Argument(metaVar="JOB",usage="Name of the job to build",required=true)
    public AbstractProject<?,?> job;

    @Option(name="-s",usage="Wait until the completion/abortion of the command")
    public boolean sync = false;

    @Option(name="-w",usage="Wait until the start of the command")
    public boolean wait = false;

    @Option(name="-c",usage="Check for SCM changes before starting the build, and if there's no change, exit without doing a build")
    public boolean checkSCM = false;

    @Option(name="-p",usage="Specify the build parameters in the key=value format.")
    public Map<String,String> parameters = new HashMap<String, String>();

    @Option(name="-v",usage="Prints out the console output of the build. Use with -s")
    public boolean consoleOutput = false;

    protected int run() throws Exception {
        job.checkPermission(Item.BUILD);

        ParametersAction a = null;
        if (!parameters.isEmpty()) {
            ParametersDefinitionProperty pdp = job.getProperty(ParametersDefinitionProperty.class);
            if (pdp==null)
                throw new AbortException(job.getFullDisplayName()+" is not parameterized but the -p option was specified");

            List<ParameterValue> values = new ArrayList<ParameterValue>(); 

            for (Entry<String, String> e : parameters.entrySet()) {
                String name = e.getKey();
                ParameterDefinition pd = pdp.getParameterDefinition(name);
                if (pd==null)
                    throw new AbortException(String.format("\'%s\' is not a valid parameter. Did you mean %s?",
                            name, EditDistance.findNearest(name, pdp.getParameterDefinitionNames())));
                values.add(pd.createValue(this,e.getValue()));
            }
            
            a = new ParametersAction(values);
        }

        if (checkSCM) {
            if (job.poll(new StreamTaskListener(stdout, getClientCharset())).change == Change.NONE) {
                return 0;
            }
        }

        QueueTaskFuture<? extends AbstractBuild> f = job.scheduleBuild2(0, new CLICause(Jenkins.getAuthentication().getName()), a);

        if (wait || sync) {
            AbstractBuild b = f.waitForStart();    // wait for the start
            stdout.println("Started "+b.getFullDisplayName());

            if (sync) {
                if (consoleOutput) {
                    b.writeWholeLogTo(stdout);
                }
                // TODO: should we abort the build if the CLI is cancelled?
                f.get();    // wait for the completion
                stdout.println("Completed "+b.getFullDisplayName()+" : "+b.getResult());
                return b.getResult().ordinal;
            }
        }

        return 0;
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(
            "Starts a build, and optionally waits for a completion.\n" +
            "Aside from general scripting use, this command can be\n" +
            "used to invoke another job from within a build of one job.\n" +
            "With the -s option, this command changes the exit code based on\n" +
            "the outcome of the build (exit code 0 indicates a success.)\n" +
            "With the -c option, a build will only run if there has been\n" +
            "an SCM change"
        );
    }

    public static class CLICause extends UserIdCause {
    	
    	private String startedBy;
    	
    	public CLICause(){
    		startedBy = "unknown";
    	}
    	
    	public CLICause(String startedBy){
    		this.startedBy = startedBy;
    	}
    	
        @Override
        public String getShortDescription() {
            return "Started by command line by " + startedBy;
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println("Started by command line by " +
                    ModelHyperlinkNote.encodeTo("/user/"+getUserId(), getUserName()));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CLICause;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }
}

