/*
 * The MIT License
 *
 * Copyright (c) 2026, Carrie Chang
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

package hudson.model.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheckGoodNameLengthLabelsCharacterizationTest {

    private static final String LONG_CLEAN_NAME = "a".repeat(1000);

    /**
     * Long clean names are not flagged for escaping today.
     */
    @Test
    void needsEscape_longCleanName() {
        assertFalse(LabelAtom.needsEscape(LONG_CLEAN_NAME));
    }

    /**
     * escape() returns the name unquoted when needsEscape() is false.
     */
    @Test
    void escape_longCleanName() {
        assertEquals(LONG_CLEAN_NAME, LabelAtom.escape(LONG_CLEAN_NAME));
    }

    /**
     * Escape decision is driven by character content, not by length.
     */
    @Test
    void needsEscape_longNameWithSpace() {
        assertTrue(LabelAtom.needsEscape(LONG_CLEAN_NAME + " "));
    }
}
