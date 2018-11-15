package jenkins.data.exportable;

import hudson.Extension;
import jenkins.data.CustomDataModel;
import jenkins.data.DataContext;
import jenkins.data.DataModel;
import jenkins.data.DataModelFactory;
import jenkins.data.DataModelParameter;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.TreeNode;
import org.jvnet.tiger_types.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;


/**
 * This would be a part of the system, not a part of the user-written code.
 * It's a bit of sugar
 */
@Extension
public class APIExportableModelFactory implements DataModelFactory {

    @Override
    public DataModel find(final Class clazz) {
        if (APIExportable.class.isAssignableFrom(clazz)) {
            return new TranslatedModel(clazz);
        }
        return null;
    }

    private static class TranslatedModel<T extends APIExportable<U>,U extends APIResource> extends CustomDataModel<T> {
        private final Class<U> u;
        private final DataModel<U> um;

        public TranslatedModel(Class<T> type) {
            super(type);
            Type t = Types.getBaseClass(type, APIExportable.class);
            Type u = Types.getTypeArgument(t, 0);
            this.u = Types.erasure(u);
            um = DataModel.byReflection(this.u);
        }

        @Override
        public Collection<DataModelParameter> getParameters() {
            return um.getParameters();
        }

        @Override
        public TreeNode write(T object, DataContext context) {
            U r = object.toResource();
            return um.write(r, context);
        }

        @Override
        public T read(TreeNode input, DataContext context) {
            return (T)um.read(input, context).toModel();
        }
    }
}
