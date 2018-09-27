package jenkins.data;

import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * A Registry to allow {@link DataModel}s retrieval.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface DataModelRegistry {
    /**
     * Retrieve a {@link DataModel} for target type.
     * @param type
     * @return <code>null</code> if we don't know any {@link DataModel} for requested type
     */
    @CheckForNull
    <T> DataModel<T> lookup(Type type);

    /**
     * null-safe flavour of {@link #lookup(Type)}.
     * @param type
     * @throws IOException if we don't know any {@link DataModel} for requested type
     */
    @Nonnull
    <T> DataModel<T> lookupOrFail(Type type) throws IOException;

    /**
     * Retrieve default implementation from Jenkins
     */
    static DataModelRegistry get() {
        return Jenkins.getInstance().getExtensionList(DataModelRegistry.class).get(0);
    }
}

