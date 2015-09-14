/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins.util;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * {@link JSONObject} response.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JSONObjectResponse implements HttpResponse {
    
    // TODO: Is there nothing like this for JSON already? I didn't find it.
    
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final JSONObject jsonObject;

    /**
     * Create an empty response.
     */
    public JSONObjectResponse() {
        this.jsonObject = new JSONObject();
        status("ok");
    }

    /**
     * Create a response containing the supplied "data".
     * @param data The data.
     */
    public JSONObjectResponse(@Nonnull JSONObject data) {
        this();
        this.jsonObject.put("data", data);
    }

    /**
     * Create a response containing the supplied "data".
     * @param data The data.
     */
    public JSONObjectResponse(@Nonnull Map<?,?> data) {
        this();
        this.jsonObject.put("data", JSONObject.fromObject(data));
    }

    /**
     * Set the response as an error response.
     * @param message The error "message" set on the response.
     * @return {@link this} object.
     */
    public @Nonnull JSONObjectResponse error(@Nonnull String message) {
        status("error");
        this.jsonObject.put("message", message);
        return this;
    }

    /**
     * Get the JSON response object.
     * @return The JSON response object.
     */
    @Nonnull JSONObject getJsonObject() {
        return jsonObject;
    }

    private @Nonnull JSONObjectResponse status(@Nonnull String status) {
        this.jsonObject.put("status", status);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        byte[] bytes = jsonObject.toString().getBytes(UTF8);
        rsp.setContentType("application/json; charset=UTF-8");
        rsp.setContentLength(bytes.length);
        rsp.getOutputStream().write(bytes);
    }
}
