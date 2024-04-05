/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package jenkins.model;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Hudson;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@Extension
public class RenameActionTransientFactory extends TransientActionFactory<AbstractItem> {

    @Override
    public Class<AbstractItem> type() {
        return AbstractItem.class;
    }

    @Override
    public Collection<? extends Action> createFor(AbstractItem target) {
        boolean hasPermission = target.hasPermission(target.CONFIGURE) ||
                (target.hasPermission(target.DELETE) &&
                        ((Hudson)target.getParent()).hasPermission(target.CREATE));

        if (hasPermission && target.isNameEditable()) {
            return Set.of(
                    new Action() {
                @Override
                public String getIconFileName() {
                    return "symbol-edit";
                }

                @Override
                public String getDisplayName() {
                    return "Rename";
                }

                @Override
                public String getUrlName() {
                    return target.getAbsoluteUrl() + "confirm-rename";
                }
            }
            );
        } else {
            return Collections.emptyList();
        }
    }
}
