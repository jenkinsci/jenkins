package hudson.search;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Locale;
import java.util.regex.Pattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public final class SearchMatcher {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");

    private final boolean caseInsensitive;

    private final String token;

    private SearchMatcher(@NonNull String token) {
        this.caseInsensitive = UserSearchProperty.isCaseInsensitive();
        this.token = normalize(token);
    }

    @NonNull
    public static SearchMatcher of(@NonNull String token) {
        return new SearchMatcher(token);
    }

    public boolean matches(@CheckForNull String value) {
        return value != null && normalize(value).contains(token);
    }

    private String normalize(String value) {
        String collapsed = SEPARATORS.matcher(value).replaceAll(" ").trim();
        return caseInsensitive ? collapsed.toLowerCase(Locale.ROOT) : collapsed;
    }
}
