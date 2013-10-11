/*
 * The MIT License
 *
 * Copyright (c) 2013, Red Hat, Inc.
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

import hudson.model.View;
import jenkins.model.Jenkins;

import org.kohsuke.MetaInfServices;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Refers to {@link View} by its name.
 *
 * @author ogondza
 * @since TODO
 */
@MetaInfServices
public class ViewOptionHandler extends OptionHandler<View> {

    public ViewOptionHandler(CmdLineParser parser, OptionDef option, Setter<View> setter) {

        super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {

        String viewName = params.getParameter(0);

        final View view = Jenkins.getInstance().getView(viewName);
        if (view == null) throw new CmdLineException(owner, "No such view '" + viewName + "'");

        setter.addValue(view);
        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {

        return "VIEW";
    }
}
