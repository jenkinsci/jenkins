/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util.jna;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * .NET related code.
 * 
 * @author Kohsuke Kawaguchi
 */
public class DotNet {
    /**
     * Returns true if the .NET framework of the given version (or greater) is installed.
     */
    public static boolean isInstalled(int major, int minor) {
        try {
// see http://support.microsoft.com/?scid=kb;en-us;315291 for the basic algorithm
            // observation in my registry shows that the actual key name can be things like "v2.0 SP1"
            // or "v2.0.50727", so the regexp is written to accomodate this.
            RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly("SOFTWARE\\Microsoft\\.NETFramework");
            try {
                for( String keyName : key.getSubKeys() ) {
                    Matcher m = VERSION_PATTERN.matcher(keyName);
                    if(m.matches()) {
                        int mj = Integer.parseInt(m.group(1));
                        if(mj>=major) {
                            int mn = Integer.parseInt(m.group(2));
                            if(mn>=minor)
                                return true;
                        }
                    }
                }
                return false;
            } finally {
                key.dispose();
            }
        } catch (JnaException e) {
            if(e.getErrorCode()==2) // thrown when openReadonly fails because the key doesn't exist.
                return false;
            throw e;
        }
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+).*");
}
