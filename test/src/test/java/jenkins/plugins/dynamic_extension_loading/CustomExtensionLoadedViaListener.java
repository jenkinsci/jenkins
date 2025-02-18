package jenkins.plugins.dynamic_extension_loading;

import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class CustomExtensionLoadedViaListener {
    private static final Logger LOGGER = Logger.getLogger(CustomExtensionLoadedViaListener.class.getName());

    public long recurrencePeriod = 120;

    public CustomExtensionLoadedViaListener() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomExtensionLoadedViaListener"));
    }
}
