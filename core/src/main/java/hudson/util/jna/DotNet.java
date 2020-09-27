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

/**
 * .NET related code.
 * 
 * @author Kohsuke Kawaguchi
 */
public class DotNet {
    private static final String PATH20 = "SOFTWARE\\Microsoft\\NET Framework Setup\\NDP\\v2.0.50727";
    private static final String PATH30 = "SOFTWARE\\Microsoft\\NET Framework Setup\\NDP\\v3.0\\Setup";
    private static final String PATH35 = "SOFTWARE\\Microsoft\\NET Framework Setup\\NDP\\v3.5";
    private static final String PATH4  = "SOFTWARE\\Microsoft\\NET Framework Setup\\NDP\\v4\\Full";

    private static final String VALUE_INSTALL = "Install";
    private static final String VALUE_INSTALL_SUCCESS = "InstallSuccess";
    private static final String VALUE_RELEASE = "Release";

    /**
     * Returns true if the .NET framework of a compatible version is installed.
     */
    public static boolean isInstalled(int major, int minor) {
        try {
            if (major == 4 && minor >= 5) {
                return isV45PlusInstalled(minor);
            } else if (major == 4 && minor == 0) {
                return isV40Installed();
            } else if (major == 3 && minor == 5) {
                return isV35Installed();
            } else if (major == 3 && minor == 0) {
                return isV35Installed() || isV30Installed();
            } else if (major == 2 && minor == 0) {
                return isV35Installed() || isV30Installed() || isV20Installed();
            } else {
                return false;
            }
        } catch (JnaException e) {
            if (e.getErrorCode() == 2) {
                // thrown when openReadonly fails because the key doesn't exist.
                return false;
            }
            throw e;
        }
    }

    private static boolean isV45PlusInstalled(int minor) {
        try (RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly(PATH4)) {
            return key.getIntValue(VALUE_RELEASE) >= GetV45PlusMinRelease(minor);
        }
    }

    private static boolean isV40Installed() {
        try (RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly(PATH4)) {
            return key.getIntValue(VALUE_INSTALL) == 1;
        }
    }

    private static boolean isV35Installed() {
        try (RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly(PATH35)) {
            return key.getIntValue(VALUE_INSTALL) == 1;
        }
    }

    private static boolean isV30Installed() {
        try (RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly(PATH30)) {
            return key.getIntValue(VALUE_INSTALL_SUCCESS) == 1;
        }
    }

    private static boolean isV20Installed() {
        try (RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly(PATH20)) {
            return key.getIntValue(VALUE_INSTALL) == 1;
        }
    }

    /**
     * Returns true if the .NET framework of a compatible version is installed on a remote machine. 
     */
    public static boolean isInstalled(int major, int minor, String targetMachine, IJIAuthInfo session) throws JIException, UnknownHostException {
        IJIWinReg registry = JIWinRegFactory.getSingleTon().getWinreg(session, targetMachine, true);
        JIPolicyHandle hklm = null;
        try {
            hklm = registry.winreg_OpenHKLM();
            if (major == 4 && minor >= 5) {
                return isV45PlusInstalled(minor, registry, hklm);
            } else if (major == 4 && minor == 0) {
                return isV40Installed(registry, hklm);
            } else if (major == 3 && minor == 5) {
                return isV35Installed(registry, hklm);
            } else if (major == 3 && minor == 0) {
                return isV35Installed(registry, hklm) || isV30Installed(registry, hklm);
            } else if (major == 2 && minor == 0) {
                return isV35Installed(registry, hklm) || isV30Installed(registry, hklm) || isV20Installed(registry, hklm);
            } else {
                return false;
            }
        } catch (JIException e) {
            if (e.getErrorCode() == 2) {
                // not found
                return false;
            }
            throw e;
        } finally {
            if (hklm != null) {
                registry.winreg_CloseKey(hklm);
            }
            registry.closeConnection();
        }
    }

    private static boolean isV45PlusInstalled(int minor, IJIWinReg registry, JIPolicyHandle hklm) throws JIException {
        JIPolicyHandle key = null;
        try {
            key = registry.winreg_OpenKey(hklm, PATH4, IJIWinReg.KEY_READ);
            return GetIntValue(registry, key, VALUE_RELEASE) >= GetV45PlusMinRelease(minor);
        } finally {
            if (key != null) {
                registry.winreg_CloseKey(key);
            }
        }
    }

    private static boolean isV40Installed(IJIWinReg registry, JIPolicyHandle hklm) throws JIException {
        JIPolicyHandle key = null;
        try {
            key = registry.winreg_OpenKey(hklm, PATH4, IJIWinReg.KEY_READ);
            return GetIntValue(registry, key, VALUE_INSTALL) == 1;
        } finally {
            if (key != null) {
                registry.winreg_CloseKey(key);
            }
        }
    }

    private static boolean isV35Installed(IJIWinReg registry, JIPolicyHandle hklm) throws JIException {
        JIPolicyHandle key = null;
        try {
            key = registry.winreg_OpenKey(hklm, PATH35, IJIWinReg.KEY_READ);
            return GetIntValue(registry, key, VALUE_INSTALL) == 1;
        } finally {
            if (key != null) {
                registry.winreg_CloseKey(key);
            }
        }
    }

    private static boolean isV30Installed(IJIWinReg registry, JIPolicyHandle hklm) throws JIException {
        JIPolicyHandle key = null;
        try {
            key = registry.winreg_OpenKey(hklm, PATH30, IJIWinReg.KEY_READ);
            return GetIntValue(registry, key, VALUE_INSTALL_SUCCESS) == 1;
        } finally {
            if (key != null) {
                registry.winreg_CloseKey(key);
            }
        }
    }

    private static boolean isV20Installed(IJIWinReg registry, JIPolicyHandle hklm) throws JIException {
        JIPolicyHandle key = null;
        try {
            key = registry.winreg_OpenKey(hklm, PATH20, IJIWinReg.KEY_READ);
            return GetIntValue(registry, key, VALUE_INSTALL) == 1;
        } finally {
            if (key != null) {
                registry.winreg_CloseKey(key);
            }
        }
    }

    private static int GetIntValue(IJIWinReg registry, JIPolicyHandle key, String name) throws JIException {
        return RegistryKey.convertBufferToInt((byte[])registry.winreg_QueryValue(key, name, Integer.BYTES)[1]);
    }

    private static int GetV45PlusMinRelease(int minor) {
        switch (minor) {
            case 5:
                return 378389;
            case 6:
                return 393295;
            case 7:
                return 460798;
            case 8:
                return 528040;
            default:
                return Integer.MAX_VALUE;
        }
    }
}
