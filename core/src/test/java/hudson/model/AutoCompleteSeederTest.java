/*
 *  The MIT License
 * 
 *  Copyright 2010 Yahoo! Inc.
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.model;

import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import hudson.model.AbstractProject.AbstractProjectDescriptor.AutoCompleteSeeder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author dty
 */
@RunWith(Parameterized.class)
public class AutoCompleteSeederTest {

    public static class TestData {
        private String seed;
        private List<String> expected;
        
        public TestData(String seed, String... expected) {
            this.seed = seed;
            this.expected = Arrays.asList(expected);
        }
    }
    
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList( new Object[][] {
                    { new TestData("", "") },
                    { new TestData("\"", "") },
                    { new TestData("\"\"", "") },
                    { new TestData("freebsd", "freebsd") },
                    { new TestData(" freebsd", "freebsd") },
                    { new TestData("freebsd ", "") },
                    { new TestData("freebsd 6", "6") },
                    { new TestData("\"freebsd", "freebsd") },
                    { new TestData("\"freebsd ", "freebsd ") },
                    { new TestData("\"freebsd\"", "") },
                    { new TestData("\"freebsd\" ", "") },
                    { new TestData("\"freebsd 6", "freebsd 6") },
                    { new TestData("\"freebsd 6\"", "") },
               });
    }

    private String seed;
    private List<String> expected;

    public AutoCompleteSeederTest(TestData dataSet) {
        this.seed = dataSet.seed;
        this.expected = dataSet.expected;
    }

    @Test
    public void testAutoCompleteSeeds() throws Exception {
        AutoCompleteSeeder seeder = new AbstractProject.AbstractProjectDescriptor.AutoCompleteSeeder(seed);
        assertEquals(expected, seeder.getSeeds());

    }
}