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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.model.Descriptor;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.lang.Klass;
import org.mockito.MockedStatic;

public class GetLocaleStaticHelpUrlTest {

    @Before
    public void setUp() {
        MockedStatic<Stapler> mockedStapler = mockStatic(Stapler.class);
        StaplerRequest staplerRequest = mock(StaplerRequest.class);
        mockedStapler.when(Stapler::getCurrentRequest).thenReturn(staplerRequest);

        // Accept-Language: zh,zh-CN;q=0.9,en-US;q=0.8,en;q=0.7,zh-TW;q=0.6
        Enumeration<Locale> locales = Collections.enumeration(
                List.of(Locale.CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH, Locale.US, Locale.TRADITIONAL_CHINESE)
        );
        when(staplerRequest.getLocales()).thenReturn(locales);
    }

    @Test
    public void getStaticHelpUrl1() {
        Klass klass = mock(Klass.class);
        when(klass.getResource("help-id.html")).thenReturn(getUrl("https://jenkins/help_id.html"));
        when(klass.getResource("help-id_zh_CN.html")).thenReturn(getUrl("https://jenkins/help-id_zh_CN.html"));
        URL id = Descriptor.getStaticHelpUrl(klass, "-id");
        assertThat(id.toString(), equalTo("https://jenkins/help-id_zh_CN.html"));
    }

    @Test
    public void getStaticHelpUrl2() {
        Klass klass = mock(Klass.class);
        when(klass.getResource("help-id.html")).thenReturn(getUrl("https://jenkins/help_id.html"));
        when(klass.getResource("help-id_zh.html")).thenReturn(getUrl("https://jenkins/help-id_zh.html"));
        when(klass.getResource("help-id_zh_CN.html")).thenReturn(getUrl("https://jenkins/help-id_zh_CN.html"));
        URL id = Descriptor.getStaticHelpUrl(klass, "-id");
        assertThat(id.toString(), equalTo("https://jenkins/help-id_zh.html"));
    }

    @Test
    public void getStaticHelpUrl3() {
        Klass klass = mock(Klass.class);
        when(klass.getResource("help-id.html")).thenReturn(getUrl("https://jenkins/help_id.html"));
        when(klass.getResource("help-id_zh_CN.html")).thenReturn(getUrl("https://jenkins/help-id_zh_CN.html"));
        when(klass.getResource("help-id_zh_TW.html")).thenReturn(getUrl("https://jenkins/help-id_zh_TW.html"));
        URL id = Descriptor.getStaticHelpUrl(klass, "-id");
        assertThat(id.toString(), equalTo("https://jenkins/help-id_zh_CN.html"));
    }

    @Test
    public void getStaticHelpUrl4() {
        Klass klass = mock(Klass.class);
        when(klass.getResource("help-id.html")).thenReturn(getUrl("https://jenkins/help_id.html"));
        when(klass.getResource("help-id_en.html")).thenReturn(getUrl("https://jenkins/help-id_en.html"));
        when(klass.getResource("help-id_zh_TW.html")).thenReturn(getUrl("https://jenkins/help-id_zh_TW.html"));
        URL id = Descriptor.getStaticHelpUrl(klass, "-id");
        assertThat(id.toString(), equalTo("https://jenkins/help-id_en.html"));
    }

    private URL getUrl(final String realUrl) {
        try {
            return new URL(realUrl);
        } catch (Exception ex) {

            throw new RuntimeException(ex);
        }
    }
}
