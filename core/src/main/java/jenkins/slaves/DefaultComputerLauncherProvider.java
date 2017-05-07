/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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
package jenkins.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Allows defining default computer launchers.
 * {@code JNLPLauncher} used to be the default launcher, but now it is decoupled to a plugin.
 * @author Oleg Nenashev
 * @since TODO
 */
public abstract class DefaultComputerLauncherProvider implements ExtensionPoint {
    
    /**
     * Gets the default computer launcher.
     * @param node Node
     * @return {@link ComputerLauncher} if it can be identified, {@code null} otherwise. 
     */
    @CheckForNull
    public abstract ComputerLauncher getDefaultLauncher(@Nonnull Node node);
    
    public static ExtensionList<DefaultComputerLauncherProvider> all() {
        return ExtensionList.lookup(DefaultComputerLauncherProvider.class);
    }
    
    /**
     * Locates a default launcher for the node.
     * @param node Node
     * @return Default launcher. 
     *         If there is no {@link DefaultComputerLauncherProvider} providing such launcher,
     *         an instance of {@link NoOpComputerLauncher} will be returned.
     */
    @Nonnull
    public static ComputerLauncher findDefaultLauncher(@Nonnull Node node) {
        for (DefaultComputerLauncherProvider provider : all()) {
            ComputerLauncher defaultLauncher = provider.getDefaultLauncher(node);
            if (defaultLauncher != null) {
                return defaultLauncher;
            }
        }
        
        // Fallback to a default Launcher
        return new NoOpComputerLauncher();
    }
}
