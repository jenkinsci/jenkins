package hudson.util;

/**
 * @author Kohsuke Kawaguchi
 */
public class SecretHelper {
    public static void set(String s) {
        Secret.SECRET = s;
    }
}
