package jenkins.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Abstracts away how to set a value to field or via a setter method.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class Setter {

    /**
     * Sets the given value to the method/field that this {@link Setter} encapsulates.
     */
    abstract void set(Object instance, Object value) throws Exception;

    /**
     * Human readable display name use to report an error
     */
    abstract String getDisplayName();

    /**
     * True if this setter is {@link Deprecated}.
     */
    abstract boolean isDeprecated();


    static Setter create(final Method m) {
        m.setAccessible(true);

        return new Setter() {
            @Override
            void set(Object instance, Object value) throws Exception {
                m.invoke(instance,value);
            }

            @Override
            String getDisplayName() {
                return m.getDeclaringClass()+"."+m.getName()+"()";
            }

            @Override
            boolean isDeprecated() {
                return m.getAnnotation(Deprecated.class) != null;
            }
        };
    }

    static Setter create(final Field f) {
        f.setAccessible(true);

        return new Setter() {
            @Override
            void set(Object instance, Object value) throws Exception {
                f.set(instance,value);
            }

            @Override
            String getDisplayName() {
                return f.getDeclaringClass()+"."+f.getName();
            }

            @Override
            boolean isDeprecated() {
                return f.getAnnotation(Deprecated.class) != null;
            }
        };
    }
}
