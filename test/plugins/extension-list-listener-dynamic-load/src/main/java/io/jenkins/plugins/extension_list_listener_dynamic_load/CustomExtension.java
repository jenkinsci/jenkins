package io.jenkins.plugins.extension_list_listener_dynamic_load;

import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class CustomExtension {
    private static final Logger LOGGER = Logger.getLogger(CustomExtension.class.getName());

    public long recurrencePeriod = 120;

    public CustomExtension() {
        LOGGER.log(Level.INFO, null, new Exception("Instantiating CustomExtension"));
    }
}
