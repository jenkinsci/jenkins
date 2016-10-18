/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package jenkins;

import java.io.IOException;
import java.util.List;

import hudson.PluginWrapper.Dependency;
import hudson.Util;

/**
 * Exception thrown if plugin resolution fails due to missing dependencies
 * 
 * @author Carlos Sanchez
 * @since 2.4
 *
 */
public class MissingDependencyException extends IOException {

    private String pluginShortName;
    private List<Dependency> missingDependencies;

    public MissingDependencyException(String pluginShortName, List<Dependency> missingDependencies) {
        super("One or more dependencies could not be resolved for " + pluginShortName + " : "
                + Util.join(missingDependencies, ", "));
        this.pluginShortName = pluginShortName;
        this.missingDependencies = missingDependencies;
    }

    public List<Dependency> getMissingDependencies() {
        return missingDependencies;
    }

    public String getPluginShortName() {
        return pluginShortName;
    }

}
