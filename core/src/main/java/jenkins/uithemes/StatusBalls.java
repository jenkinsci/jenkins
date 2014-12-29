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
import org.jenkinsci.plugins.uithemes.model.UIThemeImplSpec;
import org.jenkinsci.plugins.uithemes.model.UIThemeImplSpecProperty;
import org.jenkinsci.plugins.uithemes.model.UIThemeSet;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class StatusBalls implements UIThemeContributor {

    @Override
    public void contribute(UIThemeSet uiThemeSet) {
        uiThemeSet.registerTheme("status-balls", "Status Balls/Orbs", "Jenkins Ball/Orb Status Indicators");

        // Register core status balls themes
        registerClassicStatusBalls(uiThemeSet);
        registerCSS3StatusBalls(uiThemeSet);

        // Plugins etc can register other status ball theme "implementations".
    }

    private void registerClassicStatusBalls(UIThemeSet themeSet) {
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
        themeSet.registerThemeImpl("status-balls", "classic", "Classic", "Classic image-based Status Balls/Orbs");

        // #2 ...
        themeSet.contribute(new UIThemeContribution("classic-status-balls", "status-balls", "classic", Jenkins.class));

        // #3 ...
        // see core/src/main/resources/jenkins-themes/status-balls/classic/classic-status-balls/theme-template.less
    }

    private void registerCSS3StatusBalls(UIThemeSet themeSet) {
        // #1 ...
        themeSet.registerThemeImpl("status-balls", "css3-animated", "CSS3 Animated", "CSS3 Animated Status Balls/Orbs")
                .setThemeImplSpec(
                        new UIThemeImplSpec()
                        .addProperty("successColor", new UIThemeImplSpecProperty()
                                .setTitle("Success Color")
                                .setDescription("Success Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("008000")) // Green
                        .addProperty("warningColor", new UIThemeImplSpecProperty()
                                .setTitle("Warning Color")
                                .setDescription("Warning Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("ff8c00")) // Dark Orange
                        .addProperty("errorColor", new UIThemeImplSpecProperty()
                                .setTitle("Error Color")
                                .setDescription("Error Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("ff0000")) // Red
                        .addProperty("notBuiltColor", new UIThemeImplSpecProperty()
                                .setTitle("Not Built Color")
                                .setDescription("Not Built Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("808080"))  // Grey
                        .addProperty("disabledColor", new UIThemeImplSpecProperty()
                                .setTitle("Disabled Color")
                                .setDescription("Disabled Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("808080"))  // Grey
                        .addProperty("abortedColor", new UIThemeImplSpecProperty()
                                .setTitle("Aborted Color")
                                .setDescription("Aborted Color Indicator")
                                .setType(UIThemeImplSpecProperty.Type.COLOR)
                                .setDefaultValue("808080")) // Grey
                );

        // #2 ...
        themeSet.contribute(new UIThemeContribution("css3-animated-status-balls", "status-balls", "css3-animated", Jenkins.class));

        // #3 ...
        // see core/src/main/resources/jenkins-themes/status-balls/css3-animated/css3-animated-status-balls/theme-template.less
    }
}
