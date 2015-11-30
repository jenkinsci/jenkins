package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.Collection;


public abstract class TopLevelItemLoader implements ExtensionPoint {
     public abstract Collection<TopLevelItem> load(Jenkins jenkins) throws IOException;
}
