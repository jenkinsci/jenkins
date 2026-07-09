package jenkins.security;

import hudson.Extension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class MapEntryClassFilter implements CustomClassFilter {

    @Override
    public Boolean permits(Class<?> c) {
        return permits(c.getName());
    }

    @Override
    public Boolean permits(String name) {
        if ("java.util.Map$Entry".equals(name)) {
            return Boolean.TRUE;
        }
        return null;
    }
}
