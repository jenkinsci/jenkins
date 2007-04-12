package hudson.api;

import java.io.IOException;

/**
 * Receives the event callback on the model data to be exposed.
 *
 * <p>
 * The call sequence is:
 *
 * <pre>
 * EVENTS := startObject PROPERTY* endObject
 * PROPERTY := name VALUE
 * VALUE := valuePrimitive
 *        | value
 *        | valueNull
 *        | startArray VALUE* endArray
 *        | EVENTS
 * </pre>
 * 
 * @author Kohsuke Kawaguchi
 */
public interface DataWriter {
    void name(String name) throws IOException;

    void valuePrimitive(Object v) throws IOException;
    void value(String v) throws IOException;
    void valueNull() throws IOException;

    void startArray() throws IOException;
    void endArray() throws IOException;

    void startObject() throws IOException;
    void endObject() throws IOException;
}
