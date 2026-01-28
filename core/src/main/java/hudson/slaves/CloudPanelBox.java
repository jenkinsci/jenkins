/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds box rendered in the cloud side panel.
 *
 * Add box.jelly to display box
 *
 * @author Kohsuke Kawaguchi
 * @since 2.491
 * @see hudson.slaves.Cloud#getCloudPanelBoxes()
 */

public abstract class CloudPanelBox implements ExtensionPoint {

    private Cloud cloud;


    public void setCloud(Cloud cloud) {
        this.cloud = cloud;
    }

    public Cloud getCloud() {
        return cloud;
    }

    /**
     * Create boxes for the given cloud in its page.
     *
     * @param cloud
     *      The cloud for which displays the boxes. Never null.
     * @return
     *      List of all the registered {@link CloudPanelBox}s.
     */
    public static List<CloudPanelBox> all(Cloud cloud) {
        List<CloudPanelBox> boxes = new ArrayList<>();
        for (CloudPanelBox box : ExtensionList.lookup(CloudPanelBox.class)) {
            box.setCloud(cloud);
            boxes.add(box);
        }
        return boxes;
    }


}
