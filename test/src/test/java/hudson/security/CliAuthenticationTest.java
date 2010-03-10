package hudson.security;

import hudson.cli.CLICommand;
import hudson.cli.CliManagerImpl;
import hudson.model.Hudson;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.springframework.dao.DataAccessException;
import junit.framework.Assert;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author Kohsuke Kawaguchi
 */
public class CliAuthenticationTest extends HudsonTestCase {
    public void test1() throws Exception {
        // dummy security realm that authenticates when username==password
        hudson.setSecurityRealm(new AbstractPasswordBasedSecurityRealm() {
            @Override
            protected UserDetails authenticate(String username, String password) throws AuthenticationException {
                if (username.equals(password))
                    return new User(username,password,true,true,true,true,new GrantedAuthority[]{AUTHENTICATED_AUTHORITY});
                throw new BadCredentialsException(username);
            }

            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                throw new UsernameNotFoundException(username);
            }

            @Override
            public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
                throw new UsernameNotFoundException(groupname);
            }
        });

        assertEquals(0,new CliManagerImpl(null).main(Arrays.asList("test","--username","abc","--password","abc"),
                Locale.ENGLISH, System.in, System.out, System.err));
    }

    @TestExtension
    public static class TestCommand extends CLICommand {
        @Override
        public String getShortDescription() {
            return "test command";
        }

        @Override
        protected int run() throws Exception {
            Authentication auth = Hudson.getInstance().getAuthentication();
            Assert.assertNotSame(Hudson.ANONYMOUS,auth);
            Assert.assertEquals("abc", auth.getName());
            return 0;
        }
    }

}
