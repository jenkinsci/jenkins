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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Field;

/**
 * @deprecated use {@code org.apache.commons.lang.reflect.FieldUtils}
 */
@Deprecated
public final class FieldUtils {

    public static Object getProtectedFieldValue(@NonNull String protectedField, @NonNull Object object) {
        try {
            Field field = getField(object.getClass(), protectedField);
            return field.get(object);
        } catch (IllegalAccessException x) {
            throw new RuntimeException(x);
        }
    }

    public static void setProtectedFieldValue(String protectedField, Object object, Object newValue) {
        try {
            // acgegi would silently fail to write to final fields
            // FieldUtils.writeField(Object, field, true) only sets accessible on *non* public fields
            // and then fails with IllegalAccessException (even if you make the field accessible in the interim!
            // for backwards compatability we need to use a few steps
            Field field = getField(object.getClass(), protectedField);
            field.setAccessible(true);
            field.set(object, newValue);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Return the field with the given name from the class or its superclasses.
     * If the field is not found, an {@link IllegalArgumentException} is thrown.
     *
     * @param clazz the class to search for the field
     * @param fieldName the name of the field to find
     * @return the {@link Field} object representing the field
     * @throws IllegalArgumentException if the field is not found
     */
    private static Field getField(@NonNull final Class<?> clazz, @NonNull final String fieldName) {
        // Check class and its superclasses
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // Continue to check superclass
            }
            current = current.getSuperclass();
        }

        // Check interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Field field = iface.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // Continue to check next interface
            }
        }

        throw new IllegalArgumentException("Field '" + fieldName + "' not found in class " + clazz.getName());
    }

    // TODO other methods as needed

    private FieldUtils() {}

}
