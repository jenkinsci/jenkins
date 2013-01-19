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
package hudson.maven;

import org.apache.maven.reporting.MavenReport;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

/**
 * Information about {@link MavenReport} that was executed.
 *
 * <p>
 * Since {@link MavenReport} is always a {@link Mojo} (even though the interface
 * inheritance is not explicitly defined), this class extends from {@link MojoInfo}.
 *
 * <p>
 * This object provides additional convenience methods that only make sense for {@link MavenReport}.
 *
 * @author Kohsuke Kawaguchi
 * @see MojoInfo
 */
public final class MavenReportInfo extends MojoInfo {
    /**
     * The fully-populated {@link MavenReport} object. The same object as 
     * {@link #mojo} but in the right type. Never null.
     */
    public final MavenReport report;

    public MavenReportInfo(MojoExecution mojoExecution, MavenReport mojo, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator,
            long mojoStartTime) {
        super(mojoExecution, (Mojo)mojo, configuration, expressionEvaluator,mojoStartTime);
        this.report = mojo;
    }
}
