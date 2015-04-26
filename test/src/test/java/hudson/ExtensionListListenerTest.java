/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import jenkins.model.TransientActionFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ExtensionListListenerTest {

    @Rule
    public JenkinsRule r = PluginManagerUtil.newJenkinsRule();

    @Test
    public void test_onChange() throws Exception {
        ExtensionList<TransientActionFactory> extensionList = ExtensionList.lookup(TransientActionFactory.class);

        // force ExtensionList.ensureLoaded, otherwise the refresh will be ignored because
        // the extension list will not be initialised.
        extensionList.size();

        // Add the listener
        MyExtensionListListener listListener = new MyExtensionListListener();
        extensionList.addListener(listListener);

        // magiext.hpi has a TransientActionFactory @Extension impl in it. The loading of that
        // plugin should trigger onChange in the MyExtensionListListener instance.
        PluginManagerUtil.dynamicLoad("magicext.hpi", r.jenkins);

        Assert.assertEquals(1, listListener.onChangeCallCount);
    }

    private class MyExtensionListListener extends ExtensionListListener {
        private int onChangeCallCount = 0;
        @Override
        public void onChange() {
            onChangeCallCount++;
        }
    }
}
