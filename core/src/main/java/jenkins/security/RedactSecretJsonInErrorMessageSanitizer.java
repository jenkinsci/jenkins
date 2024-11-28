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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.JsonInErrorMessageSanitizer;

@Restricted(NoExternalUse.class)
public class RedactSecretJsonInErrorMessageSanitizer implements JsonInErrorMessageSanitizer {
    private static final Logger LOGGER = Logger.getLogger(RedactSecretJsonInErrorMessageSanitizer.class.getName());

    // must be kept in sync with hudson-behavior.js in function buildFormTree, password case
    public static final String REDACT_KEY = "$redact";
    public static final String REDACT_VALUE = "[value redacted]";

    public static final RedactSecretJsonInErrorMessageSanitizer INSTANCE = new RedactSecretJsonInErrorMessageSanitizer();

    private RedactSecretJsonInErrorMessageSanitizer() {}

    @Override
    public JSONObject sanitize(JSONObject jsonObject) {
        return copyAndSanitizeObject(jsonObject);
    }

    /**
     * Accept anything as value for the {@link #REDACT_KEY} but only process the first level of an array and the string value.
     */
    private Set<String> retrieveRedactedKeys(JSONObject jsonObject) {
        Set<String> redactedKeySet = new HashSet<>();
        if (jsonObject.has(REDACT_KEY)) {
            Object value = jsonObject.get(REDACT_KEY);
            if (value instanceof JSONArray) {
                for (Object o : jsonObject.getJSONArray(REDACT_KEY)) {
                    if (o instanceof String) {
                        redactedKeySet.add((String) o);
                    } else {
                        // array, object, null, number, boolean
                        LOGGER.log(Level.WARNING, "Unsupported type " + o.getClass().getName() + " for " + REDACT_KEY + ", please use either a single String value or an Array");
                    }
                }
            } else if (value instanceof String) {
                redactedKeySet.add((String) value);
            } else {
                // object, null, number, boolean
                LOGGER.log(Level.WARNING, "Unsupported type " + value.getClass().getName() + " for " + REDACT_KEY + ", please use either a single String value or an Array");
            }
        }
        return redactedKeySet;
    }

    private Object copyAndSanitize(Object value) {
        if (value instanceof JSONObject) {
            return copyAndSanitizeObject((JSONObject) value);
        } else if (value instanceof JSONArray) {
            return copyAndSanitizeArray((JSONArray) value);
        } else {
            // string, null, number, boolean
            return value;
        }
    }

    private JSONObject copyAndSanitizeObject(JSONObject jsonObject) {
        Set<String> redactedKeySet = retrieveRedactedKeys(jsonObject);
        JSONObject result = new JSONObject();

        jsonObject.keySet().forEach(keyObject -> {
            String key = keyObject.toString();
            if (redactedKeySet.contains(key)) {
                result.accumulate(key, REDACT_VALUE);
            } else {
                Object value = jsonObject.get(keyObject);
                result.accumulate(key, copyAndSanitize(value));
            }
        });

        return result;
    }

    private JSONArray copyAndSanitizeArray(JSONArray jsonArray) {
        JSONArray result = new JSONArray();

        jsonArray.forEach(value ->
                result.add(copyAndSanitize(value))
        );

        return result;
    }
}
