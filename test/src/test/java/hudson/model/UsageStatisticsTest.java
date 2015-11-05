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

import static org.junit.Assert.assertEquals;

import com.trilead.ssh2.crypto.Base64;
import hudson.Util;
import hudson.model.UsageStatistics.CombinedCipherInputStream;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.zip.GZIPInputStream;

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
        // key pair for testing
        String privateKey = "30820276020100300d06092a864886f70d0101010500048202603082025c0201000281810084cababdb38040f659c2cb07a36d758f46e84ebc3d6ba39d967aedf1d396b0788ed3ab868d45ce280b1102b434c2a250ddc3254defe1785ab4f94d7038cf69ecca16753d2de3f6ad8976b3f74902d8634111d730982da74e1a6e3fc0bc3523bba53e45b8a8cbfd0321b94efc9f7fefbe66ad85281e3d0323d87f4426ec51204f0203010001028180784deaacdea8bd31f2d44578601954be3f714b93c2d977dbd76efb8f71303e249ad12dbeb2d2a1192a1d7923a6010768d7e06a3597b3df83de1d5688eb0f0e58c76070eddd696682730c93890dc727564c65dc8416bfbde5aad4eb7a97ed923efb55a291daf3c00810c0e43851298472fd539aab355af8cedcf1e9a0cbead661024100c498375102b068806c71dec838dc8dfa5624fb8a524a49cffadc19d10689a8c9c26db514faba6f96e50a605122abd3c9af16e82f2b7565f384528c9f31ea5947024100aceafd31d7f4872a873c7e5fe88f20c2fb086a053c6970026b3ce364768e2033100efb1ad8f2010fe53454a29decedc23a8a0c8df347742b1f13e11bd3a284b9024100931321470cd0f6cd24d4278bf8e61f9d69b6ef2bf3163a944aa340f91c7ffdf33aeea22b18cc43514af6714a21bb148d6cdca14530a8fa65acd7a8f62bfc9b5f024067452059f8438dc61466488336fce3f00ec483ad04db638dce45daf850e5a8cd5635dc39b87f2fab32940247ec5167ddabe06e870858104500967ac687aa73e102407e3b7997503e18d8d0f094d5e0bd5d57cb93cb39a2fc42cec1ea9a1562786438b61139e45813204d72c919f5397e139ad051d98e4d0f8a06d237f42c0d8440fb";
        String publicKey = "30819f300d06092a864886f70d010101050003818d003081890281810084cababdb38040f659c2cb07a36d758f46e84ebc3d6ba39d967aedf1d396b0788ed3ab868d45ce280b1102b434c2a250ddc3254defe1785ab4f94d7038cf69ecca16753d2de3f6ad8976b3f74902d8634111d730982da74e1a6e3fc0bc3523bba53e45b8a8cbfd0321b94efc9f7fefbe66ad85281e3d0323d87f4426ec51204f0203010001";

        String data = new UsageStatistics(publicKey).getStatData();
        System.out.println(data);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey priv = (RSAPrivateKey)keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Util.fromHexString(privateKey)));

        byte[] cipherText = Base64.decode(data.toCharArray());
        InputStreamReader r = new InputStreamReader(new GZIPInputStream(
                new CombinedCipherInputStream(new ByteArrayInputStream(cipherText),priv,"AES")), "UTF-8");
        JSONObject o = JSONObject.fromObject(IOUtils.toString(r));
        System.out.println(o);
        assertEquals(1,o.getInt("stat"));
    }
}
