/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A {@link AutoCloseable} that captures the previous {@link SecurityContext} and restores it on {@link #close()}
 *
 * @since 2.14
 */
public class ACLContext implements AutoCloseable {

    /**
     * The previous context.
     */
    @NonNull
    private final SecurityContext previousContext;

    /**
     * Private constructor to ensure only instance creation is from {@link ACL#as(Authentication)}.
     * @param previousContext the previous context
     */
    ACLContext(@NonNull SecurityContext previousContext) {
        this.previousContext = previousContext;
    }

    /**
     * Accessor for the previous context.
     * @return the previous context.
     * @since TODO
     */
    @NonNull
    public SecurityContext getPreviousContext2() {
        return previousContext;
    }

    /**
     * @deprecated use {@link #getPreviousContext2}
     */
    @Deprecated
    public org.acegisecurity.context.SecurityContext getPreviousContext() {
        return org.acegisecurity.context.SecurityContext.fromSpring(getPreviousContext2());
    }

    @Override
    public void close() {
        SecurityContextHolder.setContext(previousContext);
    }
}
