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

import com.google.common.io.Resources;
import com.trilead.ssh2.crypto.Base64;
import hudson.Util;
import hudson.model.UsageStatistics.CombinedCipherInputStream;
import hudson.node_monitors.ArchitectureMonitor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Kohsuke Kawaguchi
 */
public class UsageStatisticsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that the stat data can be decrypted safely.
     */
    @Test
    public void roundtrip() throws Exception {

        j.createOnlineSlave();
        warmUpNodeMonitorCache();

        // key pair for testing
        String privateKey = "30820276020100300d06092a864886f70d0101010500048202603082025c0201000281810084cababdb38040f659c2cb07a36d758f46e84ebc3d6ba39d967aedf1d396b0788ed3ab868d45ce280b1102b434c2a250ddc3254defe1785ab4f94d7038cf69ecca16753d2de3f6ad8976b3f74902d8634111d730982da74e1a6e3fc0bc3523bba53e45b8a8cbfd0321b94efc9f7fefbe66ad85281e3d0323d87f4426ec51204f0203010001028180784deaacdea8bd31f2d44578601954be3f714b93c2d977dbd76efb8f71303e249ad12dbeb2d2a1192a1d7923a6010768d7e06a3597b3df83de1d5688eb0f0e58c76070eddd696682730c93890dc727564c65dc8416bfbde5aad4eb7a97ed923efb55a291daf3c00810c0e43851298472fd539aab355af8cedcf1e9a0cbead661024100c498375102b068806c71dec838dc8dfa5624fb8a524a49cffadc19d10689a8c9c26db514faba6f96e50a605122abd3c9af16e82f2b7565f384528c9f31ea5947024100aceafd31d7f4872a873c7e5fe88f20c2fb086a053c6970026b3ce364768e2033100efb1ad8f2010fe53454a29decedc23a8a0c8df347742b1f13e11bd3a284b9024100931321470cd0f6cd24d4278bf8e61f9d69b6ef2bf3163a944aa340f91c7ffdf33aeea22b18cc43514af6714a21bb148d6cdca14530a8fa65acd7a8f62bfc9b5f024067452059f8438dc61466488336fce3f00ec483ad04db638dce45daf850e5a8cd5635dc39b87f2fab32940247ec5167ddabe06e870858104500967ac687aa73e102407e3b7997503e18d8d0f094d5e0bd5d57cb93cb39a2fc42cec1ea9a1562786438b61139e45813204d72c919f5397e139ad051d98e4d0f8a06d237f42c0d8440fb";
        String publicKey = "30819f300d06092a864886f70d010101050003818d003081890281810084cababdb38040f659c2cb07a36d758f46e84ebc3d6ba39d967aedf1d396b0788ed3ab868d45ce280b1102b434c2a250ddc3254defe1785ab4f94d7038cf69ecca16753d2de3f6ad8976b3f74902d8634111d730982da74e1a6e3fc0bc3523bba53e45b8a8cbfd0321b94efc9f7fefbe66ad85281e3d0323d87f4426ec51204f0203010001";

        String data = new UsageStatistics(publicKey).getStatData();

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey priv = (RSAPrivateKey)keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Util.fromHexString(privateKey)));

        byte[] cipherText = Base64.decode(data.toCharArray());
        InputStreamReader r = new InputStreamReader(new GZIPInputStream(
                new CombinedCipherInputStream(new ByteArrayInputStream(cipherText),priv,"AES")), "UTF-8");
        JSONObject o = JSONObject.fromObject(IOUtils.toString(r));
        Jenkins jenkins = Jenkins.getActiveInstance();
        // A bit intrusive with UsageStatistics internals, but done to prevent undetected changes
        // that would cause issues with parsing/analyzing uploaded usage statistics
        assertEquals(1, o.getInt("stat"));
        assertEquals(jenkins.getLegacyInstanceId(), o.getString("install"));
        assertEquals(jenkins.servletContext.getServerInfo(), o.getString("servletContainer"));
        assertEquals(Jenkins.VERSION, o.getString("version"));

        assertTrue(o.has("plugins"));
        assertTrue(o.has("jobs"));
        assertTrue(o.has("nodes"));

        // Compare content to watch out for backwards compatibility
        compareWithFile("plugins.json", sortPlugins((List<JSONObject>) o.get("plugins")));
        compareWithFile("jobs.json", sortJobTypes((JSONObject) o.get("jobs")));
        compareWithFile("nodes.json", o.get("nodes"));
    }

    /**
     * Node monitoring uses a cache for retrieved values. Querying it the first time will return null, while triggering
     * a background update of that cache.
     * <p/>
     * <p>This method triggers that update and waits until the cache is filled during roughly 1 second max.</p>
     *
     * @throws InterruptedException
     */
    private void warmUpNodeMonitorCache() throws InterruptedException {
        Jenkins j = Jenkins.getActiveInstance();
        ArchitectureMonitor.DescriptorImpl descriptor = j.getDescriptorByType(ArchitectureMonitor.DescriptorImpl.class);
        String value = null;
        int count = 1;
        while (value == null && count++ <= 5)  // If for some reason the cache doesn't get populated, don't loop forever
        {
            final Computer master = j.getComputers()[0];
            value = descriptor.get(master);
            Thread.sleep(200);
        }
    }

    // Plugins can be retrieved in any order, so sorting them so that the test is stable
    private Object sortPlugins(List<JSONObject> list) {
        List<JSONObject> sorted = new ArrayList<>(list);
        Collections.sort(sorted, new Comparator<JSONObject>() {
            public int compare(JSONObject j1, JSONObject j2) {
                return j1.getString("name").compareTo(j2.getString("name"));
            }
        });
        return sorted;
    }

    private JSONObject sortJobTypes(JSONObject object) {
        SortedSet<String> keys = new TreeSet(object.keySet());
        JSONObject sorted = new JSONObject();
        for (String key : keys) {
            sorted.put(key, object.get(key));
        }
        return sorted;
    }

    private void compareWithFile(String fileName, Object object) throws IOException {

        Class clazz = this.getClass();
        String fileContent = Resources.toString(clazz.getResource(clazz.getSimpleName() + "/" + fileName), Charset.forName("UTF-8"));
        fileContent = fileContent.replace("JVMVENDOR", System.getProperty("java.vendor"));
        fileContent = fileContent.replace("JVMNAME", System.getProperty("java.vm.name"));
        fileContent = fileContent.replace("JVMVERSION", System.getProperty("java.version"));
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        fileContent = fileContent.replace("OSSPEC", os + " (" + arch + ')');
        assertEquals(fileContent, object.toString());
    }
}
