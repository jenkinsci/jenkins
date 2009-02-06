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
package hudson.util;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.xml.sax.Attributes;

/**
 * {@link Digester} wrapper to fix the issue DIGESTER-118.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.125
 */
public class Digester2 extends Digester {
    @Override
    public void addObjectCreate(String pattern, Class clazz) {
        addRule(pattern,new ObjectCreateRule2(clazz));
    }

    private static final class ObjectCreateRule2 extends Rule {
        private final Class clazz;
        
        public ObjectCreateRule2(Class clazz) {
            this.clazz = clazz;
        }

        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            Object instance = clazz.newInstance();
            digester.push(instance);
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            digester.pop();
        }
    }
}
