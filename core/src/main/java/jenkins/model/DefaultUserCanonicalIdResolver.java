/*
 * The MIT License
 *
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Tom Huybrechts, Vincent Latombe
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

import hudson.Extension;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.User;

import java.util.Map;

/**
 * Default User.CanonicalIdResolver to escape unsupported characters and generate user ID.
 * Compared to other implementations, this resolver will always return an ID
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
*/
@Extension
public class DefaultUserCanonicalIdResolver extends User.CanonicalIdResolver {

    @Override
    public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
        String id = idOrFullName.replace('\\', '_').replace('/', '_').replace('<','_')
                .replace('>', '_');  // 4 replace() still faster than regex
        if (Functions.isWindows()) id = id.replace(':','_');
        return id;
    }

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public Descriptor<User.CanonicalIdResolver> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<User.CanonicalIdResolver> DESCRIPTOR = new Descriptor<User.CanonicalIdResolver>() {
        public String getDisplayName() {
            return "compute default user ID";
        }
    };

}
