/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts,
 * Andrew Bayer
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

package hudson.model.listeners;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.XmlFile;
import hudson.model.Saveable;
import jenkins.util.Listeners;

/**
 * Receives notifications about save actions on {@link Saveable} objects in Hudson.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 *
 * @author Andrew Bayer
 * @since 1.334
 */
public abstract class SaveableListener implements ExtensionPoint {

    /**
     * Called when a change is made to a {@link Saveable} object.
     *
     * @param o
     *      The saveable object.
     * @param file
     *      The {@link XmlFile} for this saveable object.
     */
    public void onChange(Saveable o, XmlFile file) {}

    /**
     * Called when a {@link Saveable} object gets deleted.
     *
     * @param o
     *      The saveable object.
     * @param file
     *      The {@link XmlFile} for this saveable object.
     * @since 2.480
     */
    public void onDeleted(Saveable o, XmlFile file) {}

    /**
     * Registers this object as an active listener so that it can start getting
     * callbacks invoked.
     *
     * @deprecated as of 1.281
     *      Put {@link Extension} on your class to get it auto-registered.
     */
    @Deprecated
    public void register() {
        all().add(this);
    }

    /**
     * Reverse operation of {@link #register()}.
     */
    public void unregister() {
        all().remove(this);
    }

    /**
     * Fires the {@link #onChange} event.
     */
    public static void fireOnChange(Saveable o, XmlFile file) {
        Listeners.notify(SaveableListener.class, false, l -> l.onChange(o, file));
    }

    /**
     * Fires the {@link #onDeleted} event.
     * @since 2.480
     */
    public static void fireOnDeleted(Saveable o, XmlFile file) {
        Listeners.notify(SaveableListener.class, false, l -> l.onDeleted(o, file));
    }

    /**
     * Returns all the registered {@link SaveableListener} descriptors.
     */
    public static ExtensionList<SaveableListener> all() {
        return ExtensionList.lookup(SaveableListener.class);
    }
}
