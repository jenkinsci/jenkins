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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.WarExploder;
import org.opentest4j.MultipleFailuresError;

class ClassPathTest {

    private final List<Throwable> errors = new ArrayList<>();

    private static final Set<String> KNOWN_VIOLATIONS = Set.of(
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
            "org/fusesource/jansi/WindowsAnsiOutputStream.class");

    @AfterEach
    void tearDown() {
        if (!errors.isEmpty()) {
            throw new MultipleFailuresError(null, errors);
        }
    }

    @Issue("JENKINS-46754")
    @Test
    void uniqueness() throws Exception {
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
                errors.add(new AssertionError(name + " duplicated in " + jarnames));
            }
        });
    }

}
