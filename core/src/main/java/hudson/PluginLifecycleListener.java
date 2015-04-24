/*
 * The MIT License
 *
 * Copyright (c) 2013-2015, CloudBees, Inc.
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
package hudson;

import javax.annotation.Nonnull;

import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * Extension point providing plugin lifecycle events.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class PluginLifecycleListener implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(PluginLifecycleListener.class.getName());

    /**
     * Plugin activate.
     * @param plugin The plugin that's just been been activated.
     */
    public void onActivate(@Nonnull PluginWrapper plugin) {}

    /**
     * Plugin failure.
     * @param failedPlugin The failed plugin.               
     */
    public void onFail(@Nonnull PluginManager.FailedPlugin failedPlugin) {}

    /**
     * Plugin deactivate.
     * @param plugin The plugin that's just been been deactivated.
     */
    public void onDeactivate(@Nonnull PluginWrapper plugin) {}

    /**
     * Get all {@link PluginLifecycleListener} {@link Extension extension points}.
     * @return All {@link PluginLifecycleListener}. 
     */
    public static ExtensionList<PluginLifecycleListener> all() {
        return ExtensionList.lookup(PluginLifecycleListener.class);
    }
    
    
    public static void fireOnActivate(PluginWrapper pluginWrapper) {
        for (PluginLifecycleListener listener : all()) {
            try {
                listener.onActivate(pluginWrapper);
            } catch (Throwable t) {
                LOGGER.log(WARNING, "Error firing plugin onActivate event.", t);
            }
        }
    }

    public static void fireOnFail(PluginManager.FailedPlugin failedPlugin) {
        for (PluginLifecycleListener listener : all()) {
            try {
                listener.onFail(failedPlugin);
            } catch (Throwable t) {
                LOGGER.log(WARNING, "Error firing plugin fail event.", t);
            }
        }
    }

    public static void fireOnDeactivate(PluginWrapper pluginWrapper) {
        for (PluginLifecycleListener listener : all()) {
            try {
                listener.onDeactivate(pluginWrapper);
            } catch (Throwable t) {
                LOGGER.log(WARNING, "Error firing plugin onDeactivate event.", t);
            }
        }
    }
}
