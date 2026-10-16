package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.QuotedStringTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SSHCLITest {

    @Test
    void controlCharactersSurviveServerTokenization() {
        String[] args = {
            "set-build-description", "job", "1",
            "a\nb", "a\r\nb", "a\tb", "a\fb", "a\bb",
            "C:\\new\\path", "trailing\\", "say \"hi\"", "it's", "",
        };
        StringBuilder command = new StringBuilder();
        for (String arg : args) {
            command.append(SSHCLI.quoteArgument(arg)).append(' ');
        }
        assertArrayEquals(args, QuotedStringTokenizer.tokenize(command.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "with space", "C:\\new\\path", "trailing\\", "say \"hi\"", "it's", "\\\"", "ünïcödé"})
    void sameAsQuoteWithoutControlCharacters(String arg) {
        assertEquals(QuotedStringTokenizer.quote(arg), SSHCLI.quoteArgument(arg));
    }
}
