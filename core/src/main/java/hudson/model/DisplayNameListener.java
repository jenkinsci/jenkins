/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Yahoo!, Inc.
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



import hudson.Extension;
import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author kingfai
 *
 */
@Extension
public class DisplayNameListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(DisplayNameListener.class.getName());

    /**
     * Called after the user has clicked OK in the New Job page when
     * Copy existing job has been selected.
     * The fields in item will be displayed in when the config page is loaded
     * displayed.
     */
    @Override
    public void onCopied(Item src, Item item) {
        // bug 5056825 - Display name field should be cleared when you copy a job within the same folder.
        if (item instanceof AbstractItem dest && src.getParent() == item.getParent()) {
            try {
                dest.setDisplayName(null);
            } catch (IOException ioe) {
                LOGGER.log(Level.WARNING, String.format("onCopied():Exception while trying to clear the displayName for Item.name:%s", item.getName()), ioe);
            }
        }
    }

    /**
     * Called after the user has changed the project name of a job and then
     * clicked on submit.
     * @param item The item that has been renamed. The new project name is already
     * in item.getName()
     * @param oldName the old name
     * @param newName the new name
     */
    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        // bug 5077308 - Display name field should be cleared when you rename a job.
        if (item instanceof AbstractItem abstractItem) {
            if (oldName.equals(abstractItem.getDisplayName())) {
                // the user renamed the job, but the old project name which is shown as the
                // displayname if no displayname was set, has been set into the displayname field.
                // This means that the displayname was never set, so we want to set it
                // to null as it was before
                try {
                    LOGGER.info(String.format("onRenamed():Setting displayname to null for item.name=%s", item.getName()));
                    abstractItem.setDisplayName(null);
                }
                catch (IOException ioe) {
                    LOGGER.log(Level.WARNING, String.format("onRenamed():Exception while trying to clear the displayName for Item.name:%s",
                            item.getName()), ioe);
                }
            }
        }
    }

}
