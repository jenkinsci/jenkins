/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.model;

/**
 * An {@link Action} that can return information about the health of the Job.
 * <p>
 * The health of a {@link Job} is the lowest health returned by the HealthReportingAction's
 * that contribute to the Job.
 * <p>
 * When a particular Action "wins", it's gets to determine the icon and associated description.
 * <p>
 * This provides a mechanism for plugins to present important summary information
 * on the {@link View} main pages without eating up significant screen real estate.
 *
 * @author Stephen Connolly
 * @since 1.115
 */
public interface HealthReportingAction extends Action {
    /**
     * Get this {@link Action}'s {@link HealthReport}.
     *
     * @return
     *     The health report for this instance of the Action or 
     *     <code>null</code> if the Action does not want to 
     *     contribute a HealthReport.
     */
    HealthReport getBuildHealth();
}
