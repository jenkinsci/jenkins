/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package jenkins.uithemes;

import org.jenkinsci.plugins.uithemes.UIThemesProcessor;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CoreThemeIntsaller {

    private static final Logger LOGGER = Logger.getLogger(CoreThemeIntsaller.class.getName());

    public static void installCoreThemes() throws IOException {
        UIThemesProcessor themesProcessor = UIThemesProcessor.getInstance();

        // Set up the environment variables for this Jenkins instance.
        Properties jenkinsEnv = new Properties();
        jenkinsEnv.setProperty("rootURL", ".."); // TODO: Jenkins.getInstance().getRootUrl() returns null ?? Probably needs the context of a request.
        UIThemesProcessor.createJenkinsEnvVariablesLESSFile(jenkinsEnv);

        // Delete all user theme pre-generated data, forcing a refresh.
        themesProcessor.deleteAllUserThemes();

        // Add the core contributors.
        themesProcessor.addContributor(new Icons());
        themesProcessor.addContributor(new StatusBalls());

        LOGGER.log(Level.INFO, "Core UI Themes installed.");
    }
}
