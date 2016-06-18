/*
 * The MIT License
 *
 * Copyright 2014 Oleg Nenashev <o.v.nenashev@gmail.com>.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Test for {@link FileParameterValue}.
 * @author Oleg Nenashev
 */
public class FileParameterValueTest {
    
    @Issue("JENKINS-19017")
    @Test public void compareParamsWithSameName() {
        final String paramName = "MY_FILE_PARAM"; // Same paramName (location) reproduces the bug
        final FileParameterValue param1 = new FileParameterValue(paramName, new File("ws_param1.txt"), "param1.txt");
        final FileParameterValue param2 = new FileParameterValue(paramName, new File("ws_param2.txt"), "param2.txt");
        
        assertNotEquals("Files with same locations shoud be considered as different", param1, param2);
        assertNotEquals("Files with same locations shoud be considered as different", param2, param1);
    }
    
    @Test public void compareNullParams() {
        final String paramName = "MY_FILE_PARAM";     
        FileParameterValue nonNullParam = new FileParameterValue(paramName, new File("ws_param1.txt"), "param1.txt");
        FileParameterValue nullParam1 = new FileParameterValue(null, new File("null_param1.txt"), "null_param1.txt");
        FileParameterValue nullParam2 = new FileParameterValue(null, new File("null_param2.txt"), "null_param2.txt");
        
        // Combine nulls
        assertEquals(nullParam1, nullParam1);
        assertEquals(nullParam1, nullParam2);
        assertEquals(nullParam2, nullParam1);
        assertEquals(nullParam2, nullParam2);
        
        // Compare with non-null
        assertNotEquals(nullParam1, nonNullParam);
        assertNotEquals(nonNullParam, nullParam1);
    }
}
