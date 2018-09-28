package jenkins.data.tree;

import java.io.Serializable;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */

public class Source implements Serializable {

    static final long serialVersionUID = 1L;

    public final String file;
    public final int line;

    public Source(String file, int line) {
        this.file = file;
        this.line = line;
    }
}
