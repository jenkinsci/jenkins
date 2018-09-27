package jenkins.data;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@link DataModel} whose serilization code is hand-written.
 *
 * Subtype is responsible for defining schema by creating a set of {@link CustomDataModelParameter}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CustomDataModel<T> extends DataModel<T> {
    private final Class<T> type;
    private final List<DataModelParameter> parameters;

    public CustomDataModel(Class<T> type, DataModelParameter... parameters) {
        this.type = type;
        this.parameters = Arrays.asList(parameters);
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Collection<DataModelParameter> getParameters() {
        return parameters;
    }

    @CheckForNull
    @Override
    public String getHelp() throws IOException {
        // TODO
        return null;
    }

    protected static DataModelParameter parameter(String name, Type type) {
        return new CustomDataModelParameter(name,type);
    }
}
