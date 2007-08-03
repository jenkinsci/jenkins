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
