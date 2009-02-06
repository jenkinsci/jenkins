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

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.doxia.sink.Sink;

import java.io.IOException;
import java.util.Locale;

/**
 * Receives notification from {@link PluginManagerInterceptor},
 * before and after a mojo is executed.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface PluginManagerListener {
    /**
     * Called right before {@link Mojo#execute()} is invoked.
     *
     * @param mojo
     *      The mojo object to be invoked. All its configuration values have been populated.
     */
    void preExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException;

    /**
     * Called after the mojo has finished executing.
     *
     * @param project
     *      Same object as passed to {@link #preExecute}.
     * @param exec
     *      Same object as passed to {@link #preExecute}.
     * @param mojo
     *      The mojo object that executed.
     * @param mergedConfig
     *      Same object as passed to {@link #preExecute}.
     * @param eval
     *      Same object as passed to {@link #preExecute}.
     * @param exception
     *      If mojo execution failed with {@link MojoFailureException} or
     *      {@link MojoExecutionException}, this method is still invoked
     *      with those error objects.
     *      If mojo executed successfully, this parameter is null.
     */
    void postExecute(MavenProject project, MojoExecution exec, Mojo mojo, PlexusConfiguration mergedConfig, ExpressionEvaluator eval, Exception exception) throws IOException, InterruptedException;

    /**
     * Called after a successful execution of {@link MavenReport#generate(Sink, Locale)}.
     *
     * @param report
     *      The {@link MavenReport} object that just successfully completed.
     */
    void onReportGenerated(MavenReport report, MojoExecution mojoExecution, PlexusConfiguration mergedConfig, ExpressionEvaluator eval) throws IOException, InterruptedException;
}
