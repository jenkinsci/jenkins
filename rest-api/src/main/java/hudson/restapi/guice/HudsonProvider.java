package hudson.restapi.guice;

import hudson.model.Hudson;
import com.google.inject.Provider;

public class HudsonProvider implements Provider<Hudson> {
    public Hudson get() {
	    return Hudson.getInstance();
    }
}
