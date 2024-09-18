/*
 * The MIT License
 *
 * Copyright (c) 2022 CloudBees, Inc.
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.thoughtworks.xstream.security.InputManipulationException;
import hudson.model.Saveable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jenkins.util.xstream.CriticalXStreamException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class RobustMapConverterTest {
    private final boolean originalRecordFailures = RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS;

    @Before
    public void before() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = true;
    }

    @After
    public void after() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = originalRecordFailures;
    }

    /**
     * As RobustMapConverter is the replacer of the default MapConverter
     * We had to patch it in order to not be impacted by CVE-2021-43859
     */
    // force timeout to prevent DoS due to test in the case the DoS prevention is broken
    @Test(timeout = 30 * 1000)
    @Issue("SECURITY-2602")
    public void dosIsPrevented_customProgrammaticallyTimeout() {
        XStream2 xstream2 = new XStream2();

        Map<Object, Object> map = preparePayload();


        xstream2.setCollectionUpdateLimit(3);
        final String xml = xstream2.toXML(map);
        CriticalXStreamException e = assertThrows(CriticalXStreamException.class, () -> xstream2.fromXML(xml));
        Throwable cause = e.getCause();
        assertNotNull(cause);
        assertThat(cause, instanceOf(InputManipulationException.class));
        InputManipulationException ime = (InputManipulationException) cause;
        assertTrue("Limit expected in message", ime.getMessage().contains("exceeds 3 seconds"));
    }

    @Test(timeout = 30 * 1000)
    @Issue("SECURITY-2602")
    public void dosIsPrevented_customPropertyTimeout() {
        String currentValue = System.getProperty(XStream2.COLLECTION_UPDATE_LIMIT_PROPERTY_NAME);
        try {
            System.setProperty(XStream2.COLLECTION_UPDATE_LIMIT_PROPERTY_NAME, "4");

            XStream2 xstream2 = new XStream2();

            Map<Object, Object> map = preparePayload();

            final String xml = xstream2.toXML(map);
            CriticalXStreamException e = assertThrows(CriticalXStreamException.class, () -> xstream2.fromXML(xml));
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertThat(cause, instanceOf(InputManipulationException.class));
            InputManipulationException ime = (InputManipulationException) cause;
            assertTrue("Limit expected in message", ime.getMessage().contains("exceeds 4 seconds"));
        } finally {
            if (currentValue == null) {
                System.clearProperty(XStream2.COLLECTION_UPDATE_LIMIT_PROPERTY_NAME);
            } else {
                System.setProperty(XStream2.COLLECTION_UPDATE_LIMIT_PROPERTY_NAME, currentValue);
            }
        }
    }

    // force timeout to prevent DoS due to test in the case the DoS prevention is broken
    @Test(timeout = 30 * 1000)
    @Issue("SECURITY-2602")
    public void dosIsPrevented_defaultTimeout() {
        XStream2 xstream2 = new XStream2();

        Map<Object, Object> map = preparePayload();

        final String xml = xstream2.toXML(map);
        CriticalXStreamException e = assertThrows(CriticalXStreamException.class, () -> xstream2.fromXML(xml));
        Throwable cause = e.getCause();
        assertNotNull(cause);
        assertThat(cause, instanceOf(InputManipulationException.class));
        InputManipulationException ime = (InputManipulationException) cause;
        assertTrue("Limit expected in message", ime.getMessage().contains("exceeds 5 seconds"));
    }

    // Inspired by https://github.com/x-stream/xstream/commit/e8e88621ba1c85ac3b8620337dd672e0c0c3a846#diff-9fde4ecf1bb4dc9850c031cb161960d2e61e069b386fa0b3db0d57e0e9f5baa
    // which seems to be inspired by https://owasp.org/www-community/vulnerabilities/Deserialization_of_untrusted_data
    private Map<Object, Object> preparePayload() {
        /*
            On a i7-1185G7@3.00GHz (2021)
            Full test time:
            max=8 => ~1s
            max=9 => ~1s
            max=10 => ~1s
            max=11 => ~1s
            max=12 => ~1.5s
            max=13 => ~2s
            max=14 => ~5s
            max=15 => ~17s
            max=16 => ~66s
            max=18 => est. ~5m

            With the protection in place, each test is taking ~12 seconds before the protection triggers
        */

        final Map<Object, Object> map = new HashMap<>();
        Map<Object, Object> m1 = map;
        Map<Object, Object> m2 = new HashMap<>();
        for (int i = 0; i < 18; i++) {
            final Map<Object, Object> t1 = new HashMap<>();
            final Map<Object, Object> t2 = new HashMap<>();
            t1.put("a", "b");
            t2.put("c", "d");
            m1.put(t1, t2);
            m1.put(t2, t1);
            m2.put(t2, t1);
            m2.put(t1, t2);
            m1 = t1;
            m2 = t2;
        }
        return map;
    }

    @Test
    public void robustAgainstInvalidEntry() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = true;
        XStream2 xstream2 = new XStream2();
        String xml =
            """
            <hudson.util.RobustMapConverterTest_-Data>
              <map>
                <string>key1</string>
                <entry>
                  <string>key2</string>
                  <string>value2</string>
                </entry>
              </map>
            </hudson.util.RobustMapConverterTest_-Data>
            """;
        Data data = (Data) xstream2.fromXML(xml);
        assertThat(data.map, equalTo(Map.of("key2", "value2")));
    }

    @Test
    public void robustAgainstInvalidEntryWithNoValue() {
        XStream2 xstream2 = new XStream2();
        String xml =
            """
            <hudson.util.RobustMapConverterTest_-Data>
              <map>
                <entry>
                  <string>key1</string>
                </entry>
                <entry>
                  <string>key2</string>
                  <string>value2</string>
                </entry>
              </map>
            </hudson.util.RobustMapConverterTest_-Data>
            """;
        Data data = (Data) xstream2.fromXML(xml);
        assertThat(data.map, equalTo(Map.of("key2", "value2")));
    }

    @Test
    public void robustAgainstInvalidKeyType() {
        XStream2 xstream2 = new XStream2();
        String xml =
            """
            <hudson.util.RobustMapConverterTest_-Data>
              <map>
                <entry>
                  <int>1</int> <!-- bad type -->
                  <string>value1</string>
                </entry>
                <entry>
                  <string>key2</string>
                  <string>value2</string>
                </entry>
              </map>
            </hudson.util.RobustMapConverterTest_-Data>
            """;
        Data data = (Data) xstream2.fromXML(xml);
        assertThat(data.map, equalTo(Map.of("key2", "value2")));
    }

    @Test
    public void robustAgainstInvalidValueType() {
        XStream2 xstream2 = new XStream2();
        String xml =
            """
            <hudson.util.RobustMapConverterTest_-Data>
              <map>
                <entry>
                  <string>key1</string>
                  <string>value1</string>
                </entry>
                <entry>
                  <string>key2</string>
                  <int>2</int> <!-- bad type -->
                </entry>
              </map>
            </hudson.util.RobustMapConverterTest_-Data>
            """;
        Data data = (Data) xstream2.fromXML(xml);
        assertThat(data.map, equalTo(Map.of("key1", "value1")));
    }

    private static class Data implements Saveable {
        Map<String, String> map;

        @Override
        public void save() throws IOException {
            // We only implement Saveable so that RobustReflectionConverter logs deserialization problems.
        }
    }
}
