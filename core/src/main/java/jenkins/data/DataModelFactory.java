package jenkins.data;

import hudson.ExtensionPoint;

import java.lang.reflect.Type;

/**
 * Extension point to discover {@link DataModel}s
 *
 * @author Kohsuke Kawaguchi
 */
public interface DataModelFactory extends ExtensionPoint {
    DataModel find(Type type);
}
