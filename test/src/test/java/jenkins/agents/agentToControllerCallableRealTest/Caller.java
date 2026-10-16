/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

package jenkins.agents.agentToControllerCallableRealTest;

import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import java.io.Serializable;
import jenkins.agents.AgentToControllerCallable;
import jenkins.agents.ControllerToAgentCallable;

public class Caller {

    public static int call(VirtualChannel ch) throws Exception {
        return ch.call(new Out(new AgentToControllerCallable.TrustedObject<>(new Thing(19)), new AgentToControllerCallable.EncryptedObject<>(new Thing(23))));
    }

    private record Thing(int x) implements Serializable {}

    private record Out(AgentToControllerCallable.TrustedObject<Thing> thing1, AgentToControllerCallable.EncryptedObject<Thing> thing2) implements ControllerToAgentCallable<Integer, Exception> {
        @Override
        public Integer call() throws Exception {
            return Channel.currentOrFail().call(new AndBack(thing1, thing2));
        }
    }

    private record AndBack(TrustedObject<Thing> thing1, EncryptedObject<Thing> thing2) implements AgentToControllerCallable<Integer, Exception> {
        @Override
        public Integer call() throws Exception {
            return thing1.o().x + thing2.o().x;
        }
    }

    private Caller() {}

}
