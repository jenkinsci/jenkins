package jenkins.plugins.dynamic_extension_loading;

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
        ExtensionList.lookupSingleton(CustomExtensionLoadedViaConstructor.class);
    }

    @Override
    protected void doRun() {}

    @Override
    public long getRecurrencePeriod() {
        LOGGER.log(Level.INFO, null, new Exception("Loading CustomExtensionLoadedViaListener"));
        return ExtensionList.lookupSingleton(CustomExtensionLoadedViaListener.class).recurrencePeriod;
    }
}
