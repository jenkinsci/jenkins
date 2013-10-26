/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson;

import hudson.EnvVars.OverrideOrderCalculator;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class EnvVarsTest extends TestCase {
    /**
     * Makes sure that {@link EnvVars} behave in case-insensitive way.
     */
    public void test1() {
        EnvVars ev = new EnvVars(Collections.singletonMap("Path","A:B:C"));
        assertTrue(ev.containsKey("PATH"));
        assertEquals("A:B:C",ev.get("PATH"));
    }
    
    public void testOverrideExpandingAll() throws Exception {
        EnvVars env = new EnvVars();
        env.put("PATH", "orig");
        env.put("A", "Value1");
        
        EnvVars overrides = new EnvVars();
        overrides.put("PATH", "append" + Platform.current().pathSeparator + "${PATH}");
        overrides.put("B", "${A}Value2");
        overrides.put("C", "${B}${D}");
        overrides.put("D", "${E}");
        overrides.put("E", "Value3");
        overrides.put("PATH+TEST", "another");
        
        env.overrideExpandingAll(overrides);
        
        assertEquals("Value1Value2Value3", env.get("C"));
        assertEquals("another" + Platform.current().pathSeparator + "append" + Platform.current().pathSeparator + "orig", env.get("PATH"));
    }
    
    public void testOverrideOrderCalculatorSimple() {
        EnvVars env = new EnvVars();
        EnvVars overrides = new EnvVars();
        overrides.put("A", "NoReference");
        overrides.put("A+B", "NoReference");
        overrides.put("B", "Refer1${A}");
        overrides.put("C", "Refer2${B}");
        overrides.put("D", "Refer3${B}${Nosuch}");
        
        OverrideOrderCalculator calc = new OverrideOrderCalculator(env, overrides);
        assertEquals(0, calc.getRefereeNum("A"));
        assertEquals(0, calc.getRefereeNum("A+B"));
        assertEquals(0, calc.getRefereeNum("Nosuch"));
        assertEquals(1, calc.getRefereeNum("B"));
        assertEquals(2, calc.getRefereeNum("C"));
        assertEquals(3, calc.getRefereeNum("D"));
        
        List<Map.Entry<String,String>> order = calc.getOrderedVariables();
        assertEquals(5, order.size());
        assertEquals("A", order.get(0).getKey());
        assertEquals("B", order.get(1).getKey());
        assertEquals("C", order.get(2).getKey());
        assertEquals("D", order.get(3).getKey());
        assertEquals("A+B", order.get(4).getKey());
    }
    
    public void testOverrideOrderCalculatorInOrder() {
        EnvVars env = new EnvVars();
        EnvVars overrides = new EnvVars();
        overrides.put("A", "NoReference");
        overrides.put("B", "${A}");
        overrides.put("C", "${B}"); // refers A, B
        overrides.put("D", "${E}"); // refers A, B, C, E
        overrides.put("E", "${C}"); // refers A, B, C
        
        OverrideOrderCalculator calc = new OverrideOrderCalculator(env, overrides) {
            @Override
            protected Iterator<String> rawKeyIterator() {
                // should process in this order.
                return Arrays.asList("A", "B", "C", "D", "E", "F").iterator();
            }
        };
        assertEquals(0, calc.getRefereeNum("A"));
        assertEquals(1, calc.getRefereeNum("B"));
        assertEquals(2, calc.getRefereeNum("C"));
        assertEquals(4, calc.getRefereeNum("D"));
        assertEquals(3, calc.getRefereeNum("E"));
    }
    
    public void testOverrideOrderCalculatorMultiple() {
        EnvVars env = new EnvVars();
        EnvVars overrides = new EnvVars();
        overrides.put("A", "Noreference");
        overrides.put("B", "${A}");
        overrides.put("C", "${A}${B}");
        
        OverrideOrderCalculator calc = new OverrideOrderCalculator(env, overrides);
        assertEquals(0, calc.getRefereeNum("A"));
        assertEquals(1, calc.getRefereeNum("B"));
        assertEquals(2, calc.getRefereeNum("C"));
    }
    
    public void testOverrideOrderCalculatorSelfReference() {
        EnvVars env = new EnvVars();
        EnvVars overrides = new EnvVars();
        overrides.put("PATH", "some;${PATH}");
        
        OverrideOrderCalculator calc = new OverrideOrderCalculator(env, overrides);
        assertEquals(1, calc.getRefereeNum("PATH"));
    }
    
    public void testOverrideOrderCalculatorCyclic() {
        EnvVars env = new EnvVars();
        EnvVars overrides = new EnvVars();
        overrides.put("A", "${B}");
        overrides.put("B", "${C}");
        overrides.put("C", "${A}");
        
        OverrideOrderCalculator calc = new OverrideOrderCalculator(env, overrides);
        assertEquals(3, calc.getRefereeNum("A"));
        assertEquals(3, calc.getRefereeNum("B"));
        assertEquals(3, calc.getRefereeNum("C"));
    }
}
