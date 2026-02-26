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

package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.List;

/**
 * Participates in the rendering of the login page
 *
 * <p>
 * This class provides a few hooks to augment the HTML of the login page.
 *
 * <dl>
 *  <dt>simple-branding.jelly</dt>
 *  <dd>
 *    This view contributes to the branding section, usually located on the left side of the login/register pages.
 *  </dd>
 *  <dt>simple-footer.jelly</dt>
 *  <dd>
 *    This view contributes to the footer section, located below the login/register form.
 *  </dd>
 *  <dt>simple-head.jelly</dt>
 *  <dd>
 *    This view contributes to the head section.
 *  </dd>
 *  <dt>simple-header.jelly</dt>
 *  <dd>
 *    This view contributes to the header section just above the login/register form.
 *  </dd>
 * </dl>
 *
 * @since 2.128
 */
public class SimplePageDecorator extends Descriptor<SimplePageDecorator> implements ExtensionPoint, Describable<SimplePageDecorator> {

    protected SimplePageDecorator()  {
        super(self());
    }

    @Override
    public final Descriptor<SimplePageDecorator> getDescriptor() {
        return this;
    }
    /**
     * Obtains the URL of this object, excluding the context path.
     *
     * <p>
     * Every {@link SimplePageDecorator} is bound to URL via {@link Jenkins#getDescriptor()}.
     * This method returns such an URL.
     */

    public final String getUrl() {
        return "descriptor/" + clazz.getName();
    }

    /**
     * Returns all login page decorators.
     * @since 2.156
     */
    public static List<SimplePageDecorator> all() {
        return Jenkins.get().getDescriptorList(SimplePageDecorator.class);
    }

    /**
     * The first found LoginDecorator, there can only be one.
     * @return the first found {@link SimplePageDecorator}
     */
    public static SimplePageDecorator first() {
        List<SimplePageDecorator> decorators = all();
        return decorators.isEmpty() ? null : decorators.getFirst();
    }

}
