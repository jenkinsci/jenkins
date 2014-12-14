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
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.plugins.uithemes.UIThemeContributor;
import org.jenkinsci.plugins.uithemes.model.UIThemeContribution;
import org.jenkinsci.plugins.uithemes.model.UIThemeSet;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class Icons implements UIThemeContributor {

    public Icons() {
        redefineCoreIcons();
    }

    @Override
    public void contribute(UIThemeSet uiThemeSet) {
        uiThemeSet.registerTheme("icons", "Icons", "The set of Icons used by Jenkins");

        // Register the default icon set
        registerDefaultIcons(uiThemeSet);

        // Plugins etc can register other icons sets i.e. other "icon" theme "implementations".
    }

    private void registerDefaultIcons(UIThemeSet themeSet) {
        //
        // The default icons theme impl is very simple since it is not configurable. All we need to do
        // here in Jenkins core is:
        //
        //      1. register the default theme implementation
        //      2. add a contribution for the default icons added by Jenkins core (plugins can also make
        //         contributions to the "default" impl for their icons)
        //      3. define the LESS template for the core "default" icon styles
        //
        //      See below.
        //

        // #1 ...
        themeSet.registerThemeImpl("icons", "default", "Default", "Classic Jenkins Icon Set");

        // #2 ...
        themeSet.contribute(new UIThemeContribution("default-icons-core", "icons", "default", Jenkins.class));

        // #3 ...
        // see core/src/main/resources/jenkins-themes/icons/default/default-icons-core/theme-template.less
    }

    /**
     * The core icons need to be redefined from the base/default definitions as defined in
     * the IconSet class in the icon-shim plugin. We redefine by removing the image url from
     * the definition, forcing the <l:icon> tag (see icon.jelly in Jenkins core) to render
     * a <span> instead of an <img>. The theme impl CSS will style the <span>.
     */
    private void redefineCoreIcons() {
        // Re-add all of the core icons, but without the URL
        for (Icon coreIcon : IconSet.icons.getCoreIcons().values()) {
            IconSet.icons.addIcon(new Icon(coreIcon.getClassSpec(), coreIcon.getStyle()));
        }
    }
}
