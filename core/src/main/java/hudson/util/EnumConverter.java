package hudson.util;

import org.apache.commons.beanutils.Converter;

/**
 * {@link Converter} for enums. Used for form binding.
 * @author Kohsuke Kawaguchi
 */
public class EnumConverter implements Converter {
    public Object convert(Class aClass, Object object) {
        return Enum.valueOf(aClass,object.toString());
    }
}
