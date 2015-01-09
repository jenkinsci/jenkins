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
public class PageHeader implements UIThemeContributor {

    @Override
    public void contribute(UIThemeSet uiThemeSet) {
        uiThemeSet.registerTheme("page-header", "Page Header", "Jenkins Page Header");

        // Register core header themes
        // See Icons class for more details on what's happening here.

        // #1 ...
        uiThemeSet.registerThemeImpl("page-header", "classic", "Classic", "Classic Page Header")
                .setThemeImplSpec(
                        new UIThemeImplSpec()
                                .addProperty("logoUrl", new UIThemeImplSpecProperty()
                                        .setTitle("Logo URL")
                                        .setDefaultValue("@{rootURL}/images/logo-headshot-white-title.png"))
                                .addProperty("backgroundColor", new UIThemeImplSpecProperty()
                                        .setTitle("Background Color")
                                        .setType(UIThemeImplSpecProperty.Type.COLOR)
                                        .setDefaultValue("000000")) // Black
                                .addProperty("fontColor", new UIThemeImplSpecProperty()
                                        .setTitle("Font Color")
                                        .setType(UIThemeImplSpecProperty.Type.COLOR)
                                        .setDefaultValue("ffffff")) // White
                );

        // #2 ...
        uiThemeSet.contribute(new UIThemeContribution("classic-page-header", "page-header", "classic", Jenkins.class));

        // #3 ...
        // see core/src/main/resources/jenkins-themes/page-header/classic/classic-page-header/theme-template.less
    }
}
