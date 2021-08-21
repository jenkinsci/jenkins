package jenkins.model.labels;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility class for taking the current input value and computing a list of potential terms to match against the
 * list of defined labels.
 */
@Restricted(NoExternalUse.class)
public class LabelAutoCompleteSeeder {

    private final String source;

    /**
     * Creates a new auto-complete seeder for labels.
     *
     * @param source The (partial) label expression to use as the source..
     */
    public LabelAutoCompleteSeeder(@NonNull String source) {
        this.source = source;
    }

    /**
     * Gets a list of seeds for label auto-completion.
     *
     * @return A list of seeds for label auto-completion.
     */
    @NonNull
    public List<String> getSeeds() {
        final ArrayList<String> terms = new ArrayList<>();
        boolean trailingQuote = source.endsWith("\"");
        boolean leadingQuote = source.startsWith("\"");
        boolean trailingSpace = source.endsWith(" ");
        if (trailingQuote || (trailingSpace && !leadingQuote)) {
            terms.add("");
        } else {
            if (leadingQuote) {
                int quote = source.lastIndexOf('"');
                if (quote == 0) {
                    terms.add(source.substring(1));
                } else {
                    terms.add("");
                }
            } else {
                int space = source.lastIndexOf(' ');
                if (space > -1) {
                    terms.add(source.substring(space+1));
                } else {
                    terms.add(source);
                }
            }
        }
        return terms;
    }

}
