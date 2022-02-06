/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

package jenkins.util;

import hudson.ExtensionList;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.security.core.Authentication;

/**
 * Utilities for working with listener interfaces.
 * @since TODO
 */
public class Listeners {

    /**
     * Safely send a notification to all listeners of a given type.
     * <p>Only suitable for listener methods which do not throw checked or otherwise documented exceptions.
     * @param <L> the type of listener
     * @param listenerType the type of listener
     * @param asSystem whether to impersonate {@link ACL#SYSTEM2}.
     *                 For most listener methods, this should be {@code true},
     *                 so that listener implementations can freely perform various operations without access checks.
     *                 In some cases, existing listener interfaces were implicitly assumed to pass along user authentication,
     *                 because they were sometimes triggered by user actions such as configuration changes;
     *                 this is an antipattern (better to pass an explicit {@link Authentication} argument if relevant).
     * @param notification a listener method, perhaps with arguments
     * @since TODO
     */
    public static <L> void notify(Class<L> listenerType, boolean asSystem, Consumer<L> notification) {
        Runnable r = () -> {
            for (L listener : ExtensionList.lookup(listenerType)) {
                try {
                    notification.accept(listener);
                } catch (Throwable x) {
                    Logger.getLogger(listenerType.getName()).log(Level.WARNING, null, x);
                }
            }
        };
        if (asSystem) {
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                r.run();
            }
        } else {
            r.run();
        }
    }

    private Listeners() {}

}
