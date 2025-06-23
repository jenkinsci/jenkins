/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.model;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Quick test for {@link UpdateCenter}.
 *
 * @author Kohsuke Kawaguchi
 */
class UpdateCenterTest {

    @Test
    void data() {
        try {
            doData("https://updates.jenkins.io/update-center.json?version=build");
            doData("https://updates.jenkins.io/stable/update-center.json?version=build");
        } catch (Exception x) {
            // TODO this should not be in core at all; should be in repo built by a separate job somewhere
            assumeTrue(false, "Might be no Internet connectivity, or might start failing due to expiring certificate through no fault of code changes: " + x);
        }
    }

    private void doData(String location) throws Exception {
        URL url = new URI(location).toURL();
        String jsonp = DownloadService.loadJSON(url);
        JSONObject json = JSONObject.fromObject(jsonp);

        UpdateSite us = new UpdateSite("default", url.toExternalForm());
        UpdateSite.Data data = us.new Data(json);
        assertThat(requireNonNull(data.core).url, startsWith("https://updates.jenkins.io/"));
        assertTrue(data.plugins.containsKey("rake"));
        System.out.println(data.core.url);

        // make sure the certificate is valid for a while more
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        JSONObject signature = json.getJSONObject("signature");
        for (Object cert : signature.getJSONArray("certificates")) {
            X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cert.toString().getBytes(StandardCharsets.UTF_8))));
            c.checkValidity(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)));
        }
    }
}
