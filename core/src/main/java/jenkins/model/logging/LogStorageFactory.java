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
 * Locates {@link LogStorage}s.
 * @author Oleg Nenashev
 * @author Xing Yan
 * @since TODO
 * @see jenkins.model.logging.impl.FileLogStorage
 */
@Restricted(Beta.class)
public abstract class LogStorageFactory implements ExtensionPoint {
  
  /**
   * Retrieve the logging method for the run.
   * @param object Loggable object
   * @return Logging method. {@code null} if the locator does not provide the
   *         implementation for the run.
   */
  @CheckForNull
  protected abstract LogStorage getLogStorage(Loggable object);

  @Nonnull
  public static LogStorage locate(Loggable run) {
      for (LogStorageFactory locator : all()) {
          final LogStorage logStorage = locator.getLogStorage(run);
          if (logStorage != null) {
              return logStorage;
          }
      }
      // Fallback
      return run.getDefaultLogStorage();
  }
  
  public static ExtensionList<LogStorageFactory> all() {
      return ExtensionList.lookup(LogStorageFactory.class);
  }
}