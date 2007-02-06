package hudson.tasks.junit;

/**
 * Used to signal an orderly abort of the processing.
 */
class AbortException extends RuntimeException {
    public AbortException(String msg) {
        super(msg);
    }

    private static final long serialVersionUID = 1L;
}
