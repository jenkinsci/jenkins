/*
 * The MIT License
 *
 * Copyright (c) 2010, Seiji Sogabe
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
package hudson.util;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author sogabe
 */
public class FormValidationTest {

    @Test
    public void testValidateRequired_OK() {
        FormValidation actual = FormValidation.validateRequired("Name");
        assertEquals(FormValidation.ok(), actual);
    }

    @Test
    public void testValidateRequired_Null() {
        FormValidation actual = FormValidation.validateRequired(null);
        assertNotNull(actual);
        assertEquals(FormValidation.Kind.ERROR, actual.kind);
    }

    @Test
    public void testValidateRequired_Empty() {
        FormValidation actual = FormValidation.validateRequired("  ");
        assertNotNull(actual);
        assertEquals(FormValidation.Kind.ERROR, actual.kind);
    }

    // @Issue("JENKINS-7438")
    @Test
    public void testMessage() {
        assertEquals("test msg", FormValidation.errorWithMarkup("test msg").getMessage());
    }

    @Test
    public void aggregateZeroValidations() {
        assertEquals(FormValidation.ok(), aggregate());
    }

    @Test
    public void aggregateSingleValidations() {
        FormValidation ok = FormValidation.ok();
        FormValidation warning = FormValidation.warning("");
        FormValidation error = FormValidation.error("");

        assertEquals(ok, aggregate(ok));
        assertEquals(warning, aggregate(warning));
        assertEquals(error, aggregate(error));
    }

    @Test
    public void aggregateSeveralValidations() {
        FormValidation ok = FormValidation.ok("ok_message");
        FormValidation warning = FormValidation.warning("warning_message");
        FormValidation error = FormValidation.error("error_message");

        final FormValidation ok_ok = aggregate(ok, ok);
        assertEquals(FormValidation.Kind.OK, ok_ok.kind);
        assertTrue(ok_ok.renderHtml().contains(ok.getMessage()));

        final FormValidation ok_warning = aggregate(ok, warning);
        assertEquals(FormValidation.Kind.WARNING, ok_warning.kind);
        assertTrue(ok_warning.renderHtml().contains(ok.getMessage()));
        assertTrue(ok_warning.renderHtml().contains(warning.getMessage()));

        final FormValidation ok_error = aggregate(ok, error);
        assertEquals(FormValidation.Kind.ERROR, ok_error.kind);
        assertTrue(ok_error.renderHtml().contains(ok.getMessage()));
        assertTrue(ok_error.renderHtml().contains(error.getMessage()));

        final FormValidation warninig_error = aggregate(warning, error);
        assertEquals(FormValidation.Kind.ERROR, warninig_error.kind);
        assertTrue(warninig_error.renderHtml().contains(error.getMessage()));
        assertTrue(warninig_error.renderHtml().contains(warning.getMessage()));
    }

    private FormValidation aggregate(FormValidation... fvs) {
        return FormValidation.aggregate(Arrays.asList(fvs));
    }

    @Test
    public void formValidationException() {
        FormValidation fv = FormValidation.error(new Exception("<html"), "Message<html");
        assertThat(fv.renderHtml(), not(containsString("<html")));
    }
}
