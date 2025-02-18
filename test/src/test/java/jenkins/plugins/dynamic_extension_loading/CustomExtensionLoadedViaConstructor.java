package jenkins.plugins.dynamic_extension_loading;

import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class CustomExtensionLoadedViaConstructor {
    private static final Logger LOGGER = Logger.getLogger(CustomExtensionLoadedViaConstructor.class.getName());

    public CustomExtensionLoadedViaConstructor() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomExtensionLoadedViaConstructor"));
    }
}
