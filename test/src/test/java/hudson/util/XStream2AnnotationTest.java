/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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
import static org.hamcrest.Matchers.is;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.ExtensionList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class XStream2AnnotationTest {

    @RegisterExtension
    public JenkinsSessionExtension rr = new JenkinsSessionExtension();

    @Test
    void xStreamAlias() throws Throwable {
        rr.then(r -> {
            AnnotatedProcessed annotatedProcessed = AnnotatedProcessed.get();
            annotatedProcessed.x = 1;
            annotatedProcessed.save();
            assertThat(annotatedProcessed.xml(), is("<myconf-annotated-processed><x>1</x></myconf-annotated-processed>"));
            AnnotatedUnprocessed annotatedUnprocessed = AnnotatedUnprocessed.get();
            annotatedUnprocessed.x = 2;
            annotatedUnprocessed.save();
            assertThat(annotatedUnprocessed.xml(), is("<hudson.util.XStream2AnnotationTest_-AnnotatedUnprocessed><x>2</x></hudson.util.XStream2AnnotationTest_-AnnotatedUnprocessed>"));
            Programmatic programmatic = Programmatic.get();
            programmatic.x = 3;
            programmatic.save();
            assertThat(programmatic.xml(), is("<myconf-programmatic><x>3</x></myconf-programmatic>"));
        });
        rr.then(r -> {
            assertThat(AnnotatedProcessed.get().x, is(1));
            assertThat(AnnotatedUnprocessed.get().x, is(2));
            assertThat(Programmatic.get().x, is(3));
            // Typical content saved by Jenkins session when annotation autodetection was still enabled:
            AnnotatedUnprocessed.get().writeXml("<myconf-annotated-unprocessed><x>4</x></myconf-annotated-unprocessed>");
        });
        rr.then(r -> assertThat("CannotResolveClassException/IOException caught in Descriptor.load", AnnotatedUnprocessed.get().x, is(0)));
    }

    @XStreamAlias("myconf-annotated-processed")
    @TestExtension("xStreamAlias")
    public static class AnnotatedProcessed extends GlobalConfiguration {
        static AnnotatedProcessed get() {
            return ExtensionList.lookupSingleton(AnnotatedProcessed.class);
        }

        int x;

        @SuppressWarnings(value = "checkstyle:redundantmodifier")
        public AnnotatedProcessed() {
            getConfigFile().getXStream().processAnnotations(AnnotatedProcessed.class);
            load();
        }

        String xml() throws IOException {
            return getConfigFile().asString().replaceAll("\n *", "").replaceAll("<[?].+?[?]>", "");
        }
    }

    @XStreamAlias("myconf-annotated-unprocessed")
    @TestExtension("xStreamAlias")
    public static class AnnotatedUnprocessed extends GlobalConfiguration {
        static AnnotatedUnprocessed get() {
            return ExtensionList.lookupSingleton(AnnotatedUnprocessed.class);
        }

        int x;

        @SuppressWarnings(value = "checkstyle:redundantmodifier")
        public AnnotatedUnprocessed() {
            load();
        }

        String xml() throws IOException {
            return getConfigFile().asString().replaceAll("\n *", "").replaceAll("<[?].+?[?]>", "");
        }

        void writeXml(String xml) throws IOException {
            Files.writeString(getConfigFile().getFile().toPath(), xml, StandardCharsets.UTF_8);
        }
    }

    @TestExtension("xStreamAlias")
    public static class Programmatic extends GlobalConfiguration {
        static Programmatic get() {
            return ExtensionList.lookupSingleton(Programmatic.class);
        }

        int x;

        @SuppressWarnings(value = "checkstyle:redundantmodifier")
        public Programmatic() {
            getConfigFile().getXStream().alias("myconf-programmatic", Programmatic.class);
            load();
        }

        String xml() throws IOException {
            return getConfigFile().asString().replaceAll("\n *", "").replaceAll("<[?].+?[?]>", "");
        }
    }

}
