/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.org.apache.commons.validator.routines;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.regex.PatternSyntaxException;
import junit.framework.TestCase;

/**
 * Test Case for RegexValidatorTest.
 *
 * @version $Revision$
 * @since Validator 1.4
 */
public class RegexValidatorTest extends TestCase {

    private static final String REGEX         = "^([abc]*)(?:\\-)([DEF]*)(?:\\-)([123]*)$";

    private static final String COMPONENT_1 = "([abc]{3})";
    private static final String COMPONENT_2 = "([DEF]{3})";
    private static final String COMPONENT_3 = "([123]{3})";
    private static final String SEPARATOR_1  = "(?:\\-)";
    private static final String SEPARATOR_2  = "(?:\\s)";
    private static final String REGEX_1 = "^" + COMPONENT_1 + SEPARATOR_1 + COMPONENT_2 + SEPARATOR_1 + COMPONENT_3 + "$";
    private static final String REGEX_2 = "^" + COMPONENT_1 + SEPARATOR_2 + COMPONENT_2 + SEPARATOR_2 + COMPONENT_3 + "$";
    private static final String REGEX_3 = "^" + COMPONENT_1 + COMPONENT_2 + COMPONENT_3 + "$";
    private static final String[] MULTIPLE_REGEX = new String[] {REGEX_1, REGEX_2, REGEX_3};

    /**
     * Constrct a new test case.
     * @param name The name of the test
     */
    public RegexValidatorTest(String name) {
        super(name);
    }

    /**
     * Test instance methods with single regular expression.
     */
    public void testSingle() {
        RegexValidator sensitive   = new RegexValidator(REGEX);
        RegexValidator insensitive = new RegexValidator(REGEX, false);

        // isValid()
        assertTrue("Sensitive isValid() valid", sensitive.isValid("ac-DE-1"));
        assertFalse("Sensitive isValid() invalid", sensitive.isValid("AB-de-1"));
        assertTrue("Insensitive isValid() valid", insensitive.isValid("AB-de-1"));
        assertFalse("Insensitive isValid() invalid", insensitive.isValid("ABd-de-1"));

        // validate()
        assertEquals("Sensitive validate() valid",     "acDE1", sensitive.validate("ac-DE-1"));
        assertNull("Sensitive validate() invalid", sensitive.validate("AB-de-1"));
        assertEquals("Insensitive validate() valid",   "ABde1", insensitive.validate("AB-de-1"));
        assertNull("Insensitive validate() invalid", insensitive.validate("ABd-de-1"));

        // match()
        checkArray("Sensitive match() valid",     new String[] {"ac", "DE", "1"}, sensitive.match("ac-DE-1"));
        checkArray("Sensitive match() invalid",   null,                           sensitive.match("AB-de-1"));
        checkArray("Insensitive match() valid",   new String[] {"AB", "de", "1"}, insensitive.match("AB-de-1"));
        checkArray("Insensitive match() invalid", null,                           insensitive.match("ABd-de-1"));
        assertEquals("validate one", "ABC", (new RegexValidator("^([A-Z]*)$")).validate("ABC"));
        checkArray("match one", new String[] {"ABC"}, (new RegexValidator("^([A-Z]*)$")).match("ABC"));
    }

    /**
     * Test with multiple regular expressions (case sensitive).
     */
    public void testMultipleSensitive() {

        // ------------ Set up Sensitive Validators
        RegexValidator multiple   = new RegexValidator(MULTIPLE_REGEX);
        RegexValidator single1   = new RegexValidator(REGEX_1);
        RegexValidator single2   = new RegexValidator(REGEX_2);
        RegexValidator single3   = new RegexValidator(REGEX_3);

        // ------------ Set up test values
        String value = "aac FDE 321";
        String expect = "aacFDE321";
        String[] array = new String[] {"aac", "FDE", "321"};

        // isValid()
        assertTrue("Sensitive isValid() Multiple", multiple.isValid(value));
        assertFalse("Sensitive isValid() 1st", single1.isValid(value));
        assertTrue("Sensitive isValid() 2nd", single2.isValid(value));
        assertFalse("Sensitive isValid() 3rd", single3.isValid(value));

        // validate()
        assertEquals("Sensitive validate() Multiple", expect, multiple.validate(value));
        assertNull("Sensitive validate() 1st", single1.validate(value));
        assertEquals("Sensitive validate() 2nd",      expect, single2.validate(value));
        assertNull("Sensitive validate() 3rd", single3.validate(value));

        // match()
        checkArray("Sensitive match() Multiple", array, multiple.match(value));
        checkArray("Sensitive match() 1st",      null,  single1.match(value));
        checkArray("Sensitive match() 2nd",      array, single2.match(value));
        checkArray("Sensitive match() 3rd",      null,  single3.match(value));

        // All invalid
        value = "AAC*FDE*321";
        assertFalse("isValid() Invalid", multiple.isValid(value));
        assertNull("validate() Invalid", multiple.validate(value));
        assertNull("match() Multiple", multiple.match(value));
    }

    /**
     * Test with multiple regular expressions (case in-sensitive).
     */
    public void testMultipleInsensitive() {

        // ------------ Set up In-sensitive Validators
        RegexValidator multiple = new RegexValidator(MULTIPLE_REGEX, false);
        RegexValidator single1   = new RegexValidator(REGEX_1, false);
        RegexValidator single2   = new RegexValidator(REGEX_2, false);
        RegexValidator single3   = new RegexValidator(REGEX_3, false);

        // ------------ Set up test values
        String value = "AAC FDE 321";
        String expect = "AACFDE321";
        String[] array = new String[] {"AAC", "FDE", "321"};

        // isValid()
        assertTrue("isValid() Multiple", multiple.isValid(value));
        assertFalse("isValid() 1st", single1.isValid(value));
        assertTrue("isValid() 2nd", single2.isValid(value));
        assertFalse("isValid() 3rd", single3.isValid(value));

        // validate()
        assertEquals("validate() Multiple", expect, multiple.validate(value));
        assertNull("validate() 1st", single1.validate(value));
        assertEquals("validate() 2nd",      expect, single2.validate(value));
        assertNull("validate() 3rd", single3.validate(value));

        // match()
        checkArray("match() Multiple", array, multiple.match(value));
        checkArray("match() 1st",      null,  single1.match(value));
        checkArray("match() 2nd",      array, single2.match(value));
        checkArray("match() 3rd",      null,  single3.match(value));

        // All invalid
        value = "AAC*FDE*321";
        assertFalse("isValid() Invalid", multiple.isValid(value));
        assertNull("validate() Invalid", multiple.validate(value));
        assertNull("match() Multiple", multiple.match(value));
    }

    /**
     * Test Null value
     */
    public void testNullValue() {

        RegexValidator validator = new RegexValidator(REGEX);
        assertFalse("Instance isValid()", validator.isValid(null));
        assertNull("Instance validate()", validator.validate(null));
        assertNull("Instance match()", validator.match(null));
    }

    /**
     * Test exceptions
     */
    public void testMissingRegex() {

        // Single Regular Expression - null
        {
            final IllegalArgumentException e = assertThrows("Single Null - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator((String) null));
            assertEquals("Single Null", "Regular expression[0] is missing", e.getMessage());
        }

        // Single Regular Expression - Zero Length
        {
            final IllegalArgumentException e = assertThrows("Single Zero Length - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator(""));
            assertEquals("Single Zero Length", "Regular expression[0] is missing", e.getMessage());
        }

        // Multiple Regular Expression - Null array
        {
            final IllegalArgumentException e = assertThrows("Null Array - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator((String[]) null));
            assertEquals("Null Array", "Regular expressions are missing", e.getMessage());
        }

        // Multiple Regular Expression - Zero Length array
        {
            final IllegalArgumentException e = assertThrows("Zero Length Array - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator(new String[0]));
            assertEquals("Zero Length Array", "Regular expressions are missing", e.getMessage());
        }

        // Multiple Regular Expression - Array has Null
        {
            String[] expressions = new String[] {"ABC", null};
            final IllegalArgumentException e = assertThrows("Array has Null - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator(expressions));
            assertEquals("Array has Null", "Regular expression[1] is missing", e.getMessage());
        }

        // Multiple Regular Expression - Array has Zero Length
        {
            String[] expressions = new String[] {"", "ABC"};
            final IllegalArgumentException e = assertThrows("Array has Zero Length - expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> new RegexValidator(expressions));
            assertEquals("Array has Zero Length", "Regular expression[0] is missing", e.getMessage());
        }
    }

    /**
     * Test exceptions
     */
    public void testExceptions() {
        String invalidRegex = "^([abCD12]*$";
        assertThrows(PatternSyntaxException.class, () -> new RegexValidator(invalidRegex));
    }

    /**
     * Test toString() method
     */
    public void testToString() {
        RegexValidator single = new RegexValidator(REGEX);
        assertEquals("Single", "RegexValidator{" + REGEX + "}", single.toString());

        RegexValidator multiple = new RegexValidator(new String[] {REGEX, REGEX});
        assertEquals("Multiple", "RegexValidator{" + REGEX + "," + REGEX + "}", multiple.toString());
    }

    /**
     * Compare two arrays
     * @param label Label for the test
     * @param expect Expected array
     * @param result Actual array
     */
    private void checkArray(String label, String[] expect, String[] result) {

        // Handle nulls
        if (expect == null || result == null) {
            if (expect == null && result == null) {
                return; // valid, both null
            } else {
                fail(label + " Null expect=" + Arrays.toString(expect) + " result=" + Arrays.toString(result));
            }
            return; // not strictly necessary, but prevents possible NPE below
        }

        // Check Length
        if (expect.length != result.length) {
            fail(label + " Length expect=" + expect.length + " result=" + result.length);
        }

        // Check Values
        for (int i = 0; i < expect.length; i++) {
            assertEquals(label + " value[" + i + "]", expect[i], result[i]);
        }
    }

}
