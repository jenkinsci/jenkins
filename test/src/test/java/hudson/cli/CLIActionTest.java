/*
 * The MIT License
 *
 * Copyright (c) 2013 christ66
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

package hudson.cli;

import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * A Test unit to verify that CLIAction has .
 *
 * @author christ66
 */
public class CLIActionTest extends HudsonTestCase {
    public void testCliActionUnprotectedRootActionTest() throws Exception {
        WebClient wc = createWebClient();
        wc.goTo("cli"); // Verify user can see cli before security

        jenkins.setSecurityRealm(createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());

        wc.goTo("cli"); // and after security enabled.
    }
}
