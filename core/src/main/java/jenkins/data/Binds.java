package jenkins.data;

/**
 * @author Kohsuke Kawaguchi
 */
public @interface Binds {
    /**
     * What class this binds?
     */
    Class value();
}
