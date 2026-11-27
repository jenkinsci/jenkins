package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Translations go through {@link MessageFormat}, where a lone apostrophe starts a quoted run:
 * the apostrophe disappears, and a {@code {0}} inside the run is shown as is instead of its value.
 */
class TranslationFormatTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern TRANSLATION = Pattern.compile("(.+)_[a-z]{2,3}(_[A-Za-z]+)?\\.properties");

    @Test
    void translationsKeepTheirApostrophesAndUseOnlyTheArgumentsTheyAreGiven() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(TranslationFormatTest::isFormattedTranslation).sorted().toList()) {
                Properties english = load(englishOf(file));
                Properties translated = load(file);
                boolean view = !file.getFileName().toString().startsWith("Messages_");
                for (String key : translated.stringPropertyNames()) {
                    String original = english.getProperty(key);
                    // A view's English text is often the key itself, as in ${%Jenkins Log}.
                    String text = original != null ? original : view ? key : null;
                    if (text == null) {
                        continue;
                    }
                    String value = translated.getProperty(key);
                    String where = RESOURCES.relativize(file) + ": " + key;
                    if (hasLoneApostrophe(value) && !hasLoneApostrophe(text)) {
                        problems.add(where + " has a lone apostrophe, which is dropped; write '' instead");
                    }
                    // A key alone can't say how many arguments the page passes, so this needs the English entry.
                    if (original != null && arguments(value) > arguments(original)) {
                        problems.add(where + " uses an argument the English text is never given");
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void aTranslatedMessageShowsItsArgumentAndItsApostrophe() {
        assertEquals("O agente chamado 'build-01' já existe",
                hudson.model.Messages._ComputerSet_SlaveAlreadyExists("build-01").toString(Locale.forLanguageTag("pt-BR")));
        assertEquals("Il valore dev'essere maggiore o uguale a 5",
                hudson.model.Messages._Hudson_MustBeAtLeast(5).toString(Locale.ITALIAN));
    }

    /** Localizer bundles and the text of Jelly or Groovy views; both are formatted with MessageFormat. */
    private static boolean isFormattedTranslation(Path file) {
        Matcher m = TRANSLATION.matcher(file.getFileName().toString());
        if (!m.matches()) {
            return false;
        }
        String stem = m.group(1);
        return stem.equals("Messages")
                || Files.exists(file.resolveSibling(stem + ".jelly"))
                || Files.exists(file.resolveSibling(stem + ".groovy"));
    }

    private static Path englishOf(Path file) {
        Matcher m = TRANSLATION.matcher(file.getFileName().toString());
        return m.matches() ? file.resolveSibling(m.group(1) + ".properties") : file;
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(file)) {
            return properties;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    /** A ' that isn't doubled and isn't part of the '{' or '}' escape. */
    private static boolean hasLoneApostrophe(String value) {
        return value.replace("''", "").replace("'{'", "").replace("'}'", "").contains("'");
    }

    /** How many arguments the pattern reads, or 0 when it isn't a valid pattern. */
    private static int arguments(String value) {
        try {
            return new MessageFormat(value).getFormatsByArgumentIndex().length;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
