package hudson.security;

import org.acegisecurity.ui.logout.LogoutHandler;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.Authentication;
import org.apache.commons.io.FileUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Random;

import hudson.util.Scrambler;
import ch.ethz.ssh2.crypto.Base64;

/**
 * @author Kohsuke Kawaguchi
 */
public class MutatingRememberMeServices implements RememberMeServices, LogoutHandler {
    /**
     * Cookie name.
     */
    private static final String COOKIE_NAME = TokenBasedRememberMeServices.ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY;

    /**
     * The directory to store remember-me cookies.
     */
    private final File store;

    private final Random random;

    public MutatingRememberMeServices(File store) {
        this.store = store;
        this.random = new SecureRandom();
    }

    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
    }

    public void loginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        RememberMeCookie old = getCookie(request);
        if(old!=null)   old.invalidate();

        RememberMeCookie n = new RememberMeCookie(auth.getName());
        
    }

    private RememberMeCookie getCookie(HttpServletRequest req) throws IOException {
        for(Cookie c : req.getCookies()) {
            if(c.getName().equals(COOKIE_NAME)) {
                // 256bit base64 encoded => 44bytes
                if(c.getValue().length()>45) {
                    return new RememberMeCookie(c.getValue().substring(45),
                            Base64.decode(c.getValue().substring(0,44).toCharArray()));
                }
            }
        }

        // no cookie found, or cookie was syntactically invalid
        return null;
    }

    private final class RememberMeCookie {
        private final String userName;
        /**
         * 256bit random number.
         */
        private final byte[] randomNumber;

        private RememberMeCookie(String userName) throws IOException {
            this.userName = userName;
            this.randomNumber = new byte[32];
            random.nextBytes(randomNumber);

            // activates this remember me cookie by recording this association
            FileUtils.writeStringToFile(getKeyFile(),userName,"UTF-8");
        }

        private RememberMeCookie(String userName, byte[] randomNumber) {
            this.userName = userName;
            this.randomNumber = randomNumber;
        }

        /**
         * The place to store the cookie on the server side for verification.
         */
        private File getKeyFile() {
            return new File(store,new String(Base64.encode(randomNumber)));
        }

        /**
         * Prohibit future log in by using this remember-me cookie. 
         */
        private void invalidate() {
            getKeyFile().delete();
        }

        private Cookie makeCookie() {
            Cookie cookie = new Cookie(COOKIE_NAME, );
            cookie.setMaxAge(new Long(maxAge).intValue());
            cookie.setPath(StringUtils.hasLength(request.getContextPath()) ? request.getContextPath() : "/");
        }
    }
}
