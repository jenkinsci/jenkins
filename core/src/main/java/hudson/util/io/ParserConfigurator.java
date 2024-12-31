/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package hudson.util.io;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Channel;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.security.AgentToMasterCallable;
import org.dom4j.io.SAXReader;

/**
 * Configures XML parsers to be used for various XML parsing activities inside Jenkins.
 *
 * <p>
 * XML parsing is a complex enough activity that often certain degree of customization of the
 * parsing behaviour is desired. This extension point enables that. To avoid creating
 * new extension point for each different parsing scene, this extension point takes the type-less
 * "context" argument, which should identify the context of the parse by type.
 *
 * <p>
 * This extension point is added late in the game, so existing XML parsing behaviour should
 * be retrofitted to use this as we find them. Similarly, additional overloaded versions are likely
 * needed to support SAX, JAXP, and other means of parsing.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.416
 * @deprecated No longer used.
 */
@Deprecated
public abstract class ParserConfigurator implements ExtensionPoint, Serializable {
    private static final long serialVersionUID = -2523542286453177108L;

    /**
     * Configures the given {@link SAXReader}
     *
     * @param context
     *      Object that represents the context in which the parser is used.
     *      It is up to the caller to decide what to pass in here.
     */
    public void configure(SAXReader reader, Object context) {}

    /**
     * Returns all the registered {@link ParserConfigurator}s.
     */
    public static ExtensionList<ParserConfigurator> all() {
        return ExtensionList.lookup(ParserConfigurator.class);
    }

    public static void applyConfiguration(SAXReader reader, Object context) throws IOException, InterruptedException {
        Collection<ParserConfigurator> all = Collections.emptyList();

        if (Jenkins.getInstanceOrNull() == null) {
            Channel ch = Channel.current();
            if (ch != null)
                all = ch.call(new GetParserConfigurators());
        } else
            all = all();
        for (ParserConfigurator pc : all)
            pc.configure(reader, context);
    }

    private static class GetParserConfigurators extends AgentToMasterCallable<Collection<ParserConfigurator>, IOException> {
        private static final long serialVersionUID = -2178106894481500733L;

        @Override
        public Collection<ParserConfigurator> call() throws IOException {
            return new ArrayList<>(all());
        }
    }
}
