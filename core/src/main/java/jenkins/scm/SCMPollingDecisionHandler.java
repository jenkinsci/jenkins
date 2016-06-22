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
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Extension point for deciding if particular job should be polled or not.
 *
 * <p>
 * This handler is consulted every time someone tries to run a polling of an {@link Item}.
 * If any of the registered handlers returns false, the {@link Item} will not be polled.
 *
 * @since TODO
 */
public abstract class SCMPollingDecisionHandler implements ExtensionPoint {
    /**
     * Returns whether the new item should be polled.
     *
     * @param item The item.
     */
    public abstract boolean shouldPoll(@Nonnull Item item);

    /**
     * All registered {@link SCMPollingDecisionHandler}s
     */
    @Nonnull
    public static ExtensionList<SCMPollingDecisionHandler> all() {
        return ExtensionList.lookup(SCMPollingDecisionHandler.class);
    }

    /**
     * Returns the first {@link SCMPollingDecisionHandler} that returns {@code false} from {@link #shouldPoll(Item)}
     * @param item the item
     * @return the first veto or {@code null} if there are no vetos
     */
    @CheckForNull
    public static SCMPollingDecisionHandler firstVeto(@Nonnull Item item) {
        for (SCMPollingDecisionHandler handler : all()) {
            if (!handler.shouldPoll(item)) {
                return handler;
            }
        }
        return null;
    }

}
