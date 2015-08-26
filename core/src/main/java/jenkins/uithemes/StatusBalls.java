/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package jenkins.uithemes;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.uithemes.UIThemeContributor;
import org.jenkinsci.plugins.uithemes.model.UIThemeContribution;
import org.jenkinsci.plugins.uithemes.model.UIThemeSet;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class StatusBalls implements UIThemeContributor {

    @Override
    public void contribute(UIThemeSet uiThemeSet) {
        uiThemeSet.registerTheme("status-balls", "Status Balls/Orbs", "Jenkins Ball/Orb Status Indicators");

        // Register core status balls themes
        // Plugins etc can register other status ball theme "implementations".

        //
        // The classic status balls theme impl is very simple since it is not configurable. All we need to do
        // here in Jenkins core is:
        //
        //      1. register the classic theme implementation
        //      2. add a contribution for the classic status balls added by Jenkins core
        //      3. define the LESS template for the core "classic" status ball styles
        //
        //      See below.
        //

        // #1 ...
        uiThemeSet.registerThemeImpl("status-balls", "classic", "Classic", "Classic image-based Status Balls/Orbs");

        // #2 ...
        uiThemeSet.contribute(new UIThemeContribution("classic-status-balls", "status-balls", "classic", Jenkins.class));

        // #3 ...
        // see core/src/main/resources/jenkins-themes/status-balls/classic/classic-status-balls/theme-template.less
    }
}
