/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.model.Item;
import hudson.model.Items;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Refers to an {@link Item} by its name.
 * May be subclassed to handle specific kinds of items.
 * (Use {@code @MetaInfServices(OptionHandler.class)} to register the subclass.)
 * @param <T> the kind of item being handled
 * @since 1.538
 */
public abstract class GenericItemOptionHandler<T extends Item> extends OptionHandler<T> {

    protected GenericItemOptionHandler(CmdLineParser parser, OptionDef option, Setter<T> setter) {
        super(parser, option, setter);
    }

    protected abstract Class<T> type();

    @Override public int parseArguments(Parameters params) throws CmdLineException {
        Jenkins j = Jenkins.getInstance();
        String src = params.getParameter(0);
        T s = j.getItemByFullName(src, type());
        if (s == null) {
            T nearest = Items.findNearest(type(), src, j);
            if (nearest != null) {
                throw new CmdLineException(owner, "No such job '" + src + "'; perhaps you meant '" + nearest.getFullName() + "'?");
            } else {
                throw new CmdLineException(owner, "No such job '" + src + "'");
            }
        }
        setter.addValue(s);
        return 1;
    }

    @Override public String getDefaultMetaVariable() {
        return "ITEM";
    }

}
