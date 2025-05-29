/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunParameterValueTest {

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test
    void robustness() {
        RunParameterValue rpv = new RunParameterValue("whatever", "folder/job#57");
        assertEquals("whatever", rpv.getName());
        assertEquals("folder/job", rpv.getJobName());
        assertEquals("57", rpv.getNumber());

        assertThrows(IllegalArgumentException.class, () -> new RunParameterValue("whatever", null));
        assertThrows(IllegalArgumentException.class, () -> new RunParameterValue("whatever", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> new RunParameterValue("whatever", "invalid", "desc"));

        rpv = (RunParameterValue) Run.XSTREAM2.fromXML("<hudson.model.RunParameterValue><name>whatever</name><runId>bogus</runId></hudson.model.RunParameterValue>");
        assertEquals("whatever", rpv.getName());
        assertNull(rpv.getJobName());
        assertNull(rpv.getNumber());
   }

}
