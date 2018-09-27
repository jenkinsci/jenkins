package jenkins.data;

/**
 * @author Kohsuke Kawaguchi
 */
public class ReadDataContext extends DataContext {
    public ReadDataContext(DataModelRegistry registry) {
        super(registry);
    }

    public Class expectedType() {
        // TODO
        throw new UnsupportedOperationException();
    }
}

