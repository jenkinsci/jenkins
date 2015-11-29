package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.Collection;


public interface TopLevelItemLoader extends ExtensionPoint {
     Collection<TopLevelItem> load(Jenkins jenkins) throws IOException;
}
