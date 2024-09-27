/*
 * The MIT License
 *
 * Copyright 2018, CloudBees, Inc.
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

package hudson;


import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Participates in the rendering of the about page
 *
 * <p>
 * This class provides a few hooks to augment the HTML of the about page.
 *
 * <dl>
 *  <dt>about-branding.jelly</dt>
 *  <dd>
 *    This view contributes to the branding section.
 *  </dd>
 *  <dt>about-involved.jelly</dt>
 *  <dd>
 *    This view contributes the get involved box/icon.
 *  </dd>
 *  <dt>about-static.jelly</dt>
 *  <dd>
 *    This view contributes to the static listing of dependencies.
 *  </dd>
 * </dl>
 *
 * @since 2.478
 */
public abstract class AboutPageStablerOverride implements ExtensionPoint {

    /**
     * Return all implementations of this extension point
     * @return All implementations of this extension point
     */
    public static @NonNull ExtensionList<AboutPageStablerOverride> all() {
        return ExtensionList.lookup(AboutPageStablerOverride.class);
    }
}
