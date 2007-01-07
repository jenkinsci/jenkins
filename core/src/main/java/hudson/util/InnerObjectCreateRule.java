package hudson.util;

import org.apache.commons.digester.Rule;
import org.apache.commons.digester.Digester;
import org.xml.sax.Attributes;

import java.lang.reflect.Constructor;

/**
 * {@link Digester} rule to create instances of inner class.
 * 
 * @author Kohsuke Kawaguchi
 */
public class InnerObjectCreateRule extends Rule {
    private final Constructor ctr;

    public InnerObjectCreateRule(Class clazz, Class outerClass) {
        try {
            this.ctr = clazz.getConstructor(outerClass);
        } catch (NoSuchMethodException e) {
            NoSuchMethodError x = new NoSuchMethodError();
            x.initCause(e);
            throw x;
        }
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        Object instance = ctr.newInstance(digester.peek());
        digester.push(instance);
    }

    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }
}
