package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Mistakes in translations that look fine in the file but not on the page.
 */
class TranslationTextTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern TRANSLATION = Pattern.compile(".+_([a-z]{2,3})(_[A-Za-z]+)?\\.properties");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Pattern TAG = Pattern.compile("</?([^\\s<>/=]+)([^<>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("\\s([^\\s<>=\"']+)=");
    private static final Map<String, UnicodeScript> SCRIPTS = Map.of(
            "bg", UnicodeScript.CYRILLIC,
            "ru", UnicodeScript.CYRILLIC,
            "sr", UnicodeScript.CYRILLIC,
            "uk", UnicodeScript.CYRILLIC,
            "el", UnicodeScript.GREEK);

    /**
     * Some Latin letters look just like Cyrillic or Greek ones ({@code a} and {@code а}, {@code o} and {@code о}).
     * A word that mixes them reads fine, but search, spell checkers and screen readers see a different word.
     */
    @Test
    void translatedWordsDoNotMixLatinLetters() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : translations()) {
            UnicodeScript script = SCRIPTS.get(language(file));
            if (script == null) {
                continue;
            }
            Properties translated = load(file);
            for (String key : translated.stringPropertyNames()) {
                Matcher word = WORD.matcher(translated.getProperty(key));
                while (word.find()) {
                    if (mixesLatinInto(word.group(), script)) {
                        problems.add(where(file, key) + word.group());
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /**
     * When a line ends with {@code \}, the value goes on with the next line, minus its leading spaces.
     * So {@code word\} followed by {@code   next} reads {@code wordnext}; the space has to go before the {@code \}.
     */
    @Test
    void continuedLinesDoNotGlueWordsTogether() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : translations()) {
            // a few old files are not valid UTF-8, so don't fail on them here
            List<String> lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList();
            boolean continued = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String line = lines.get(i);
                boolean comment = !continued && line.stripLeading().matches("[#!].*");
                continued = !comment && endsWithContinuation(line);
                if (!continued) {
                    continue;
                }
                String before = line.substring(0, line.length() - 1);
                String after = lines.get(i + 1).stripLeading();
                if (gluesWords(before, after)) {
                    problems.add(RESOURCES.relativize(file) + ":" + (i + 1) + ": "
                            + tail(before) + "\\ + " + after.substring(0, Math.min(after.length(), 20)));
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /** A translated {@code <а href>} with a Cyrillic {@code а} is not a link any more. */
    @Test
    void htmlTagsStayInAscii() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : translations()) {
            Properties translated = load(file);
            for (String key : translated.stringPropertyNames()) {
                Matcher tag = TAG.matcher(translated.getProperty(key));
                while (tag.find()) {
                    List<String> names = new ArrayList<>(List.of(tag.group(1)));
                    Matcher attribute = ATTRIBUTE.matcher(tag.group(2));
                    while (attribute.find()) {
                        names.add(attribute.group(1));
                    }
                    if (names.stream().anyMatch(name -> !name.matches("\\p{ASCII}+"))) {
                        problems.add(where(file, key) + tag.group());
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    private static List<Path> translations() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            return files.filter(file -> language(file) != null).sorted().toList();
        }
    }

    private static String language(Path file) {
        Matcher m = TRANSLATION.matcher(file.getFileName().toString());
        return m.matches() ? m.group(1) : null;
    }

    private static String where(Path file, String key) {
        return RESOURCES.relativize(file) + ": " + key + ": ";
    }

    private static boolean mixesLatinInto(String word, UnicodeScript script) {
        boolean latin = false;
        boolean other = false;
        for (int i = 0; i < word.length(); ) {
            int c = word.codePointAt(i);
            UnicodeScript s = UnicodeScript.of(c);
            latin |= s == UnicodeScript.LATIN;
            other |= s == script;
            i += Character.charCount(c);
        }
        return latin && other;
    }

    /** An odd number of trailing backslashes; {@code \\} is an escaped backslash. */
    private static boolean endsWithContinuation(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(line.length() - 1 - n) == '\\') {
            n++;
        }
        return n % 2 == 1;
    }

    private static boolean gluesWords(String before, String after) {
        if (before.isEmpty() || after.isEmpty() || !Character.isLetterOrDigit(after.codePointAt(0))) {
            return false;
        }
        int last = before.codePointBefore(before.length());
        if (!Character.isLetterOrDigit(last) && ".,;:!?)]»\"'%".indexOf(last) < 0) {
            return false;
        }
        // an escape such as \n ends the line on purpose
        if (before.matches("(?s).*(?<!\\\\)(\\\\\\\\)*\\\\[a-z]")) {
            return false;
        }
        // Chinese, Japanese and Korean text does not put spaces between words
        return !isCjk(last) && !isCjk(after.codePointAt(0));
    }

    private static boolean isCjk(int c) {
        UnicodeScript s = UnicodeScript.of(c);
        return s == UnicodeScript.HAN || s == UnicodeScript.HIRAGANA || s == UnicodeScript.KATAKANA
                || s == UnicodeScript.HANGUL || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }

    private static String tail(String s) {
        return s.substring(Math.max(0, s.length() - 20));
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
