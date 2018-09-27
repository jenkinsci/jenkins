package jenkins.data;

import hudson.ExtensionPoint;

/**
 * Extension point to discover {@link DataModel}s
 *
 * @author Kohsuke Kawaguchi
 */
public interface DataModelFactory extends ExtensionPoint {
    DataModel find(Class type);
}
