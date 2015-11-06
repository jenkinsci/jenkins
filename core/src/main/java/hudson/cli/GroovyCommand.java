/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import groovy.lang.GroovyShell;
import groovy.lang.Binding;
import hudson.cli.util.ScriptLoader;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.Run;
import hudson.Extension;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes the specified groovy script.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.GroovyCommand_ShortDescription();
    }

    @Argument(metaVar="SCRIPT",usage="Script to be executed. File, URL or '=' to represent stdin.")
    public String script;

    /**
     * Remaining arguments.
     */
    @Argument(metaVar="ARGUMENTS", index=1, usage="Command line arguments to pass into script.")
    public List<String> remaining = new ArrayList<String>();

    protected int run() throws Exception {
        // this allows the caller to manipulate the JVM state, so require the execute script privilege.
        Jenkins.getInstance().checkPermission(Jenkins.RUN_SCRIPTS);

        Binding binding = new Binding();
        binding.setProperty("out",new PrintWriter(stdout,true));
        binding.setProperty("stdin",stdin);
        binding.setProperty("stdout",stdout);
        binding.setProperty("stderr",stderr);
        binding.setProperty("channel",channel);
        String j = getClientEnvironmentVariable("JOB_NAME");
        if (j!=null) {
            Item job = Jenkins.getInstance().getItemByFullName(j);
            binding.setProperty("currentJob", job);
            String b = getClientEnvironmentVariable("BUILD_NUMBER");
            if (b!=null && job instanceof AbstractProject) {
                Run r = ((AbstractProject) job).getBuildByNumber(Integer.parseInt(b));
                binding.setProperty("currentBuild", r);
            }
        }

        GroovyShell groovy = new GroovyShell(Jenkins.getInstance().getPluginManager().uberClassLoader, binding);
        groovy.run(loadScript(),"RemoteClass",remaining.toArray(new String[remaining.size()]));
        return 0;
    }

    /**
     * Loads the script from the argument.
     */
    private String loadScript() throws CmdLineException, IOException, InterruptedException {
        if(script==null)
            throw new CmdLineException(null, "No script is specified");
        if (script.equals("="))
            return IOUtils.toString(stdin);

        return checkChannel().call(new ScriptLoader(script));
    }
}

