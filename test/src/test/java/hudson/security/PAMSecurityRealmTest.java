package hudson.security;

import hudson.Functions;
import hudson.security.SecurityRealm.SecurityComponents;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Arrays;

import static hudson.util.jna.GNUCLibrary.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class PAMSecurityRealmTest extends HudsonTestCase {
    public void testLoadUsers() {
        if (Functions.isWindows())  return; // skip on Windows

        SecurityComponents sc = new PAMSecurityRealm("sshd").getSecurityComponents();

        try {
            sc.userDetails.loadUserByUsername("bogus-bogus-bogus");
            fail("no such user");
        } catch (UsernameNotFoundException e) {
            // expected
        }

        String name = LIBC.getpwuid(LIBC.geteuid()).pw_name;

        System.out.println(Arrays.asList(sc.userDetails.loadUserByUsername(name).getAuthorities()));
    }
}
