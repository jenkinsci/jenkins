/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.model;

import hudson.model.AbstractItem;
import hudson.model.Action;
import java.util.Collection;
import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class TransientActionFactoryTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void addedToAbstractItem() throws Exception {
        assertNotNull(r.createFolder("d").getAction(MyAction.class));
        assertNotNull(r.createFreeStyleProject().getAction(MyAction.class));
    }
    @TestExtension("addedToAbstractItem") public static class TestItemFactory extends TransientActionFactory<AbstractItem> {
        @Override public Class<AbstractItem> type() {return AbstractItem.class;}
        @Override public Collection<? extends Action> createFor(AbstractItem i) {
            return Collections.singleton(new MyAction());
        }
    }

    private static class MyAction implements Action {
        @Override public String getIconFileName() {
            return null;
        }
        @Override public String getDisplayName() {
            return null;
        }
        @Override public String getUrlName() {
            return null;
        }
    }

}
