package jenkins.plugins.dynamic_extension_loading;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension
public class CustomExtensionLoadedViaConstructor {
    private static final Logger LOGGER = Logger.getLogger(CustomExtensionLoadedViaConstructor.class.getName());

    public CustomExtensionLoadedViaConstructor() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomExtensionLoadedViaConstructor"));
    }
}
