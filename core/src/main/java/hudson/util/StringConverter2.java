package hudson.util;

import com.thoughtworks.xstream.converters.basic.AbstractBasicConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;

/**
 * The default {@link StringConverter} in XStream
 * uses {@link String#intern()}, which stresses the
 * (rather limited) PermGen space with a large XML file.
 *
 * <p>
 * Use this to avoid that (instead those strings will
 * now be allocated to the heap space.)
 *
 * @author Kohsuke Kawaguchi
 */
public class StringConverter2 extends AbstractBasicConverter {

    public boolean canConvert(Class type) {
        return type.equals(String.class);
    }

    protected Object fromString(String str) {
        return str;
    }

}
