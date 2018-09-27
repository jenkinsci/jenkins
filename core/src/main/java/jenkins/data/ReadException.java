package jenkins.data;

import jenkins.data.model.Source;

import java.io.IOException;

/**
 * Exception when reading from Data API
 *
 * @author Kohsuke Kawaguchi
 */
public class ReadException extends IOException {
    private Source source;

    public ReadException() {
    }

    public ReadException(String message) {
        super(message);
    }

    public ReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReadException(Throwable cause) {
        super(cause);
    }

    /**
     * Where in the source did this problem happen?
     */
    public Source getSource() {
        return source;
    }

    public ReadException withSource(Source s) {
        this.source = s;
        return this;
    }
}
