package jenkins.data;

import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Type;

/**
 * A Registry to allow {@link ModelBinder}s retrieval.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */

public interface ModelBinderRegistry {

    /**
     * Retrieve a {@link RootElementModelBinder} by it's yaml element (key) name.
     * @param name
     * @return <code>null</code> if we don't know any {@link RootElementModelBinder} for requested name
     */
    @CheckForNull
    RootElementModelBinder lookupRootElement(String name);

    /**
     * Retrieve a {@link ModelBinder} for target type.
     * @param type
     * @return <code>null</code> if we don't know any {@link RootElementModelBinder} for requested type
     */
    @CheckForNull
    <T> ModelBinder<T> lookup(Type type);

    /**
     * null-safe flavour of {@link #lookup(Type)}.
     * @param type
     * @throws ModelBinderException if we don't know any {@link RootElementModelBinder} for requested type
     */
    @Nonnull
    <T> ModelBinder<T> lookupOrFail(Type type) throws ModelBinderException;

    /**
     * Retrieve default implementation from Jenkins
     */
    static ModelBinderRegistry get() {
        return Jenkins.getInstance().getExtensionList(ModelBinderRegistry.class).get(0);
    }
}

