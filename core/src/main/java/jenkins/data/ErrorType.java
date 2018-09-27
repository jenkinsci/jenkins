package jenkins.data;

import java.lang.reflect.Type;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jesse Glick
 * @author Anderw Bayer
 */
public final class ErrorType extends ParameterType {
    private final Exception error;

    ErrorType(Exception error, Type type) {
        super(type);
        LOGGER.log(Level.FINE, null, error);
        this.error = error;
    }

    public Exception getError() {
        return error;
    }

    @Override
    void toString(StringBuilder b, Stack<Class<?>> modelTypes) {
        b.append(error);
    }

    private static final Logger LOGGER = Logger.getLogger(ErrorType.class.getName());
}
