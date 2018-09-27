package jenkins.data;

/**
 * @author Kohsuke Kawaguchi
 */
public class WriteDataContext extends DataContext {
    public WriteDataContext(ModelBinderRegistry registry) {
        super(registry);
    }
}
