/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

package jenkins.websocket;

import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.nio.ByteBuffer;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;

@Extension
@Restricted(NoExternalUse.class)
public class WebSocketEcho  extends InvisibleAction implements RootAction {

        @Override
        public String getUrlName() {
            return "wsecho";
        }

        public HttpResponse doIndex() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return WebSockets.upgrade(new WebSocketSession() {
                @Override
                protected void text(String message) {
                    sendText("hello " + message);
                }
                @Override
                protected void binary(byte[] payload, int offset, int len) {
                    ByteBuffer data = ByteBuffer.allocate(len);
                    for (int i = 0; i < len; i++) {
                        byte b = payload[offset + i];
                        if (b >= 'a' && b <= 'z') {
                            b += 'A' - 'a';
                        }
                        data.put(i, b);
                    }
                    sendBinary(data);
                }
            });
        }

}
