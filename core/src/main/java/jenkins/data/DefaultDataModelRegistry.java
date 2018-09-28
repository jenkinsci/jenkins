package jenkins.data;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Default implementation of {@link DataModelRegistry}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class)
public class DefaultDataModelRegistry implements DataModelRegistry {
    private final ExtensionList<DataModelFactory> factories = ExtensionList.lookup(DataModelFactory.class);

    private LoadingCache<Type, DataModel> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Type, DataModel>() {
                @Override
                public DataModel load(Type type) throws IOException {
                    Class<Object> c = Types.erasure(type);
                    for (DataModelFactory f : factories) {
                        DataModel m = f.find(c);
                        if (m!=null)
                            return m;
                    }
                    throw new IOException("no DataModel found for "+type);
                }
            });

    @CheckForNull
    @Override
    public <T> DataModel<T> lookup(Type type) {
        try {
            return cache.get(type);
        } catch (ExecutionException e) {
            return null;
        }
    }

    @Override
    public Set<Class<?>> findSubtypes(Class<?> superType) {
        Set<Class<?>> clazzes = new HashSet<>();
        // Jenkins.getDescriptorList does not work well since it is limited to descriptors declaring one supertype, and does not work at all for SimpleBuildStep.
        for (Descriptor<?> d : ExtensionList.lookup(Descriptor.class)) {
            if (superType.isAssignableFrom(d.clazz)) {
                clazzes.add(d.clazz);
            }
        }
        return clazzes;
    }
}
