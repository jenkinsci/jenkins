/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
/**
 * Participates in the rendering of the login page
 *
 * <p>
 * This class provides a few hooks to augment the HTML of the login page.
 *
 * @since TODO
 */
public class LoginPageDecorator extends Descriptor<LoginPageDecorator> implements ExtensionPoint, Describable<LoginPageDecorator> {

    protected LoginPageDecorator()  {
        super(self());
    }

    @Override
    public final Descriptor<LoginPageDecorator> getDescriptor() {
        return this;
    }
    /**
     * Obtains the URL of this object, excluding the context path.
     *
     * <p>
     * Every {@link LoginPageDecorator} is bound to URL via {@link Jenkins#getDescriptor()}.
     * This method returns such an URL.
     */
    public final String getUrl() {
        return "descriptor/"+clazz.getName();
    }

    /**
     * The first found LoginDecarator, there can only be one.
     * @return the first found {@link LoginPageDecorator}
     */
    public static LoginPageDecorator first(){
        DescriptorExtensionList<LoginPageDecorator, LoginPageDecorator> descriptorList = Jenkins.getInstanceOrNull().<LoginPageDecorator, LoginPageDecorator>getDescriptorList(LoginPageDecorator.class);
        return descriptorList.get(0);
    }

}
