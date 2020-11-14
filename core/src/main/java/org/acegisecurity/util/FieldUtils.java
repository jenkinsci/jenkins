/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.acegisecurity.util;

/**
 * @deprecated use {@link org.apache.commons.lang.reflect.FieldUtils}
 */
@Deprecated
public final class FieldUtils {

    public static Object getProtectedFieldValue(String protectedField, Object object) {
        try {
            return org.apache.commons.lang.reflect.FieldUtils.readField(object, protectedField, true);
        } catch (IllegalAccessException x) {
            throw new RuntimeException(x);
        }
    }

    public static void setProtectedFieldValue(String protectedField, Object object, Object newValue) {
        try {
            org.apache.commons.lang.reflect.FieldUtils.writeField(object, protectedField, newValue, true);
        } catch (IllegalAccessException x) {
            throw new RuntimeException(x);
        }
    }

    // TODO other methods as needed

    private FieldUtils() {}

}
