/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.util;

import hudson.Util;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilities to reduce memory footprint
 * @author Sam Van Oort
 */
public class MemoryReductionUtil {
    /** Returns the capacity we need to allocate for a HashMap so it will hold all elements without needing to resize. */
    public static int preallocatedHashmapCapacity(int elementsToHold) {
        if (elementsToHold <= 0) {
            return 0;
        } else if (elementsToHold < 3) {
            return elementsToHold+1;
        } else {
            return elementsToHold+elementsToHold/3; // Default load factor is 0.75, so we want to fill that much.
        }
    }

    /** Returns a mutable HashMap presized to hold the given number of elements without needing to resize. */
    public static Map getPresizedMutableMap(int elementCount) {
        return new HashMap(preallocatedHashmapCapacity(elementCount));
    }

    /** Empty string array, exactly what it says on the tin. Avoids repeatedly created empty array when calling "toArray." */
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    /** Returns the input strings, but with all values interned. */
    public static String[] internInPlace(String[] input) {
        if (input == null) {
            return null;
        } else if (input.length == 0) {
            return EMPTY_STRING_ARRAY;
        }
        for (int i=0; i<input.length; i++) {
            input[i] = Util.intern(input[i]);
        }
        return input;
    }

}
