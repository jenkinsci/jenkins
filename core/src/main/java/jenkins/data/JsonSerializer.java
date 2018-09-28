package jenkins.data;

import jenkins.data.tree.TreeNode;

import java.io.Reader;
import java.io.Writer;

/**
 * @author Kohsuke Kawaguchi
 */
public class JsonSerializer extends Serializer {
    @Override
    protected TreeNode unstring(Reader in) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    protected void stringify(TreeNode tree, Writer out) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
