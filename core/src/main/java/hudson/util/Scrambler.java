package hudson.util;

import com.trilead.ssh2.crypto.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Used when storing passwords in configuration files.
 *
 * <p>
 * This doesn't make passwords secure, but it prevents unwanted
 * exposure to passwords, such as when one is grepping the file system
 * or looking at config files for trouble-shooting.
 *
 * @author Kohsuke Kawaguchi
 * @see Protector
 */
public class Scrambler {
    public static String scramble(String secret) {
        if(secret==null)    return null;
        try {
            return new String(Base64.encode(secret.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    public static String descramble(String scrambled) {
        if(scrambled==null)    return null;
        try {
            return new String(Base64.decode(scrambled.toCharArray()),"UTF-8");
        } catch (IOException e) {
            return "";  // corrupted data.
        }
    }
}
