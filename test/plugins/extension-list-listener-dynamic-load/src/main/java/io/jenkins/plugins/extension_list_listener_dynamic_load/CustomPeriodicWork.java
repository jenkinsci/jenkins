package io.jenkins.plugins.extension_list_listener_dynamic_load;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.PeriodicWork;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class CustomPeriodicWork extends PeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(CustomPeriodicWork.class.getName());

    public CustomPeriodicWork() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomPeriodicWork"));
    }

    @Override
    protected void doRun() {}

    @Override
    public long getRecurrencePeriod() {
        LOGGER.log(Level.INFO, null, new Exception("Loading CustomExtension"));
        return ExtensionList.lookupSingleton(CustomExtension.class).recurrencePeriod;
    }
}
