package jenkins.util.antlr;

import antlr.ANTLRException;
import java.util.function.Supplier;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class JenkinsANTLRErrorListener extends BaseErrorListener {

    private final Supplier<String> errorMessageSupplier;

    public JenkinsANTLRErrorListener() {
        errorMessageSupplier = () -> null;
    }

    public JenkinsANTLRErrorListener(Supplier<String> errorMessageSupplier) {
        this.errorMessageSupplier = errorMessageSupplier;
    }

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {
        String errorMessage = errorMessageSupplier.get();
        if (errorMessage != null) {
            msg = errorMessage;
        }
        throw new ANTLRException(formatMessage(line, charPositionInLine, msg), e);
    }

    private static String formatMessage(int line, int column, String message) {
        StringBuilder sb = new StringBuilder();
        if (line != -1) {
            sb.append("line ");
            sb.append(line);
            if (column != -1) {
                sb.append(":");
                sb.append(column);
            }
            sb.append(": ");
        }
        sb.append(message);
        return sb.toString();
    }
}
