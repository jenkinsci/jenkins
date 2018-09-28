package jenkins.data.tree;

import jenkins.data.ReadException;

import java.io.IOException;

/**
 * A node in YAML/JSON/etc tree.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface TreeNode extends Cloneable {

    enum Type { MAPPING, SEQUENCE, SCALAR }

    Type getType();

    default Mapping asMapping() throws IOException {
        throw new ReadException("Item isn't a Mapping").withSource(getSource());
    }

    default Sequence asSequence() throws IOException {
        throw new ReadException("Item isn't a Sequence").withSource(getSource());
    }

    default Scalar asScalar() throws IOException {
        throw new ReadException("Item isn't a Scalar").withSource(getSource());
    }

    /**
     * Indicate the source (file, line number) this specific configuration node comes from.
     * This is used to offer relevant diagnostic messages
     */
    Source getSource();

    TreeNode clone();
}
