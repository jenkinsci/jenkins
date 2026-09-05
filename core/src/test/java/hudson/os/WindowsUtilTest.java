/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package hudson.os;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Test the test utility code, too!
 */
class WindowsUtilTest {

    @Test
    void testQuoteArgument() {
        String input = "C:\\Programs and \"Settings\"\\System32\\\\";
        String expected = "\"C:\\Programs and \\\"Settings\\\"\\System32\\\\\\\\\"";
        String actual = WindowsUtil.quoteArgument(input);
        assertEquals(expected, actual);
    }

    @Test
    void testQuoteArgument_OnlyQuotesWhenNecessary() {
        for (String arg : Arrays.asList("", "foo", "foo-bar", "C:\\test\\path", "http://www.example.com/")) {
            assertEquals(arg, WindowsUtil.quoteArgument(arg));
        }
    }

    @Test
    void testQuoteArgumentForCmd() {
        String input = "hello \"\\world&";
        String expected = "^\"hello \\^\"\\world^&^\"";
        String actual = WindowsUtil.quoteArgumentForCmd(input);
        assertEquals(expected, actual);
    }

    @Test
    void testQuoteArgumentForCmd_OnlyQuotesWhenNecessary() {
        for (String arg : Arrays.asList("", "foo", "foo-bar", "C:\\test\\path", "http://www.example.com/")) {
            assertEquals(arg, WindowsUtil.quoteArgumentForCmd(arg));
        }
    }
}
