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
package hudson.maven.agent;

import hudson.remoting.Callable;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

import java.io.IOException;

/**
 * Starts Maven CLI. Remotely executed.
 *
 * @author Kohsuke Kawaguchi
 */
public class RunCommand implements Callable {
    private final String[] args;

    public RunCommand(String[] args) {
        this.args = args;
    }

    public Object call() throws Throwable {
        // return Main.class.getClassLoader().toString();

        PluginManagerInterceptor.setListener(new PluginManagerListener() {
            public void preExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException, AbortException {
                System.out.println("***** "+exec.getMojoDescriptor().getGoal());
            }

            public void postExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException, AbortException {
                System.out.println("==== "+exec.getMojoDescriptor().getGoal());
            }

            public void onReportGenerated(MavenReport report, MojoExecution mojoExecution, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException {
                System.out.println("//// "+report);
            }
        });

        return new Integer(Main.launch(args));
    }
}
