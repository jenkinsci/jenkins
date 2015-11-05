/*
Copyright (c) 2007 Thomas Boerkel, All Rights Reserved

Disclaimer:
===========
This code is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.
 */
package hudson.util.jna;

import com.sun.jna.ptr.IntByReference;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Represents a Win32 registry key.
 *
 * @author Kohsuke Kawaguchi
 */
public class RegistryKey {
    /**
     * 32bit Windows key value.
     */
    private int handle;

    private final RegistryKey root;
    private final String path;

    /**
     * Constructor for the root key.
     */
    private RegistryKey(int handle) {
        this.handle = handle;
        root = this;
        path = "";
    }

    private RegistryKey(RegistryKey ancestor, String path,int handle) {
        this.handle = handle;
        this.root = ancestor.root;
        this.path = combine(ancestor.path,path);
    }

    private static String combine(String a, String b) {
        if(a.length()==0)   return b;
        if(b.length()==0)   return a;
        return a+'\\'+b;
    }

    /**
     * Converts a Windows buffer to a Java String.
     *
     * @param buf buffer
     * @throws java.io.UnsupportedEncodingException on error
     * @return String
     */
    private static String convertBufferToString(byte[] buf) {
        try {
            return new String(buf, 0, buf.length - 2, "UTF-16LE");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);    // impossible
        }
    }

    /**
     * Converts a Windows buffer to an int.
     *
     * @param buf buffer
     * @return int
     */
    private static int convertBufferToInt(byte[] buf) {
        return (((int) (buf[0] & 0xff)) + (((int) (buf[1] & 0xff)) << 8) + (((int) (buf[2] & 0xff)) << 16) + (((int) (buf[3] & 0xff)) << 24));
    }

    public String getStringValue(String valueName) {
        return convertBufferToString(getValue(valueName));
    }

    /**
     * Read an int value.
     */
    public int getIntValue(String valueName) {
        return convertBufferToInt(getValue(valueName));
    }

    private byte[] getValue(String valueName) {
        IntByReference pType, lpcbData;
        byte[] lpData = new byte[1];

        pType = new IntByReference();
        lpcbData = new IntByReference();

        OUTER:
        while(true) {
            int r = Advapi32.INSTANCE.RegQueryValueEx(handle, valueName, null, pType, lpData, lpcbData);
            switch (r) {
            case WINERROR.ERROR_MORE_DATA:
                lpData = new byte[lpcbData.getValue()];
                continue OUTER;

            case WINERROR.ERROR_SUCCESS:
                return lpData;
            }
            throw new JnaException(r);
        }
    }

    public void deleteValue(String valueName) {
        check(Advapi32.INSTANCE.RegDeleteValue(handle, valueName));
    }

    private void check(int r) {
        if (r != WINERROR.ERROR_SUCCESS)
            throw new JnaException(r);
    }

    /**
     * Writes a String value.
     */
    public void setValue(String name, String value) {
        try {
            byte[] bytes = value.getBytes("UTF-16LE");
            int newLength = bytes.length+2; // for 0 padding
            byte[] with0 = new byte[newLength];
            System.arraycopy(bytes, 0, with0, 0, newLength);
            check(Advapi32.INSTANCE.RegSetValueEx(handle, name, 0, WINNT.REG_SZ, with0, with0.length));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Writes a DWORD value.
     */
    public void setValue(String name, int value) {
        byte[] data = new byte[4];
        data[0] = (byte) (value & 0xff);
        data[1] = (byte) ((value >> 8) & 0xff);
        data[2] = (byte) ((value >> 16) & 0xff);
        data[3] = (byte) ((value >> 24) & 0xff);

        check(Advapi32.INSTANCE.RegSetValueEx(handle, name, 0, WINNT.REG_DWORD, data, data.length));
    }

    /**
     * Does a specified value exist?
     */
    public boolean valueExists(String name) {
        IntByReference pType, lpcbData;
        byte[] lpData = new byte[1];

        pType = new IntByReference();
        lpcbData = new IntByReference();

        OUTER:
        while(true) {
            int r = Advapi32.INSTANCE.RegQueryValueEx(handle, name, null, pType, lpData, lpcbData);
            switch(r) {
            case WINERROR.ERROR_MORE_DATA:
                lpData = new byte[lpcbData.getValue()];
                continue OUTER;
            case WINERROR.ERROR_FILE_NOT_FOUND:
                return false;
            case WINERROR.ERROR_SUCCESS:
                return true;
            default:
                throw new JnaException(r);
            }
        }
    }

    /**
     * Deletes this key (and disposes the key.)
     */
    public void delete() {
        check(Advapi32.INSTANCE.RegDeleteKey(handle, path));
        dispose();
    }

    /**
     * Get all sub keys of a key.
     *
     * @return array with all sub key names
     */
    public Collection<String> getSubKeys() {
        WINBASE.FILETIME lpftLastWriteTime;
        TreeSet<String> subKeys = new TreeSet<String>();
        char[] lpName = new char[256];
        IntByReference lpcName = new IntByReference(256);
        lpftLastWriteTime = new WINBASE.FILETIME();
        int dwIndex = 0;

        while (Advapi32.INSTANCE.RegEnumKeyEx(handle, dwIndex, lpName, lpcName, null,
                null, null, lpftLastWriteTime) == WINERROR.ERROR_SUCCESS) {
            subKeys.add(new String(lpName, 0, lpcName.getValue()));
            lpcName.setValue(256);
            dwIndex++;
        }

        return subKeys;
    }

    public RegistryKey open(String subKeyName) {
        return open(subKeyName,0xF003F/*KEY_ALL_ACCESS*/);
    }

    public RegistryKey openReadonly(String subKeyName) {
        return open(subKeyName,0x20019/*KEY_READ*/);
    }

    public RegistryKey open(String subKeyName, int access) {
        IntByReference pHandle = new IntByReference();
        check(Advapi32.INSTANCE.RegOpenKeyEx(handle, subKeyName, 0, access, pHandle));
        return new RegistryKey(this,subKeyName,pHandle.getValue());
    }

    /**
     * Get all values under a key.
     *
     * @return TreeMap with name and value pairs
     */
    public TreeMap<String, Object> getValues() {
        int dwIndex, result;
        char[] lpValueName;
        byte[] lpData;
        IntByReference lpcchValueName, lpType, lpcbData;
        String name;
        TreeMap<String, Object> values = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);

        lpValueName = new char[16384];
        lpcchValueName = new IntByReference(16384);
        lpType = new IntByReference();
        lpData = new byte[1];
        lpcbData = new IntByReference();
        lpcbData.setValue(0);

        dwIndex = 0;

        OUTER:
        while (true) {
            result = Advapi32.INSTANCE.RegEnumValue(handle, dwIndex, lpValueName, lpcchValueName, null,
                    lpType, lpData, lpcbData);
            switch (result) {
            case WINERROR.ERROR_NO_MORE_ITEMS:
                return values;

            case WINERROR.ERROR_MORE_DATA:
                lpData = new byte[lpcbData.getValue()];
                lpcchValueName = new IntByReference(16384);
                continue OUTER;

            case WINERROR.ERROR_SUCCESS:
                name = new String(lpValueName, 0, lpcchValueName.getValue());

                switch (lpType.getValue()) {
                case WINNT.REG_SZ:
                    values.put(name, convertBufferToString(lpData));
                    break;
                case WINNT.REG_DWORD:
                    values.put(name, convertBufferToInt(lpData));
                    break;
                default:
                    break; // not supported yet
                }
                break;
            
            default:
                check(result);
            }
            dwIndex++;
            lpcbData.setValue(0);
        }
    }


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }

    public void dispose() {
        if(handle!=0)
            Advapi32.INSTANCE.RegCloseKey(handle);
        handle = 0;
    }

    //
// Root keys
//
    public static final RegistryKey CLASSES_ROOT = new RegistryKey(0x80000000);
    public static final RegistryKey CURRENT_USER = new RegistryKey(0x80000001);
    public static final RegistryKey LOCAL_MACHINE = new RegistryKey(0x80000002);
    public static final RegistryKey USERS = new RegistryKey(0x80000003);
}
