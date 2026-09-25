/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.cli.declarative.OptionHandlerExtension;
import jenkins.model.ParameterizedJobMixIn;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Setter;

/**
 * Refer to {@link jenkins.model.ParameterizedJobMixIn.ParameterizedJob} by its name.
 */
@Restricted(DoNotUse.class)
@OptionHandlerExtension
@SuppressWarnings("rawtypes")
public class ParameterizedJobOptionHandler extends GenericItemOptionHandler<ParameterizedJobMixIn.ParameterizedJob> {

    public ParameterizedJobOptionHandler(CmdLineParser parser, OptionDef option, Setter<ParameterizedJobMixIn.ParameterizedJob> setter) {
        super(parser, option, setter);
    }

    @Override
    protected Class<ParameterizedJobMixIn.ParameterizedJob> type() {
        return ParameterizedJobMixIn.ParameterizedJob.class;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "JOB";
    }

}
