/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package hudson.model;

import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.kohsuke.stapler.ReflectionUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link ItemGroup} that is a general purpose container, which allows users and the rest of the program
 * to create arbitrary items into it.
 *
 * <p>
 * In contrast, some other {@link ItemGroup}s compute its member {@link Item}s and the content
 * is read-only, thus it cannot allow external code/user to add arbitrary objects in it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.417
 */
public interface ModifiableItemGroup<T extends Item> extends ItemGroup<T> {
    /**
     * The request format follows that of {@code &lt;n:form xmlns:n="/lib/form">}.
     *
     */
    @StaplerNotDispatchable
    default T doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (ReflectionUtils.isOverridden(
                ModifiableItemGroup.class,
                getClass(),
                "doCreateItem",
                StaplerRequest.class,
                StaplerResponse.class)) {
            try {
                return doCreateItem(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModifiableItemGroup.class.getSimpleName() + ".doCreateItem methods");
        }
    }

    /**
     * @deprecated use {@link #doCreateItem(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    default T doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        if (ReflectionUtils.isOverridden(
                ModifiableItemGroup.class,
                getClass(),
                "doCreateItem",
                StaplerRequest2.class,
                StaplerResponse2.class)) {
            try {
                return doCreateItem(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModifiableItemGroup.class.getSimpleName() + ".doCreateItem methods");
        }
    }
}
