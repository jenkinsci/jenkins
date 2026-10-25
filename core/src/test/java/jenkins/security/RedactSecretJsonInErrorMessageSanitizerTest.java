/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

@Issue("SECURITY-765")
class RedactSecretJsonInErrorMessageSanitizerTest {
    @Test
    void noSecrets() {
        assertRedaction(
                "{'a': 1, 'b': '2', 'c': {'c1': 1, 'c2': '2', 'c3': ['3a', '3b']}, 'd': ['4a', {'d1': 1, 'd2': '2'}]}",
                "{'a': 1, 'b': '2', 'c': {'c1': 1, 'c2': '2', 'c3': ['3a', '3b']}, 'd': ['4a', {'d1': 1, 'd2': '2'}]}"
        );
    }

    @Test
    void simpleWithSecret() {
        assertRedaction(
                "{'a': 'secret', 'b': 'other', '$redact': 'a'}",
                "{'a': '[value redacted]', 'b': 'other', '$redact': 'a'}"
        );
    }

    @Test
    void singleWithRedactedInArray() {
        assertRedaction(
                "{'a': 'secret', 'b': 'other', '$redact': ['a']}",
                "{'a': '[value redacted]', 'b': 'other', '$redact': ['a']}"
        );
    }

    @Test
    void objectRedactedAcceptedButNotProcessed() {
        assertRedaction(
                "{'a': 'secret', 'b': 'other', '$redact': {'a': 'a'}}",
                "{'a': 'secret', 'b': 'other', '$redact': {'a': 'a'}}"
        );
    }

    @Test
    void weirdValuesInRedactedAcceptedButNotProcessed() {
        assertRedaction(
                "{'a': 'secret', 'b': 'other', '$redact': [null, true, false, 1, 2, 'a']}",
                "{'a': '[value redacted]', 'b': 'other', '$redact': [null, true, false, 1, 2, 'a']}"
        );
    }

    @Test
    void ensureTrueAndOneAsStringAreSupportedAsRedactedKey() {
        //only null is not supported, as passing 'null' is considered as null
        assertRedaction(
                "{'true': 'secret1', '1': 'secret3', 'b': 'other', '$redact': ['true', '1']}",
                "{'true': '[value redacted]', '1': '[value redacted]', 'b': 'other', '$redact': ['true', '1']}"
        );
    }

    @Test
    void redactFullBranch() {
        assertRedaction(
                "{'a': {'s1': 'secret1', 's2': 'secret2', 's3': [1,2,3]}, 'b': [4,5,6], 'c': 'other', '$redact': ['a', 'b']}",
                "{'a': '[value redacted]', 'b': '[value redacted]', 'c': 'other', '$redact': ['a', 'b']}"
        );
    }

    @Test
    void multipleSecretAtSameLevel() {
        assertRedaction(
                "{'a1': 'secret1', 'a2': 'secret2', 'b': 'other', '$redact': ['a1', 'a2']}",
                "{'a1': '[value redacted]', 'a2': '[value redacted]', 'b': 'other', '$redact': ['a1', 'a2']}"
        );
    }

    @Test
    void redactedKeyWithoutCorrespondences() {
        assertRedaction(
                "{'a1': 'secret1', 'a2': 'secret2', 'b': 'other', '$redact': ['a0', 'a1', 'a2', 'a3']}",
                "{'a1': '[value redacted]', 'a2': '[value redacted]', 'b': 'other', '$redact': ['a0', 'a1', 'a2', 'a3']}"
        );
    }

    @Test
    void secretsAtMultipleLevels() {
        assertRedaction(
                "{'a1': 'secret1', 'a2': 'secret2', 'b': 'other', '$redact': ['a1', 'a2'], 'sub': {'c1': 'secret1', 'c2': 'secret2', 'c3': 'other', '$redact': ['c1', 'c2']}}",
                "{'a1': '[value redacted]', 'a2': '[value redacted]', 'b': 'other', '$redact': ['a1', 'a2'], 'sub': {'c1': '[value redacted]', 'c2': '[value redacted]', 'c3': 'other', '$redact': ['c1', 'c2']}}"
        );
    }

    @Test
    void noInteractionBetweenLevels() {
        assertRedaction(
                "{'a': 'secret', 'b': 'other', 'c': 'other', '$redact': 'a', 'sub': {'a': 'other', 'b': 'secret', 'c': 'other', '$redact': 'b'}}",
                "{'a': '[value redacted]', 'b': 'other', 'c': 'other', '$redact': 'a', 'sub': {'a': 'other', 'b': '[value redacted]', 'c': 'other', '$redact': 'b'}}"
        );
    }

    @Test
    void deeplyNestedObject() {
        assertRedaction(
                "{'sub': {'arr': ['d1', 2, {'a1': 'other', 'b1':'other', 'c1': 'secret', '$redact': 'c1'}, 4, {'a2': 'other', 'b2': 'other', 'c2': 'secret', '$redact': 'c2'}]}, '$redact': 'b'}",
                "{'sub': {'arr': ['d1', 2, {'a1': 'other', 'b1':'other', 'c1': '[value redacted]', '$redact': 'c1'}, 4, {'a2': 'other', 'b2': 'other', 'c2': '[value redacted]', '$redact': 'c2'}]}, '$redact': 'b'}"
        );
    }

    private void assertRedaction(String from, String to) {
        JSONObject input = JSONObject.fromObject(from.replace('\'', '"'));
        JSONObject output = RedactSecretJsonInErrorMessageSanitizer.INSTANCE.sanitize(input);
        assertNotSame(output, input);
        assertEquals(to.replace('\'', '"').replace(" ", ""),
                output.toString().replace(" ", ""),
                "redaction of " + from);
    }
}
