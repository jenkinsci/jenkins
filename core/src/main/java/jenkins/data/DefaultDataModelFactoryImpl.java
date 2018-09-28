package jenkins.data;

import hudson.Extension;
import hudson.ExtensionList;
import org.jvnet.tiger_types.Types;

import java.lang.reflect.Type;

/**
 * Looks up {@link DataModel} directly from extensions.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100)
public class DefaultDataModelFactoryImpl implements DataModelFactory {
    private final ExtensionList<DataModel> dataModels = ExtensionList.lookup(DataModel.class);

    @Override
    public DataModel find(Type type) {
        Class<Object> c = Types.erasure(type);
        for (DataModel dm : dataModels) {
            if (dm.getType().equals(c))
                return dm;
        }
        return null;
    }
}
