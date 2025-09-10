/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class ArgumentListBuilderTest {

    @Test
    void assertEmptyMask() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.add("other", "arguments");

        assertFalse(builder.hasMaskedArguments(), "There should not be any masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, false }));
    }

    @Test
    void assertLastArgumentIsMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.addMasked("ismasked");

        assertTrue(builder.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true }));
    }

    @Test
    void assertSeveralMaskedArguments() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.addMasked("ismasked");
        builder.add("non masked arg");
        builder.addMasked("ismasked2");

        assertTrue(builder.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true, false, true }));
    }

    @Test
    void assertPrependAfterAddingMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addMasked("ismasked");
        builder.add("arg");
        builder.prepend("first", "second");

        assertTrue(builder.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, true, false }));
    }

    @Test
    void assertPrependBeforeAddingMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.prepend("first", "second");
        builder.addMasked("ismasked");
        builder.add("arg");

        assertTrue(builder.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, true, false }));
    }

    @Test
    void testToWindowsCommand() {
        ArgumentListBuilder builder = new ArgumentListBuilder().
                add("ant.bat").add("-Dfoo1=abc").  // nothing special, no quotes
                add("-Dfoo2=foo bar").add("-Dfoo3=/u*r").add("-Dfoo4=/us?").  // add quotes
                add("-Dfoo10=bar,baz").
                add("-Dfoo5=foo;bar^baz").add("-Dfoo6=<xml>&here;</xml>"). // add quotes
                add("-Dfoo7=foo|bar\"baz"). // add quotes and "" for "
                add("-Dfoo8=% %QED% %comspec% %-%(%.%"). // add quotes, and extra quotes for %Q and %c
                add("-Dfoo9=%'''%%@%"); // no quotes as none of the % are followed by a letter
        // By default, does not escape %VAR%
        assertThat(builder.toWindowsCommand().toCommandArray(), is(new String[] { "cmd.exe", "/C",
                "\"ant.bat", "-Dfoo1=abc", "\"-Dfoo2=foo bar\"", "\"-Dfoo3=/u*r\"", "\"-Dfoo4=/us?\"",
                "\"-Dfoo10=bar,baz\"", "\"-Dfoo5=foo;bar^baz\"", "\"-Dfoo6=<xml>&here;</xml>\"",
                "\"-Dfoo7=foo|bar\"\"baz\"", "\"-Dfoo8=% %QED% %comspec% %-%(%.%\"",
                "-Dfoo9=%'''%%@%", "&&", "exit", "%%ERRORLEVEL%%\"" }));
        // Pass flag to escape %VAR%
        assertThat(builder.toWindowsCommand(true).toCommandArray(), is(new String[] { "cmd.exe", "/C",
                "\"ant.bat", "-Dfoo1=abc", "\"-Dfoo2=foo bar\"", "\"-Dfoo3=/u*r\"", "\"-Dfoo4=/us?\"",
                "\"-Dfoo10=bar,baz\"", "\"-Dfoo5=foo;bar^baz\"", "\"-Dfoo6=<xml>&here;</xml>\"",
                "\"-Dfoo7=foo|bar\"\"baz\"", "\"-Dfoo8=% %\"Q\"ED% %\"c\"omspec% %-%(%.%\"",
                "-Dfoo9=%'''%%@%", "&&", "exit", "%%ERRORLEVEL%%\"" }));
        // Try to hide password
        builder.add("-Dpassword=hidden", true);
        // By default, does not escape %VAR%
        assertThat(builder.toWindowsCommand().toString(), is(
                "cmd.exe /C \"ant.bat -Dfoo1=abc \"\"-Dfoo2=foo bar\"\" \"-Dfoo3=/u*r\" "
                    + "\"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\" "
                    + "\"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\" "
                    + "\"\"-Dfoo8=% %QED% %comspec% %-%(%.%\"\" -Dfoo9=%'''%%@% ****** "
                    + "&& exit %%ERRORLEVEL%%\""));
        // Pass flag to escape %VAR%
        assertThat(builder.toWindowsCommand(true).toString(), is(
                "cmd.exe /C \"ant.bat -Dfoo1=abc \"\"-Dfoo2=foo bar\"\" \"-Dfoo3=/u*r\" "
                    + "\"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\" "
                    + "\"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\" "
                    + "\"\"-Dfoo8=% %\"Q\"ED% %\"c\"omspec% %-%(%.%\"\" -Dfoo9=%'''%%@% ****** "
                    + "&& exit %%ERRORLEVEL%%\""));
    }

    @Test
    @Disabled("It's only for reproduce JENKINS-28790 issue. It's added to testToWindowsCommand")
    @Issue("JENKINS-28790")
    void testToWindowsCommandMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder().
                add("ant.bat").add("-Dfoo1=abc").  // nothing special, no quotes
                add("-Dfoo2=foo bar").add("-Dfoo3=/u*r").add("-Dfoo4=/us?").  // add quotes
                add("-Dfoo10=bar,baz").
                add("-Dfoo5=foo;bar^baz").add("-Dfoo6=<xml>&here;</xml>"). // add quotes
                add("-Dfoo7=foo|bar\"baz"). // add quotes and "" for "
                add("-Dfoo8=% %QED% %comspec% %-%(%.%"). // add quotes, and extra quotes for %Q and %c
                add("-Dfoo9=%'''%%@%"). // no quotes as none of the % are followed by a letter
                add("-Dpassword=hidden", true);
        // By default, does not escape %VAR%
        assertThat(builder.toWindowsCommand().toString(), is(
                "cmd.exe /C \"ant.bat -Dfoo1=abc \"\"-Dfoo2=foo bar\"\" \"-Dfoo3=/u*r\" "
                    + "\"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\" "
                    + "\"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\" "
                    + "\"\"-Dfoo8=% %QED% %comspec% %-%(%.%\"\" -Dfoo9=%'''%%@% ****** "
                    + "&& exit %%ERRORLEVEL%%\""));
        // Pass flag to escape %VAR%
        assertThat(builder.toWindowsCommand(true).toString(), is(
                "cmd.exe /C \"ant.bat -Dfoo1=abc \"\"-Dfoo2=foo bar\"\" \"-Dfoo3=/u*r\" "
                    + "\"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\" "
                    + "\"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\" "
                    + "\"\"-Dfoo8=% %\"Q\"ED% %\"c\"omspec% %-%(%.%\"\" -Dfoo9=%'''%%@% ****** "
                    + "&& exit %%ERRORLEVEL%%\""));
    }

    @Test
    void assertMaskOnClone() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg1");
        builder.addMasked("masked1");
        builder.add("arg2");

        ArgumentListBuilder clone = builder.clone();
        assertTrue(clone.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = clone.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(builder.toMaskArray()));
    }

    private static final Map<String, String> KEY_VALUES = new LinkedHashMap<>();

    static {
        KEY_VALUES.put("key1", "value1");
        KEY_VALUES.put("key2", "value2");
        KEY_VALUES.put("key3", "value3");
    }

    private static final Set<String> MASKS = Set.of("key2");

    @Test
    void assertKeyValuePairsWithMask() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairs(null, KEY_VALUES, MASKS);

        assertTrue(builder.hasMaskedArguments(), "There should be masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true, false }));

    }

    @Test
    void assertKeyValuePairs() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairs(null, KEY_VALUES);

        assertFalse(builder.hasMaskedArguments(), "There should not be any masked arguments");
        boolean[] array = builder.toMaskArray();
        assertNotNull(array, "The mask array should not be null");
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, false }));
    }

    @Test
    void addKeyValuePairsFromPropertyString() throws IOException {
        final Map<String, String> map = new HashMap<>();
        map.put("PATH", "C:\\Windows");
        final VariableResolver<String> resolver = new VariableResolver.ByMap<>(map);

        final String properties = "my.path=$PATH";

        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairsFromPropertyString("", properties, resolver);
        assertEquals("my.path=C:\\Windows", builder.toString());

        builder = new ArgumentListBuilder();
        builder.addKeyValuePairsFromPropertyString("", properties, resolver, null);
        assertEquals("my.path=C:\\Windows", builder.toString());
    }

    @Test
    void numberOfBackslashesInPropertiesShouldBePreservedAfterMacroExpansion() throws IOException {
        final Map<String, String> map = new HashMap<>();
        map.put("ONE", "one\\backslash");
        map.put("TWO", "two\\\\backslashes");
        map.put("FOUR", "four\\\\\\\\backslashes");

        final String properties = """
                one=$ONE
                two=$TWO
                four=$FOUR
                """
        ;

        final String args = new ArgumentListBuilder()
                .addKeyValuePairsFromPropertyString("", properties, new VariableResolver.ByMap<>(map))
                .toString()
        ;

        assertThat(args, containsString("one=one\\backslash"));
        assertThat(args, containsString("two=two\\\\backslashes"));
        assertThat(args, containsString("four=four\\\\\\\\backslashes"));
    }
}
