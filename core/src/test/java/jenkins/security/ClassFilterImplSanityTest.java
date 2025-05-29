/*
 * The MIT License
 *
 * Copyright 2017-2018 CloudBees, Inc.
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import jenkins.util.MemoryReductionUtil;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;

/**
 * Tests for {@link ClassFilterImpl}.
 * More tests are available in the {@code test} module.
 */
@For(ClassFilterImpl.class)
class ClassFilterImplSanityTest {

    @Test
    void whitelistSanity() throws Exception {
        try (InputStream is = ClassFilterImpl.class.getResourceAsStream("whitelisted-classes.txt")) {
            List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8).stream().filter(line -> !line.matches("#.*|\\s*")).toList();
            assertThat("whitelist is NOT ordered", new TreeSet<>(lines), contains(lines.toArray(MemoryReductionUtil.EMPTY_STRING_ARRAY)));
            for (String line : lines) {
                try {
                    Class.forName(line);
                } catch (ClassNotFoundException x) {
                    System.err.println("skipping checks of unknown class " + line);
                }
            }
        }
    }

}
