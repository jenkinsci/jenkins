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

import org.jinterop.dcom.common.IJIAuthInfo;
import org.jinterop.dcom.common.JIException;
import org.jinterop.winreg.IJIWinReg;
import org.jinterop.winreg.JIPolicyHandle;
import org.jinterop.winreg.JIWinRegFactory;

import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    if (matches(keyName, major, minor))
                        return true;
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

    /**
     * Returns true if the .NET framework of the given version (or grater) is installed
     * on a remote machine. 
     */
    public static boolean isInstalled(int major, int minor, String targetMachine, IJIAuthInfo session) throws JIException, UnknownHostException {
        IJIWinReg registry = JIWinRegFactory.getSingleTon().getWinreg(session,targetMachine,true);
        JIPolicyHandle hklm=null;
        JIPolicyHandle key=null;

        try {
            hklm = registry.winreg_OpenHKLM();
            key = registry.winreg_OpenKey(hklm,"SOFTWARE\\Microsoft\\.NETFramework", IJIWinReg.KEY_READ );

            for( int i=0; ; i++ ) {
                String keyName = registry.winreg_EnumKey(key,i)[0];
                if(matches(keyName,major,minor))
                    return true;
            }
        } catch (JIException e) {
            if(e.getErrorCode()==2)
                return false;       // not found
            throw e;
        } finally {
            if(hklm!=null)
                registry.winreg_CloseKey(hklm);
            if(key!=null)
                registry.winreg_CloseKey(key);
            registry.closeConnection();
        }
    }

    private static boolean matches(String keyName, int major, int minor) {
        Matcher m = VERSION_PATTERN.matcher(keyName);
        if(m.matches()) {
            int mj = Integer.parseInt(m.group(1));
            if(mj>=major) {
                int mn = Integer.parseInt(m.group(2));
                if(mn>=minor)
                    return true;
            }
        }
        return false;
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+).*");
}
