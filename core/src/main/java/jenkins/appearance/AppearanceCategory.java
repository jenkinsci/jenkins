/*
 * The MIT License
 *
 * Copyright (c) 2023, Tim Jacomb
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

package jenkins.appearance;

import hudson.Extension;
import jenkins.model.GlobalConfigurationCategory;

/**
 * <p>Global configuration of appearance configuration.</p>
 *
 * <p>This should be used for Plugins that contribute to the look and feel of Jenkins.
 * Theming, header and footer changes, information density are all good examples.
 * API plugins for UI components that are used by other plugins also fit into that, e.g. source code display.</p>
 *
 * <p>Configuration specific to a single plugin that is not related to the overall look and feel of Jenkins may not belong here.</p>
 *
 * <p>If a plugin has a single global configuration it should separate appearance and general configuration to different classes.</p>
 *
 */
@Extension
public class AppearanceCategory extends GlobalConfigurationCategory {
    @Override
    public String getShortDescription() {
        return Messages.AppearanceCategory_DisplayName();
    }

    @Override
    public String getDisplayName() {
        return Messages.AppearanceCategory_Description();
    }
}
