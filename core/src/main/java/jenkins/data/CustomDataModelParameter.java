package jenkins.data;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * {@link DataModelParameter} that represents a parameter that {@link CustomDataModel} projects.
 *
 * @author Kohsuke Kawaguchi
 */
final class CustomDataModelParameter extends AbstractDataModelParameter {
    CustomDataModelParameter(String name, Type rawType) {
        super(name, rawType);
    }

    @Override
    public boolean isRequired() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDeprecated() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @CheckForNull
    @Override
    public String getHelp() throws IOException {
        // TODO
        throw new UnsupportedOperationException();
    }
}
