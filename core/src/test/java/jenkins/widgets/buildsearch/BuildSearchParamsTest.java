/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package jenkins.widgets.buildsearch;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BuildSearchParamsTest {

    @Test
    public void test_no_params() {
        Assert.assertEquals("{name=[], desc=[], result=[], date-from=[], date-to=[]}", new BuildSearchParams("").toString());
        Assert.assertEquals("{name=[], desc=[], result=[], date-from=[], date-to=[]}", new BuildSearchParams("blah").toString());
        Assert.assertEquals("{name=[], desc=[], result=[], date-from=[], date-to=[]}", new BuildSearchParams("name:  desc:  ").toString()); // whitespace only params should be ignored
    }

    @Test
    public void test_unknown_search_term() {
        try {
            new BuildSearchParams("").getParams("blah");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Unknown search term 'blah'.", e.getMessage());
        }
    }

    @Test
    public void test_with_params_one_of_each() {
        Assert.assertEquals(
                "{name=[name:'Build1'], desc=[desc:'something big'], result=[result:'FAILED'], date-from=[], date-to=[]}",
                new BuildSearchParams(" result: FAILED Name: Build1 desc: something big ").toString());
    }

    @Test
    public void test_with_params_multiples_of_each() {
        BuildSearchParams searchParams =
                new BuildSearchParams(" result: FAILED name: Build1 desc: something big result:SUCCESS desc: something middle ");

        Assert.assertEquals("[name:'Build1']",
                searchParams.getParams("name").toString());
        Assert.assertEquals("[desc:'something big', desc:'something middle']",
                searchParams.getParams("desc").toString());
        Assert.assertEquals("[result:'FAILED', result:'SUCCESS']",
                searchParams.getParams("result").toString());
        Assert.assertEquals(
                "{name=[name:'Build1'], desc=[desc:'something big', desc:'something middle'], result=[result:'FAILED', result:'SUCCESS'], date-from=[], date-to=[]}",
                searchParams.toString());
    }
}
