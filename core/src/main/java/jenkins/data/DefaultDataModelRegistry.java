package jenkins.data;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.lang.Klass;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link DataModelRegistry}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class)
public class DefaultDataModelRegistry implements DataModelRegistry {
    private final ExtensionList<DataModelFactory> factories = ExtensionList.lookup(DataModelFactory.class);

    private LoadingCache<Class, DataModel> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Class, DataModel>() {
                @Override
                public DataModel load(Class c) throws IOException {
                    for (DataModelFactory f : factories) {
                        DataModel m = f.find(c);
                        if (m!=null)
                            return m;
                    }
                    throw new IOException("no DataModel found for "+c);
                }
            });

    @CheckForNull
    @Override
    public <T> DataModel<T> lookup(Class<T> type) {
        try {
            return cache.get(type);
        } catch (ExecutionException e) {
            return null;
        }
    }

    @Override
    public <T extends Describable> Set findSubtypes(Class<T> superType) {
        // Jenkins.getDescriptorList does not work well since it is limited to descriptors declaring one supertype, and does not work at all for SimpleBuildStep.
        return ExtensionList.lookup(Descriptor.class).stream()
            .map(Descriptor::getKlass)
            .map(Klass::toJavaClass)
            .filter(superType::isAssignableFrom)
            .collect(Collectors.toSet());
    }
}
