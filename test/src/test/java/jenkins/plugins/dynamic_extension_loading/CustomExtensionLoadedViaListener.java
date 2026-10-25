package jenkins.plugins.dynamic_extension_loading;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension
public class CustomExtensionLoadedViaListener {
    private static final Logger LOGGER = Logger.getLogger(CustomExtensionLoadedViaListener.class.getName());

    public long recurrencePeriod = 120;

    public CustomExtensionLoadedViaListener() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomExtensionLoadedViaListener"));
    }
}
