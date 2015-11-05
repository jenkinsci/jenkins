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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ArgumentListBuilderTest {

    @Test
    public void assertEmptyMask() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.add("other", "arguments");

        assertFalse("There should not be any masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, false }));
    }

    @Test
    public void assertLastArgumentIsMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.addMasked("ismasked");

        assertTrue("There should be masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true }));
    }

    @Test
    public void assertSeveralMaskedArguments() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg");
        builder.addMasked("ismasked");
        builder.add("non masked arg");
        builder.addMasked("ismasked2");

        assertTrue("There should be masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true, false, true }));
    }

    @Test
    public void assertPrependAfterAddingMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addMasked("ismasked");
        builder.add("arg");
        builder.prepend("first", "second");

        assertTrue("There should be masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, true, false }));
    }

    @Test
    public void assertPrependBeforeAddingMasked() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.prepend("first", "second");
        builder.addMasked("ismasked");
        builder.add("arg");

        assertTrue("There should be masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, true, false }));
    }

    @Test
    public void testToWindowsCommand() {
        ArgumentListBuilder builder = new ArgumentListBuilder(
                "ant.bat", "-Dfoo1=abc",  // nothing special, no quotes
                "-Dfoo2=foo bar", "-Dfoo3=/u*r", "-Dfoo4=/us?",  // add quotes
                                                 "-Dfoo10=bar,baz",
                "-Dfoo5=foo;bar^baz", "-Dfoo6=<xml>&here;</xml>", // add quotes
                "-Dfoo7=foo|bar\"baz", // add quotes and "" for "
                "-Dfoo8=% %QED% %comspec% %-%(%.%", // add quotes, and extra quotes for %Q and %c
                "-Dfoo9=%'''%%@%"); // no quotes as none of the % are followed by a letter
        // By default, does not escape %VAR%
        assertThat(builder.toWindowsCommand().toCommandArray(), is(new String[] { "cmd.exe", "/C",
                "\"ant.bat -Dfoo1=abc \"-Dfoo2=foo bar\""
                + " \"-Dfoo3=/u*r\" \"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\""
                + " \"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\""
                + " \"-Dfoo8=% %QED% %comspec% %-%(%.%\""
                + " -Dfoo9=%'''%%@% && exit %%ERRORLEVEL%%\"" }));
        // Pass flag to escape %VAR%
        assertThat(builder.toWindowsCommand(true).toCommandArray(), is(new String[] { "cmd.exe", "/C",
                "\"ant.bat -Dfoo1=abc \"-Dfoo2=foo bar\""
                + " \"-Dfoo3=/u*r\" \"-Dfoo4=/us?\" \"-Dfoo10=bar,baz\" \"-Dfoo5=foo;bar^baz\""
                + " \"-Dfoo6=<xml>&here;</xml>\" \"-Dfoo7=foo|bar\"\"baz\""
                + " \"-Dfoo8=% %\"Q\"ED% %\"c\"omspec% %-%(%.%\""
                + " -Dfoo9=%'''%%@% && exit %%ERRORLEVEL%%\"" }));
    }

    @Test
    public void assertMaskOnClone() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.add("arg1");
        builder.addMasked("masked1");
        builder.add("arg2");

        ArgumentListBuilder clone = builder.clone();
        assertTrue("There should be masked arguments", clone.hasMaskedArguments());
        boolean[] array = clone.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(builder.toMaskArray()));
    }
    
    private static final Map<String, String> KEY_VALUES = new HashMap<String, String>() {{
        put("key1", "value1");
        put("key2", "value2");
        put("key3", "value3");
    }};

    private static final Set<String> MASKS = new HashSet<String>() {{
        add("key2");
    }};
    
    @Test
    public void assertKeyValuePairsWithMask() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairs(null, KEY_VALUES, MASKS);

        assertTrue("There should be masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, true, false }));

    }

    @Test
    public void assertKeyValuePairs() {
        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairs(null, KEY_VALUES);

        assertFalse("There should not be any masked arguments", builder.hasMaskedArguments());
        boolean[] array = builder.toMaskArray();
        assertNotNull("The mask array should not be null", array);
        assertThat("The mask array was incorrect", array, is(new boolean[] { false, false, false }));
    }

    @Test
    public void addKeyValuePairsFromPropertyString() throws IOException {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("PATH", "C:\\Windows");
        final VariableResolver<String> resolver = new VariableResolver.ByMap<String>(map);

        final String properties = "my.path=$PATH";

        ArgumentListBuilder builder = new ArgumentListBuilder();
        builder.addKeyValuePairsFromPropertyString("", properties, resolver);
        assertEquals("my.path=C:\\Windows", builder.toString());

        builder = new ArgumentListBuilder();
        builder.addKeyValuePairsFromPropertyString("", properties, resolver, null);
        assertEquals("my.path=C:\\Windows", builder.toString());
    }

    @Test
    public void numberOfBackslashesInPropertiesShouldBePreservedAfterMacroExpansion() throws IOException {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("ONE", "one\\backslash");
        map.put("TWO", "two\\\\backslashes");
        map.put("FOUR", "four\\\\\\\\backslashes");

        final String properties = new StringBuilder()
                .append("one=$ONE\n")
                .append("two=$TWO\n")
                .append("four=$FOUR\n")
                .toString()
        ;

        final String args = new ArgumentListBuilder()
                .addKeyValuePairsFromPropertyString("", properties, new VariableResolver.ByMap<String>(map))
                .toString()
        ;

        assertThat(args, containsString("one=one\\backslash"));
        assertThat(args, containsString("two=two\\\\backslashes"));
        assertThat(args, containsString("four=four\\\\\\\\backslashes"));
    }
}
