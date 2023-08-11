package antlr;

/**
 * This class is for binary compatibility for older plugins that import {@link ANTLRException}.
 *
 * @deprecated use {@link IllegalArgumentException}
 */
@Deprecated
public class ANTLRException extends IllegalArgumentException {
    public ANTLRException(String message) {
        super(message);
    }

    public ANTLRException(String message, Throwable cause) {
        super(message, cause);
    }

    public ANTLRException(Throwable cause) {
        super(cause);
    }
}
