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

import org.junit.Test;
import static org.junit.Assert.*;

public class RunParameterValueTest {
    
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test public void robustness() throws Exception {
        RunParameterValue rpv = new RunParameterValue("whatever", "folder/job#57");
        assertEquals("whatever", rpv.getName());
        assertEquals("folder/job", rpv.getJobName());
        assertEquals("57", rpv.getNumber());
        try {
            new RunParameterValue("whatever", null);
            fail();
        } catch (IllegalArgumentException x) {
            // good
        }
        try {
            new RunParameterValue("whatever", "invalid");
            fail();
        } catch (IllegalArgumentException x) {
            // good
        }
        try {
            new RunParameterValue("whatever", "invalid", "desc");
            fail();
        } catch (IllegalArgumentException x) {
            // good
        }
        rpv = (RunParameterValue) Run.XSTREAM2.fromXML("<hudson.model.RunParameterValue><name>whatever</name><runId>bogus</runId></hudson.model.RunParameterValue>");
        assertEquals("whatever", rpv.getName());
        assertEquals(null, rpv.getJobName());
        assertEquals(null, rpv.getNumber());
   }

}
