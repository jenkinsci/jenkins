/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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
package jenkins.util.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.basic.URLConverter;
import hudson.remoting.URLDeserializationHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.net.URL;
import java.net.URLStreamHandler;

/**
 * Wrap the URL handler during deserialization into a specific one that does not generate DNS query on the hostname
 * for {@link URLStreamHandler#equals(URL, URL)} or {@link URLStreamHandler#hashCode(URL)}. 
 * Required to protect against SECURITY-637
 * 
 * @since 2.121.3
 */
@Restricted(NoExternalUse.class)
public class SafeURLConverter extends URLConverter {
    
    @Override
    public Object fromString(String str) {
        URL url = (URL) super.fromString(str);
        try {
            return URLDeserializationHelper.wrapIfRequired(url);
        } catch (IOException e) {
            throw new ConversionException(e);
        }
    }
}
