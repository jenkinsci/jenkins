package hudson.util.jna;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * .NET related code.
 * 
 * @author Kohsuke Kawaguchi
 */
public class DotNet {
    /**
     * Returns true if the .NET framework of the given version (or greater) is installed.
     */
    public static boolean isInstalled(int major, int minor) {
        // see http://support.microsoft.com/?scid=kb;en-us;315291 for the basic algorithm
        // observation in my registry shows that the actual key name can be things like "v2.0 SP1"
        // or "v2.0.50727", so the regexp is written to accomodate this.
        RegistryKey key = RegistryKey.LOCAL_MACHINE.openReadonly("SOFTWARE\\Microsoft\\.NETFramework");
        for( String keyName : key.getSubKeys() ) {
            Matcher m = VERSION_PATTERN.matcher(keyName);
            if(m.matches()) {
                int mj = Integer.parseInt(m.group(1));
                if(mj>=major) {
                    int mn = Integer.parseInt(m.group(2));
                    if(mn>=minor)
                        return true;
                }
            }
        }
        return false;
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+).*");
}
