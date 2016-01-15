package org.jvnet.hudson.test;

import hudson.util.FormValidation;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Some handy matchers.
 *
 * @author Stephen Connolly
 * @since 1.436
 */
public class JenkinsMatchers {

    public static Matcher<Class> privateConstructorThrows(Class<? extends Throwable> cause) {
        return new PrivateConstructorThrows(cause);
    }

    public static Matcher<FormValidation> causedBy(Class<? extends Throwable> cause) {
        return new FormValidationCauseMatcher(cause);
    }

    public static Matcher<FormValidation> hasKind(FormValidation.Kind kind) {
        return new FormValidationKindMatcher(kind);
    }

    private static class PrivateConstructorThrows extends BaseMatcher<Class> {
        private final Class<? extends Throwable> cause;

        public PrivateConstructorThrows(Class<? extends Throwable> cause) {
            this.cause = cause;
        }

        public boolean matches(Object o) {
            Class<?> clazz = (Class<?>) o;
            try {
                Constructor<?> c = clazz.getDeclaredConstructor();
                if (!Modifier.isPrivate(c.getModifiers())) {
                    return false;
                }
                boolean accessible = c.isAccessible();
                try {
                    c.setAccessible(true);
                    c.newInstance();
                } catch (InvocationTargetException e) {
                    Throwable t = e;
                    while (t != null) {
                        if (cause.isInstance(t)) {
                            return true;
                        }
                        t = t.getCause();
                    }
                } catch (InstantiationException e) {
                    // ignore
                } catch (IllegalAccessException e) {
                    // ignore
                } finally {
                    c.setAccessible(accessible);
                }
            } catch (NoSuchMethodException e) {
                // ignore
            }
            return false;
        }

        public void describeTo(Description description) {
            description.appendText("with a private constructor that throws ");
            description.appendValue(cause);
        }
    }

    private static class FormValidationCauseMatcher extends BaseMatcher<FormValidation> {
        private final Class<? extends Throwable> cause;

        public FormValidationCauseMatcher(Class<? extends Throwable> cause) {
            cause.getClass();
            this.cause = cause;
        }

        public boolean matches(Object o) {
            FormValidation v = (FormValidation) o;
            return v.getMessage().contains(cause.getName());
        }

        public void describeTo(Description description) {
            description.appendText("caused by ");
            description.appendValue(cause);
        }
    }

    private static class FormValidationKindMatcher extends BaseMatcher<FormValidation> {
        private final FormValidation.Kind kind;

        public FormValidationKindMatcher(FormValidation.Kind kind) {
            kind.getClass();
            this.kind = kind;
        }

        public boolean matches(Object o) {
            FormValidation v = (FormValidation) o;
            return v.kind == kind;
        }

        public void describeTo(Description description) {
            description.appendText("of kind ");
            description.appendValue(kind);
        }
    }

    /**
     * A matcher which checks that the class is a Utility class (i.e. is final and has only private constructors).
     *
     * @return A matcher which checks that the class is a Utility class (i.e. is final and has only private
     *         constructors).
     */
    public static Matcher<Class<?>> isUtilityClass() {
        return CoreMatchers.allOf(isFinalClass(), isClassWithOnlyPrivateConstructors());
    }

    /**
     * A matcher which checks that the class has only private constructors.
     *
     * @return A matcher which checks that the class has only private constructors.
     */
    public static Matcher<Class<?>> isClassWithOnlyPrivateConstructors() {
        return new IsClassWithOnlyPrivateConstructors();
    }

    /**
     * A matcher which checks that the class is final.
     *
     * @return A matcher which checks that the class is final.
     */
    public static Matcher<Class<?>> isFinalClass() {
        return new IsFinalClass();
    }

    /**
     * A matcher which checks that the class has the default constructor.
     *
     * @return A matcher which checks that the class has the default constructor.
     */
    public static Matcher<Class<?>> hasDefaultConstructor() {
        return new HasDefaultConstructor();
    }

    /**
     * A matcher that verifies that the a root cause of an exception is of the specified type.
     *
     * @param cause the type of exception that caused this.
     * @return A matcher that verifies that the a root cause of an exception is of the specified type.
     */
    public static Matcher<Throwable> hasCause(Class<? extends Throwable> cause) {
        return new HasCause(cause);
    }

    public static Matcher<Object> hasImplementedEquals() {
        return new HasImplementedEquals();
    }

    public static Matcher<Object> hasReflexiveEquals() {
        return new HasReflexiveEquals();
    }

    public static Matcher<Object> hasNonEqualityWithNulls() {
        return new HasNonNullEquals();
    }

    public static Matcher<Object> hasSymmetricEquals(Object other) {
        return new HasSymmetricEquals(other);
    }

    public static Matcher<Object> hasConsistentEquals(Object other) {
        return new HasConsistentEquals(other);
    }

    public static Matcher<Object> hasTransitiveEquals(Object a, Object b) {
        return new HasTransitiveEquals(a, b);
    }

    public static Matcher<Object> hasImplementedHashCode() {
        return new HasImplementedHashCode();
    }

    public static Matcher<Object> hasHashCodeContract(Object other) {
        return new HasHashCodeContract(other);
    }

    private static class HasDefaultConstructor
            extends BaseMatcher<Class<?>> {
        public boolean matches(Object item) {
            Class<?> clazz = (Class<?>) item;
            try {
                Constructor<?> constructor = clazz.getConstructor();
                return Modifier.isPublic(constructor.getModifiers());
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        public void describeTo(Description description) {
            description.appendText("a class with the default constructor");
        }
    }

    private static class HasImplementedEquals
            extends BaseMatcher<Object> {
        public boolean matches(Object item) {
            if (item == null) {
                return false;
            }
            Class<?> clazz = item instanceof Class ? (Class<?>) item : item.getClass();
            try {
                return !clazz.getMethod("equals", Object.class).equals(Object.class.getMethod("equals", Object.class));
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        public void describeTo(Description description) {
            description.appendText("has overridden the default equals(Object) method");
        }
    }

    private static class HasImplementedHashCode
            extends BaseMatcher<Object> {
        public boolean matches(Object item) {
            if (item == null) {
                return false;
            }
            Class<?> clazz = item instanceof Class ? (Class<?>) item : item.getClass();
            try {
                return !clazz.getMethod("hashCode").equals(Object.class.getMethod("hashCode"));
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        public void describeTo(Description description) {
            description.appendText("has overridden the default hashCode() method");
        }
    }

    private static class IsClassWithOnlyPrivateConstructors
            extends BaseMatcher<Class<?>> {
        public boolean matches(Object item) {
            Class<?> clazz = (Class<?>) item;
            for (Constructor<?> c : clazz.getConstructors()) {
                if (!Modifier.isPrivate(c.getModifiers())) {
                    return false;
                }
            }
            return true;
        }

        public void describeTo(Description description) {
            description.appendText("a class with only private constructors");
        }
    }

    private static class IsFinalClass
            extends BaseMatcher<Class<?>> {
        public boolean matches(Object item) {
            Class<?> clazz = (Class<?>) item;
            return Modifier.isFinal(clazz.getModifiers());
        }

        public void describeTo(Description description) {
            description.appendText("a final class");
        }
    }

    private static class HasCause
            extends BaseMatcher<Throwable> {
        private final Class<? extends Throwable> cause;

        public HasCause(Class<? extends Throwable> cause) {
            this.cause = cause;
        }

        public boolean matches(Object item) {
            Throwable throwable = (Throwable) item;
            while (throwable != null && !cause.isInstance(throwable)) {
                throwable = throwable.getCause();
            }
            return cause.isInstance(throwable);
        }

        public void describeTo(Description description) {
            description.appendText("was caused by a ").appendValue(cause).appendText(" being thrown");
        }
    }

    private static class HasNonNullEquals extends BaseMatcher<Object> {

        public boolean matches(Object o) {
            return o != null && !o.equals(null);
        }

        public void describeTo(Description description) {
            description.appendText("has an equals(Object) method that returns false for null");
        }
    }

    private static class HasReflexiveEquals extends BaseMatcher<Object> {

        public boolean matches(Object o) {
            return o != null && o.equals(o);
        }

        public void describeTo(Description description) {
            description.appendText("has a reflexive equals(Object) method");
        }
    }

    private static class HasSymmetricEquals extends BaseMatcher<Object> {

        private final Object other;

        public HasSymmetricEquals(Object other) {
            this.other = other;
        }

        public boolean matches(Object o) {
            return o == null ? other == null : (o.equals(other) ? other.equals(o) : !other.equals(o));
        }

        public void describeTo(Description description) {
            description.appendText("has a symmetric equals(Object) method with ");
            description.appendValue(other);
        }
    }

    private static class HasConsistentEquals extends BaseMatcher<Object> {

        private final Object other;

        public HasConsistentEquals(Object other) {
            this.other = other;
        }

        public boolean matches(Object o) {
            return o == null ? other == null : (o.equals(other) ? o.equals(other) : !o.equals(other));
        }

        public void describeTo(Description description) {
            description.appendText("has a symmetric equals(Object) method with ");
            description.appendValue(other);
        }
    }

    private static class HasTransitiveEquals extends BaseMatcher<Object> {

        private final Object a;
        private final Object b;

        public HasTransitiveEquals(Object a, Object b) {
            this.a = a;
            this.b = b;
        }

        public boolean matches(Object o) {
            return o != null && (!(o.equals(a) && a.equals(b)) || o.equals(b));
        }

        public void describeTo(Description description) {
            description.appendText("has a transitive equals(Object) method with ");
            description.appendValue(a);
            description.appendText(" and ");
            description.appendValue(b);
        }
    }

    private static class HasHashCodeContract extends BaseMatcher<Object> {

        private final Object other;

        public HasHashCodeContract(Object other) {
            this.other = other;
        }

        public boolean matches(Object o) {
            return o == null ? other == null : (!o.equals(other) || o.hashCode() == other.hashCode());
        }

        public void describeTo(Description description) {
            description.appendText("follows the hashCode contract when compared to ");
            description.appendValue(other);
        }
    }

}
