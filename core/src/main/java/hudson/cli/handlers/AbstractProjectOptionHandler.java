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
package hudson.cli.handlers;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.MetaInfServices;

/**
 * Refer to {@link AbstractProject} by its name.
 *
 * @author Kohsuke Kawaguchi
 */
@MetaInfServices
public class AbstractProjectOptionHandler extends OptionHandler<AbstractProject> {
    public AbstractProjectOptionHandler(CmdLineParser parser, OptionDef option, Setter<AbstractProject> setter) {
        super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
        Hudson h = Hudson.getInstance();
        String src = params.getParameter(0);

        AbstractProject s = h.getItemByFullName(src,AbstractProject.class);
        if (s==null)
            throw new CmdLineException(owner, "No such job '"+src+"' perhaps you meant "+ AbstractProject.findNearest(src)+"?");
        setter.addValue(s);
        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "JOB";
    }
}
