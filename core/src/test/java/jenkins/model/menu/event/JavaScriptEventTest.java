/*
 * The MIT License
 *
 * Copyright (c) 2026
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

package jenkins.model.menu.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

class JavaScriptEventTest {

    @Test
    void blankScriptUrlIsNotExported() {
        String previous = Jenkins.RESOURCE_PATH;
        Jenkins.RESOURCE_PATH = "/static/abc12345";
        try {
            JavaScriptEvent event = DialogEvent.of("parametersDefinitionProperty/dialog");

            assertThat(event.getJavascriptUrl(), emptyString());
            assertThat(event.getAttributes().get("type"), is("dialog-opener"));
            assertThat(event.getAttributes().get("dialog-url"), is("parametersDefinitionProperty/dialog"));
        } finally {
            Jenkins.RESOURCE_PATH = previous;
        }
    }

    @Test
    void scriptPathKeepsTheResourcePrefix() {
        String previous = Jenkins.RESOURCE_PATH;
        Jenkins.RESOURCE_PATH = "/static/abc12345";
        try {
            JavaScriptEvent event =
                    JavaScriptEvent.of(Map.of("type", "build-now"), "jsbundles/pages/project/build.js");

            assertThat(event.getJavascriptUrl(), is("/static/abc12345/jsbundles/pages/project/build.js"));
        } finally {
            Jenkins.RESOURCE_PATH = previous;
        }
    }
}
