/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.WarExploder;

public class ClassPathTest {

    @Rule
    public ErrorCollector errors = new ErrorCollector();

    private static final Set<String> KNOWN_VIOLATIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // TODO duplicated in [jline-2.14.6.jar, jansi-1.11.jar]
            "org/fusesource/hawtjni/runtime/Callback.class",
            "org/fusesource/hawtjni/runtime/JNIEnv.class",
            "org/fusesource/hawtjni/runtime/Library.class",
            "org/fusesource/hawtjni/runtime/PointerMath.class",
            "org/fusesource/jansi/Ansi$1.class",
            "org/fusesource/jansi/Ansi$2.class",
            "org/fusesource/jansi/Ansi$Attribute.class",
            "org/fusesource/jansi/Ansi$Color.class",
            "org/fusesource/jansi/Ansi$Erase.class",
            "org/fusesource/jansi/Ansi$NoAnsi.class",
            "org/fusesource/jansi/Ansi.class",
            "org/fusesource/jansi/AnsiConsole$1.class",
            "org/fusesource/jansi/AnsiConsole.class",
            "org/fusesource/jansi/AnsiOutputStream.class",
            "org/fusesource/jansi/AnsiRenderer$Code.class",
            "org/fusesource/jansi/AnsiRenderer.class",
            "org/fusesource/jansi/AnsiRenderWriter.class",
            "org/fusesource/jansi/AnsiString.class",
            "org/fusesource/jansi/HtmlAnsiOutputStream.class",
            "org/fusesource/jansi/internal/CLibrary.class",
            "org/fusesource/jansi/internal/Kernel32$CONSOLE_SCREEN_BUFFER_INFO.class",
            "org/fusesource/jansi/internal/Kernel32$COORD.class",
            "org/fusesource/jansi/internal/Kernel32$INPUT_RECORD.class",
            "org/fusesource/jansi/internal/Kernel32$KEY_EVENT_RECORD.class",
            "org/fusesource/jansi/internal/Kernel32$SMALL_RECT.class",
            "org/fusesource/jansi/internal/Kernel32.class",
            "org/fusesource/jansi/internal/WindowsSupport.class",
            "org/fusesource/jansi/WindowsAnsiOutputStream.class",
            // TODO duplicated in [kxml2-2.3.0.jar, xpp3-1.1.4c.jar]
            "org/xmlpull/v1/XmlPullParser.class",
            "org/xmlpull/v1/XmlPullParserException.class",
            "org/xmlpull/v1/XmlPullParserFactory.class",
            "org/xmlpull/v1/XmlSerializer.class",
            // TODO duplicated in [remoting.jar, args4j-2.33.jar]
            "OSGI-OPT/src/org/kohsuke/args4j/Argument.java",
            "OSGI-OPT/src/org/kohsuke/args4j/ClassParser.java",
            "OSGI-OPT/src/org/kohsuke/args4j/CmdLineException.java",
            "OSGI-OPT/src/org/kohsuke/args4j/CmdLineParser.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Config.java",
            "OSGI-OPT/src/org/kohsuke/args4j/ExampleMode.java",
            "OSGI-OPT/src/org/kohsuke/args4j/FieldParser.java",
            "OSGI-OPT/src/org/kohsuke/args4j/IllegalAnnotationError.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Localizable.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Messages.java",
            "OSGI-OPT/src/org/kohsuke/args4j/NamedOptionDef.java",
            "OSGI-OPT/src/org/kohsuke/args4j/OptionDef.java",
            "OSGI-OPT/src/org/kohsuke/args4j/OptionHandlerFilter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/OptionHandlerRegistry.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Option.java",
            "OSGI-OPT/src/org/kohsuke/args4j/package.html",
            "OSGI-OPT/src/org/kohsuke/args4j/ParserProperties.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/AnnotationImpl.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ArgumentImpl.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ArrayFieldSetter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/BooleanOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ByteOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/CharOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ConfigElement.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/DelimitedOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/DoubleOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/EnumOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ExplicitBooleanOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/FieldSetter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/FileOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/FloatOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/Getter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/InetAddressOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/IntOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/LongOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MacAddressOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MapOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/Messages.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MethodSetter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MultiFileOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MultiPathOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/MultiValueFieldSetter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/OneArgumentOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/OptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/OptionImpl.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/package.html",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/Parameters.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/PathOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/PatternOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/RestOfArgumentsHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/Setter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/Setters.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/ShortOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/StopOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/StringArrayOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/StringOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/SubCommandHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/SubCommand.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/SubCommands.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/URIOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/URLOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/spi/UuidOptionHandler.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Starter.java",
            "OSGI-OPT/src/org/kohsuke/args4j/Utilities.java",
            "OSGI-OPT/src/org/kohsuke/args4j/XmlParser.java")));

    @Issue("JENKINS-46754")
    @Test
    public void uniqueness() throws Exception {
        Map<String, List<String>> entries = new TreeMap<>();
        for (File jar : new File(WarExploder.getExplodedDir(), "WEB-INF/lib").listFiles((dir, name) -> name.endsWith(".jar"))) {
            String jarname = jar.getName();
            try (JarFile jf = new JarFile(jar)) {
                for (JarEntry e : Collections.list(jf.entries())) {
                    String name = e.getName();
                    if (name.startsWith("META-INF/") || name.endsWith("/") || !name.contains("/")) {
                        continue;
                    }
                    entries.computeIfAbsent(name, k -> new ArrayList<>()).add(jarname);
                }
            }
        }
        entries.forEach((name, jarnames) -> {
            if (jarnames.size() > 1 && !KNOWN_VIOLATIONS.contains(name)) { // Matchers.hasSize unfortunately does not display the collection
                errors.addError(new AssertionError(name + " duplicated in " + jarnames));
            }
        });
    }

}
