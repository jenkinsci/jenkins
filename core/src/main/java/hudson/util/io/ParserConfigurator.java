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
import hudson.model.Hudson;
import org.dom4j.io.SAXReader;

/**
 * Configures XML parsers to be used in various context of Jenkins.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.416
 */
public abstract class ParserConfigurator implements ExtensionPoint {
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
        return Hudson.getInstance().getExtensionList(ParserConfigurator.class);
    }

    public static void applyConfiguration(SAXReader reader, Object context) {
        for (ParserConfigurator pc : all())
            pc.configure(reader,context);
    }
}
