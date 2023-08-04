/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees, Inc.
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

package jenkins.link;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Extension point for providing console urls
 * @since TODO
 */
public interface ConsoleURLProvider extends ExtensionPoint {

    String getConsoleURL(Run<?, ?> run);

    /**
     * Retrieve all implementations of ConsoleURLProvider.
     */
    static ExtensionList<ConsoleURLProvider> all() {
        return ExtensionList.lookup(ConsoleURLProvider.class);
    }

    /**
     * Retrieve the highest registered ordinal implementation.
     */
    static ConsoleURLProvider get() {
        return all().stream().findFirst().orElseThrow(() -> new RuntimeException("The DefaultConsoleURLProviderImpl should be instanciated"));
    }

    @Extension(ordinal = -100)
    @Restricted(NoExternalUse.class)
    class DefaultConsoleURLProviderImpl implements ConsoleURLProvider {

        @Override
        public String getConsoleURL(Run<?, ?> run) {
            return Functions.getContextRelativeUrl(run.getUrl() + "console");
        }
    }
}
