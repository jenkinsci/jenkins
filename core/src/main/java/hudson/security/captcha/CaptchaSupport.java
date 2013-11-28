/*
 * The MIT License
 * 
 * Copyright (c) 2011, Winston.Prakash@oracle.com
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
package hudson.security.captcha;


import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.IOException;
import java.io.OutputStream;
import jenkins.model.Jenkins;


/**
 * Extension point for adding Captcha Support to User Registration Page {@link CaptchaSupport}.
 *
 * <p>
 * This object can have an optional <tt>config.jelly</tt> to configure the Captcha Support
 * <p>
 * A default constructor is needed to create CaptchaSupport in
 * the default configuration.
 *
 * @author Winston Prakash
 * @since 1.416
 * @see CaptchaSupportDescriptor
 */
public abstract class CaptchaSupport extends AbstractDescribableImpl<CaptchaSupport> implements ExtensionPoint {
    /**
     * Returns all the registered {@link CaptchaSupport} descriptors.
     */
    public static DescriptorExtensionList<CaptchaSupport, Descriptor<CaptchaSupport>> all() {
        return Jenkins.getInstance().<CaptchaSupport, Descriptor<CaptchaSupport>>getDescriptorList(CaptchaSupport.class);
    }
    
    abstract public  boolean validateCaptcha(String id, String text); 
    
    abstract public void generateImage(String id, OutputStream ios) throws IOException;

    public CaptchaSupportDescriptor getDescriptor() {
        return (CaptchaSupportDescriptor)super.getDescriptor();
    }
}
