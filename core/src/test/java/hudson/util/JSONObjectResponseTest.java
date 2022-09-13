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

package hudson.util;

import java.util.HashMap;
import java.util.Map;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JSONObjectResponseTest {

    @Test
    public void test() {
        Map<String, String> data = new HashMap<>();

        data.put("val_1", "1");

        HttpResponses.JSONObjectResponse response = new HttpResponses.JSONObjectResponse(data);
        JSONObject payload = response.getJsonObject();

        Assert.assertEquals("ok", payload.getString("status"));
        JSONObject payloadData = payload.getJSONObject("data");
        Assert.assertEquals("1", payloadData.getString("val_1"));

        // change it to an error
        response.error("a message");
        Assert.assertEquals("error", payload.getString("status"));
        Assert.assertEquals("a message", payload.getString("message"));
    }
}
