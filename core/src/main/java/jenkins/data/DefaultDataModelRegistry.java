package jenkins.data;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import hudson.Extension;
import hudson.ExtensionList;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;
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

                    for (DataModelFactory f : factories) {
                        DataModel m = f.find(type);
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
}
