package jenkins.data;

import jenkins.data.model.CNode;

import java.io.Reader;
import java.io.Writer;

/**
 * @author Kohsuke Kawaguchi
 */
public class JsonSerializer extends Serializer {
    @Override
    protected CNode unstring(Reader in) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    protected void stringify(CNode tree, Writer out) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
