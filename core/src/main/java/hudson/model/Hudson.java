/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.
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

import hudson.PluginManager;
import hudson.cli.declarative.CLIResolver;
import org.jvnet.hudson.reactor.ReactorException;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;

public class Hudson extends Jenkins {

    @CLIResolver
    public static Hudson getInstance() {
        return (Hudson)Jenkins.getInstance();
    }

    public Hudson(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root,context,null);
    }

    public Hudson(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        super(root, context, pluginManager);
    }

    static {
        XSTREAM.alias("hudson",Hudson.class);
    }
}
