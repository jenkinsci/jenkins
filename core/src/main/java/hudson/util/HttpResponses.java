/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Various {@link HttpResponse} implementations.
 *
 * <p>
 * This class extends from Stapler so that we can move implementations from here to Stapler periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class HttpResponses extends org.kohsuke.stapler.HttpResponses {
    public static HttpResponse staticResource(File f) throws IOException {
        return staticResource(f.toURI().toURL());
    }

    /**
     * Create an empty "ok" response.
     *
     * @since 2.0
     */
    public static HttpResponse okJSON() {
        return new JSONObjectResponse();
    }

    /**
     * Create a response containing the supplied "data".
     * @param data The data.
     *
     * @since 2.0
     */
    public static HttpResponse okJSON(@NonNull JSONObject data) {
        return new JSONObjectResponse(data);
    }

    /**
     * Create a response containing the supplied "data".
     * @param data The data.
     *
     * @since 2.0
     */
    public static HttpResponse okJSON(@NonNull JSONArray data) {
        return new JSONObjectResponse(data);
    }

    /**
     * Create a response containing the supplied "data".
     * @param data The data.
     *
     * @since 2.0
     */
    public static HttpResponse okJSON(@NonNull Map<?, ?> data) {
        return new JSONObjectResponse(data);
    }

    /**
     * Set the response as an error response.
     * @param message The error "message" set on the response.
     * @return {@code this} object.
     *
     * @since 2.0
     */
    public static HttpResponse errorJSON(@NonNull String message) {
        return new JSONObjectResponse().error(message);
    }

    /**
     * Set the response as an error response plus some data.
     * @param message The error "message" set on the response.
     * @param data The data.
     * @return {@code this} object.
     *
     * @since 2.119
     */
    public static HttpResponse errorJSON(@NonNull String message, @NonNull Map<?, ?> data) {
        return new JSONObjectResponse(data).error(message);
    }

    /**
     * Set the response as an error response plus some data.
     * @param message The error "message" set on the response.
     * @param data The data.
     * @return {@code this} object.
     *
     * @since 2.115
     */
    public static HttpResponse errorJSON(@NonNull String message, @NonNull JSONObject data) {
        return new JSONObjectResponse(data).error(message);
    }

    /**
     * Set the response as an error response plus some data.
     * @param message The error "message" set on the response.
     * @param data The data.
     * @return {@code this} object.
     *
     * @since 2.115
     */
    public static HttpResponse errorJSON(@NonNull String message, @NonNull JSONArray data) {
        return new JSONObjectResponse(data).error(message);
    }

    /**
     * {@link net.sf.json.JSONObject} response.
     *
     * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
     */
    static class JSONObjectResponse implements HttpResponse {

        private final JSONObject jsonObject;

        /**
         * Create an empty "ok" response.
         */
        JSONObjectResponse() {
            this.jsonObject = new JSONObject();
            status("ok");
        }

        /**
         * Create a response containing the supplied "data".
         * @param data The data.
         */
        JSONObjectResponse(@NonNull JSONObject data) {
            this();
            this.jsonObject.put("data", data);
        }

        /**
         * Create a response containing the supplied "data".
         * @param data The data.
         */
        JSONObjectResponse(@NonNull JSONArray data) {
            this();
            this.jsonObject.put("data", data);
        }

        /**
         * Create a response containing the supplied "data".
         * @param data The data.
         */
        JSONObjectResponse(@NonNull Map<?, ?> data) {
            this();
            this.jsonObject.put("data", JSONObject.fromObject(data));
        }

        /**
         * Set the response as an error response.
         * @param message The error "message" set on the response.
         * @return {@link this} object.
         */
        @NonNull JSONObjectResponse error(@NonNull String message) {
            status("error");
            this.jsonObject.put("message", message);
            return this;
        }

        /**
         * Get the JSON response object.
         * @return The JSON response object.
         */
        @NonNull JSONObject getJsonObject() {
            return jsonObject;
        }

        private @NonNull JSONObjectResponse status(@NonNull String status) {
            this.jsonObject.put("status", status);
            return this;
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            byte[] bytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
            rsp.setContentType("application/json; charset=UTF-8");
            rsp.setContentLength(bytes.length);
            rsp.getOutputStream().write(bytes);
        }
    }
}
