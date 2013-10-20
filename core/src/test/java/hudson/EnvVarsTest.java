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

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.Collections;

/**
 * @author Kohsuke Kawaguchi
 */
public class EnvVarsTest extends TestCase {
    private void resetEnvVarsCache() throws Exception {
        Field f = EnvVars.class.getDeclaredField("shouldNotCaseSensitive");
        f.setAccessible(true);
        f.set(null, null);
        f.setAccessible(false);
    }
    
    /**
     * Makes sure that {@link EnvVars} behave in case-sensitive way.
     */
    public void testCaseSensitive() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        resetEnvVarsCache();
        
        EnvVars ev = new EnvVars(Collections.singletonMap("Path","A:B:C"));
        assertFalse(ev.containsKey("PATH"));
        assertTrue(ev.containsKey("Path"));
        assertEquals("A:B:C",ev.get("Path"));
        
        ev.override("PATH+Test", "D");
        if (Functions.isWindows()) {
            // override always acts case-insensitive on Windows.
            assertFalse(ev.containsKey("PATH"));
            assertTrue(ev.containsKey("Path"));
            assertEquals("D;A:B:C", ev.get("Path"));
        } else {
            assertTrue(ev.containsKey("PATH"));
            assertEquals("D", ev.get("PATH"));
            assertEquals("A:B:C",ev.get("Path"));
        }
    }
    
    /**
     * Makes sure that {@link EnvVars} behave in case-insensitive way.
     */
    public void testCaseInsensitive() throws Exception {
        assertNull(System.getProperty(EnvVars.PROP_CASE_INSENSITIVE));
        try {
            System.setProperty(EnvVars.PROP_CASE_INSENSITIVE, "true");
            resetEnvVarsCache();
            
            EnvVars ev = new EnvVars(Collections.singletonMap("Path","A:B:C"));
            assertTrue(ev.containsKey("PATH"));
            assertEquals("A:B:C",ev.get("PATH"));
            
            ev.override("PATH+Test", "D");
            if (Functions.isWindows()) {
                assertEquals("D;A:B:C", ev.get("Path"));
                assertEquals("D;A:B:C", ev.get("PATH"));
            } else {
                assertEquals("D:A:B:C", ev.get("Path"));
                assertEquals("D:A:B:C", ev.get("PATH"));
            }
        } finally {
            System.getProperties().remove(EnvVars.PROP_CASE_INSENSITIVE);
        }
   }
}
