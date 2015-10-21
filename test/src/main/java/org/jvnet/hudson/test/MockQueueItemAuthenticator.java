/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.User;
import java.util.Map;
import javax.inject.Inject;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.security.QueueItemAuthenticatorDescriptor;
import org.acegisecurity.Authentication;

/**
 * Allows testing of anything related to {@link QueueItemAuthenticator}.
 * To use, call {@link QueueItemAuthenticatorConfiguration#get} (or {@link Inject} it),
 * then call {@link QueueItemAuthenticatorConfiguration#getAuthenticators}
 * and add an instance of this.
 */
public final class MockQueueItemAuthenticator extends QueueItemAuthenticator {

    private final Map<String,Authentication> jobsToUsers;
    
    /**
     * Creates a new authenticator.
     * @param jobsToUsers a map from {@link Item#getFullName} to authentications such as from {@link User#impersonate}
     */
    public MockQueueItemAuthenticator(Map<String,Authentication> jobsToUsers) {
        this.jobsToUsers = jobsToUsers;
    }
    
    @Override public Authentication authenticate(Queue.Item item) {
        if (item.task instanceof Item) {
            return jobsToUsers.get(((Item) item.task).getFullName());
        } else {
            return null;
        }
    }
    
    @Extension public static final class DescriptorImpl extends QueueItemAuthenticatorDescriptor {}

}
