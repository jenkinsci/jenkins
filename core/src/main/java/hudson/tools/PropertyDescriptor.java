package hudson.tools;

import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Base {@link Descriptor} type used for {@code XyzProperty} classes.
 *
 * @param <P>
 *      Type of the {@code XyzProperty}. Called 'property type'
 * @param <T>
 *      Type of the {@code Xyz}, that the property attaches to. Called 'target type'
 * @author Kohsuke Kawaguchi
 * @since 1.305
 */
public abstract class PropertyDescriptor<P extends Describable<P>,T> extends Descriptor<P> {
    protected PropertyDescriptor(Class<? extends P> clazz) {
        super(clazz);
    }

    protected PropertyDescriptor() {
    }

    /**
     * Infer the type parameterization 'P'
     */
    private Class<P> getP() {
        return Functions.getTypeParameter(getClass(),Descriptor.class,0);
    }

    /**
     * Returns true if this property type is applicable to the
     * given target type.
     *
     * <p>
     * The default implementation of this method checks if the given node type is assignable
     * according to the parameterization, but subtypes can extend this to change this behavior.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of the target, for example.
     */
    public boolean isApplicable(Class<? extends T> targetType) {
        Class<? extends T> applicable = Functions.getTypeParameter(clazz,getP(),0);
        return applicable.isAssignableFrom(targetType);
    }

    public static <D extends PropertyDescriptor<?,T>,T> List<D> for_(List<D> all, Class<? extends T> target) {
        List<D> result = new ArrayList<D>();
        for (D d : all)
            if (d.isApplicable(target))
                result.add(d);
        return result;
    }

    public static <D extends PropertyDescriptor<?,T>,T> List<D> for_(List<D> all, T target) {
        return for_(all,(Class)target.getClass());
    }
}
