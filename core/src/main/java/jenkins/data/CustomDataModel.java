package jenkins.data;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class CustomDataModel<T> implements DataModel<T> {
    private final Class<T> type;

    public CustomDataModel(Class<T> type) {
        this.type = type;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Collection<? extends DataModelParameter> getParameters() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @CheckForNull
    @Override
    public String getHelp() throws IOException {
        // TODO
        return null;
    }
}
