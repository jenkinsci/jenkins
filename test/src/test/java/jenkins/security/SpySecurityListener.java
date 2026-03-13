/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.security.core.userdetails.UserDetails;

//TODO temporary solution, should be moved to Jenkins Test Harness project
/**
 * Mean to be included in test classes to provide a way to spy on the SecurityListener events
 * @see jenkins.security.BasicHeaderProcessorTest.SpySecurityListenerImpl
 * @see hudson.security.CliAuthenticationTest.SpySecurityListenerImpl
 */
public abstract class SpySecurityListener extends SecurityListener {
    public final EventQueue<UserDetails> authenticatedCalls = new EventQueue<>();
    public final EventQueue<String> failedToAuthenticateCalls = new EventQueue<>();
    public final EventQueue<String> loggedInCalls = new EventQueue<>();
    public final EventQueue<String> failedToLogInCalls = new EventQueue<>();
    public final EventQueue<String> loggedOutCalls = new EventQueue<>();

    public void clearPreviousCalls() {
        this.authenticatedCalls.clear();
        this.failedToAuthenticateCalls.clear();
        this.loggedInCalls.clear();
        this.failedToLogInCalls.clear();
        this.loggedOutCalls.clear();
    }

    @Override
    protected void authenticated2(@NonNull UserDetails details) {
        this.authenticatedCalls.add(details);
    }

    @Override
    protected void failedToAuthenticate(@NonNull String username) {
        this.failedToAuthenticateCalls.add(username);
    }

    @Override
    protected void loggedIn(@NonNull String username) {
        this.loggedInCalls.add(username);
    }

    @Override
    protected void failedToLogIn(@NonNull String username) {
        this.failedToLogInCalls.add(username);
    }

    @Override
    protected void loggedOut(@NonNull String username) {
        this.loggedOutCalls.add(username);

    }

    public static class EventQueue<T> {
        private final List<T> eventList = new ArrayList<>();

        private EventQueue add(T t) {
            eventList.add(t);
            return this;
        }

        public void assertLastEventIsAndThenRemoveIt(T expected) {
            assertLastEventIsAndThenRemoveIt(expected::equals);
        }

        public void assertLastEventIsAndThenRemoveIt(Predicate<T> predicate) {
            if (eventList.isEmpty()) {
                fail("event list is empty");
            }

            T t = eventList.removeLast();
            assertTrue(predicate.test(t));
            eventList.clear();
        }

        public void assertNoNewEvents() {
            assertEquals(0, eventList.size(), "list of event should be empty");
        }

        public void clear() {
            eventList.clear();
        }
    }
}
