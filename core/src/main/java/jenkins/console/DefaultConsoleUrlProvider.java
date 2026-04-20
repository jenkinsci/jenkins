/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package jenkins.console;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Run;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default implementation of {@link ConsoleUrlProvider} that uses the standard Jenkins console view.
 * <p>Exists so that users have a way to override {@link ConsoleUrlProviderGlobalConfiguration} and specify the default
 * console view if desired via {@link ConsoleUrlProviderUserProperty}.
 * @since 2.433
 */
@Restricted(value = NoExternalUse.class)
public class DefaultConsoleUrlProvider implements ConsoleUrlProvider {

    @DataBoundConstructor
    public DefaultConsoleUrlProvider() {
    }

    @Override
    public String getConsoleUrl(Run<?, ?> run) {
        return run.getUrl() + "console";
    }

    @Extension
    @Symbol(value = "default")
    public static class DescriptorImpl extends Descriptor<ConsoleUrlProvider> {

        @Override
        public String getDisplayName() {
            return Messages.defaultProviderDisplayName();
        }
    }
}
