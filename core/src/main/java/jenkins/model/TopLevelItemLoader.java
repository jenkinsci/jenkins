package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.Collection;

/**
 * Extension point to contribute @TopLevelItem's to Jenkins.
 * @see DiskItemLoader
 */

public abstract class TopLevelItemLoader implements ExtensionPoint {
     public abstract Collection<TopLevelItem> load(Jenkins jenkins) throws IOException;
}
