package jenkins.data;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
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
    public DataModel find(Class c) {
        // if a custom data model is defined for the given type, go for it
        for (DataModel dm : dataModels) {
            if (dm.getType().equals(c))
                return dm;
        }

        // Describables have databinding annotatoins, so this should work
        // TODO: see the notes about how the type should declare its intent to participate in Data API
        // we don't know if Describable or not is the right thing to test
        if (Describable.class.isAssignableFrom(c)) {
            return DataModel.byReflection(c);
        }

        return null;
    }
}
