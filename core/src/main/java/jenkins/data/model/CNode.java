package jenkins.data.model;

import jenkins.data.ReadException;

import java.io.IOException;

/**
 * A configuration Node in yaml tree.
 * (We didn't used <em>Node</em> as class name to avoid collision with commonly used Jenkins class hudson.model.Node
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */

public interface CNode extends Cloneable {

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

    CNode clone();
}
