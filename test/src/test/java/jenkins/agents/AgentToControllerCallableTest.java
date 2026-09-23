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

package jenkins.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.Functions;
import hudson.remoting.Channel;
import java.io.Serializable;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AgentToControllerCallableTest {

    @Test
    void trustedObjectBenign(JenkinsRule r) throws Exception {
        assertThat(
                r.createOnlineSlave().getChannel().call(new TOOut(new AgentToControllerCallable.TrustedObject<>(new Thing("xxx", 1)), false)),
                is("xxx#1"));
    }

    @Test
    void trustedObjectMalicious(JenkinsRule r) throws Exception {
        var thing = new Thing("xxx", 1);
        assertThat(
                Functions.printThrowable(assertThrows(
                        Exception.class,
                        () -> r.createOnlineSlave().getChannel().call(new TOOut(new AgentToControllerCallable.TrustedObject<>(thing), true)))),
                containsString("java.lang.SecurityException: Incorrect HMAC"));
    }

    private record Thing(String x, int num) implements Serializable {}

    private record TOOut(AgentToControllerCallable.TrustedObject<Thing> thing, boolean malicious) implements ControllerToAgentCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            TOAndBack andBack;
            if (malicious) {
                var evilThing = new Thing(thing.o().x(), thing.o().num + 665);
                var evilTO = new AgentToControllerCallable.TrustedObject<>(thing.o(), AgentToControllerCallable.serialize(evilThing), thing.mac);
                andBack = new TOAndBack(evilTO);
            } else {
                andBack = new TOAndBack(thing);
            }
            return Channel.currentOrFail().call(andBack);
        }
    }

    private record TOAndBack(TrustedObject<Thing> thing) implements AgentToControllerCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            return thing.o().x + "#" + thing.o().num;
        }
    }

    @Test
    void encryptedObjectRoundTrip(JenkinsRule r) throws Exception {
        var ch = r.createOnlineSlave().getChannel();
        assertThat(ch.call(new EOOut(new AgentToControllerCallable.EncryptedObject<>(new Thing("xxx", 1)))), is("xxx#1"));
        assertThat(ch.call(new EOOut(new AgentToControllerCallable.EncryptedObject<>(new Thing("yyy", 2)))), is("yyy#2"));
    }

    private record EOOut(AgentToControllerCallable.EncryptedObject<Thing> thing) implements ControllerToAgentCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            return Channel.currentOrFail().call(new EOAndBack(thing));
        }
    }

    private record EOAndBack(EncryptedObject<Thing> thing) implements AgentToControllerCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            return thing.o().x + "#" + thing.o().num;
        }
    }
}
