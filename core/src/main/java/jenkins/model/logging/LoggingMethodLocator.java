/*
 * The MIT License
 *
 * Copyright 2016-2018 CloudBees Inc., Google Inc.
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
package jenkins.model.logging;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Locates logging methods for runs.
 * @author Oleg Nenashev
 * @author Xing Yan
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class LoggingMethodLocator implements ExtensionPoint {
  
  /**
   * Retrieve the logging method for the run.
   * @param object Loggable object
   * @return Logging method. {@code null} if the locator does not provide the
   *         implementation for the run.
   */
  @CheckForNull
  protected abstract LoggingMethod getLoggingMethod(Loggable object);

    /**
     * Retrieve the log browser which should be used for a run.
     * @param object Loggable object
     * @return Logging method. {@code null} if the locator does not provide the
     *         implementation for the run.
     */
    @CheckForNull
    protected abstract LogBrowser getLogBrowser(Loggable object);

  @Nonnull
  public static LoggingMethod locate(Loggable run) {
      for (LoggingMethodLocator locator : all()) {
          final LoggingMethod loggingMethod = locator.getLoggingMethod(run);
          if (loggingMethod != null) {
              return loggingMethod;
          }
      }
      // Fallback
      return run.getDefaultLoggingMethod();
  }

    @Nonnull
    public static LogBrowser locateBrowser(Loggable run) {
        for (LoggingMethodLocator locator : all()) {
            final LogBrowser browser = locator.getLogBrowser(run);
            if (browser != null) {
                return browser;
            }
        }

        // Fallback
        LogBrowser defaultFromLogStorage = locate(run).getDefaultLogBrowser();
        return defaultFromLogStorage != null
                ? defaultFromLogStorage
                : run.getDefaultLogBrowser();
    }
  
  public static ExtensionList<LoggingMethodLocator> all() {
      return ExtensionList.lookup(LoggingMethodLocator.class);
  }
}