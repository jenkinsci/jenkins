/*
 * The MIT License
 *
 * Copyright (c) 2024, Bob Du
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

package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Descriptor;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.lang.Klass;

class GetLocaleStaticHelpUrlTest {

    @Test
    void getStaticHelpUrlAcceptEnResDefault() {
        // Accept-Language: en
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.ENGLISH
        );

        Klass klass = mockKlass(
                "help-id.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id.html");
    }

    @Test
    void getStaticHelpUrlAcceptDeResDeNoCountry() {
        // Accept-Language: de-DE,de;q=0.9,en;q=0.8
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.GERMANY,
                Locale.GERMAN,
                Locale.ENGLISH
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_de.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_de.html");
    }

    @Test
    void getStaticHelpUrlAcceptDeResDeCountry() {
        // Accept-Language: de-DE,de;q=0.9,en;q=0.8
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.GERMANY,
                Locale.GERMAN,
                Locale.ENGLISH
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_de_DE.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_de_DE.html");
    }

    @Test
    void getStaticHelpUrlAcceptDeResBoth() {
        // Accept-Language: de-DE,de;q=0.9,en;q=0.8
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.GERMANY,
                Locale.GERMAN,
                Locale.ENGLISH
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_de.html",
                "help-id_de_DE.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_de_DE.html");
    }

    @Test
    void getStaticHelpUrlAcceptZhResDefault() {
        // Accept-Language: zh,zh-CN;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.CHINESE,
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.TRADITIONAL_CHINESE
        );

        Klass klass = mockKlass(
                "help-id.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id.html");
    }

    @Test
    void getStaticHelpUrlAcceptZhResZhCountry() {
        // Accept-Language: zh,zh-CN;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.CHINESE,
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.TRADITIONAL_CHINESE
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_zh_CN.html"
        );

        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_zh_CN.html");
    }

    @Test
    void getStaticHelpUrlAcceptZhResBoth() {
        // Accept-Language: zh,zh-CN;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.CHINESE,
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.TRADITIONAL_CHINESE
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_zh.html",
                "help-id_zh_CN.html"
        );
        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_zh.html");
    }

    @Test
    void getStaticHelpUrlAcceptZhResMore() {
        // Accept-Language: zh,zh-CN;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.CHINESE,
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.TRADITIONAL_CHINESE
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_zh_CN.html",
                "help-id_zh_TW.html",
                "help-id_de_DE.html"
        );
        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id_zh_CN.html");
    }

    @Issue("JENKINS-73246")
    @Test
    void getStaticHelpUrlAcceptEnFirst() {
        // Accept-Language: en-US,en;q=0.9,de;q=0.8
        StaplerRequest2 req = mockStaplerRequest2(
                Locale.US,
                Locale.ENGLISH,
                Locale.GERMAN
        );

        Klass klass = mockKlass(
                "help-id.html",
                "help-id_de.html"
        );
        URL id = Descriptor.getStaticHelpUrl(req, klass, "-id");
        assertThatLocaleResourceIs(id, "help-id.html");
    }

    private StaplerRequest2 mockStaplerRequest2(Locale... localeArr) {
        StaplerRequest2 req = mock(StaplerRequest2.class);
        Enumeration<Locale> locales = Collections.enumeration(Arrays.asList(localeArr));
        when(req.getLocales()).thenReturn(locales);
        return req;
    }

    private Klass mockKlass(String... resources) {
        Klass klass = mock(Klass.class);
        for (String resource : resources) {
            when(klass.getResource(resource)).thenReturn(getUrl("https://jenkins/" + resource));
        }
        return klass;
    }

    private URL getUrl(final String realUrl) {
        try {
            return new URL(realUrl);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertThatLocaleResourceIs(URL url, String resource) {
        assertThat(url.toString(), equalTo("https://jenkins/" + resource));
    }
}
