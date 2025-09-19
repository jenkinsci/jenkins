package jenkins.security.apitoken;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class ExpiringApiTokenAdministrativeMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return "Expiring API Tokens";
    }

    @Override
    public boolean isActivated() {
        return !getExpiringTokens().isEmpty();
    }

    public List<TokenInfo> getExpiringTokens() {
        List<TokenInfo> expiringTokens = new ArrayList<>();
        Date now = new Date();
        Date thirtyDaysFromNow = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));

        for (User user : User.getAll()) {
            ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
            if (apiTokenProperty != null) {
                apiTokenProperty.getTokenStore().getTokenListSortedByName().stream()
                        .filter(token -> token.getExpirationDate() != null &&
                                token.getExpirationDate().after(now) &&
                                token.getExpirationDate().before(thirtyDaysFromNow))
                        .forEach(token -> expiringTokens.add(new TokenInfo(user, token)));
            }
        }
        return expiringTokens;
    }

    @RequirePOST
    public void doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        // No action needed, this is just for the UI
        rsp.forwardToPreviousPage(req);
    }

    public static class TokenInfo {
        private final User user;
        private final ApiTokenStore.HashedToken token;

        public TokenInfo(User user, ApiTokenStore.HashedToken token) {
            this.user = user;
            this.token = token;
        }

        public User getUser() {
            return user;
        }

        public ApiTokenStore.HashedToken getToken() {
            return token;
        }
    }
}
