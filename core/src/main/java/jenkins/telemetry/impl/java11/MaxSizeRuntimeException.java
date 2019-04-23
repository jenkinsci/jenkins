package jenkins.telemetry.impl.java11;

public class MaxSizeRuntimeException extends RuntimeException {
    public MaxSizeRuntimeException() {
        super();
    }

    public MaxSizeRuntimeException(String message) {
        super(message);
    }

    public MaxSizeRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaxSizeRuntimeException(Throwable cause) {
        super(cause);
    }

    protected MaxSizeRuntimeException(String message, Throwable cause,
                               boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
