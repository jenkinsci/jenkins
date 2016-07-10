/*
 * The MIT License
 *
 * Copyright (c) 2016, Stephen Connolly.
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
package jenkins.scm;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Extension point for various decisions about SCM operations for {@link Item} instances.
 *
 * @since 2.11
 */
public abstract class SCMDecisionHandler implements ExtensionPoint {
    /**
     * This handler is consulted every time someone tries to run a polling of an {@link Item}.
     * If any of the registered handlers returns false, the {@link Item} will not be polled.
     *
     * @param item The item.
     */
    public abstract boolean shouldPoll(@Nonnull Item item);

    /**
     * All registered {@link SCMDecisionHandler}s
     */
    @Nonnull
    public static ExtensionList<SCMDecisionHandler> all() {
        return ExtensionList.lookup(SCMDecisionHandler.class);
    }

    /**
     * Returns the first {@link SCMDecisionHandler} that returns {@code false} from {@link #shouldPoll(Item)}
     * @param item the item
     * @return the first veto or {@code null} if there are no vetos
     */
    @CheckForNull
    public static SCMDecisionHandler firstShouldPollVeto(@Nonnull Item item) {
        for (SCMDecisionHandler handler : all()) {
            if (!handler.shouldPoll(item)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Returns the {@link SCMDecisionHandler} instances that return {@code false} from {@link #shouldPoll(Item)}
     * @param item the item
     * @return the {@link SCMDecisionHandler} instances vetoing the polling of the specified item.
     */
    @Nonnull
    public static List<SCMDecisionHandler> listShouldPollVetos(@Nonnull Item item) {
        List<SCMDecisionHandler> result = new ArrayList<>();
        for (SCMDecisionHandler handler : all()) {
            if (!handler.shouldPoll(item)) {
                result.add(handler);
            }
        }
        return result;
    }

}
