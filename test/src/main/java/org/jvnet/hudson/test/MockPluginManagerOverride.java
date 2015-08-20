package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.PluginManagerStaplerOverride;

/** Test the PluginManagerStapler override works correctly by adding a trivial custom Jelly view */
@Extension
public class MockPluginManagerOverride extends PluginManagerStaplerOverride {
}
