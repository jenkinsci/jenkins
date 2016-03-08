/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.trilead.ssh2.crypto.Base64;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.Extension;
import hudson.node_monitors.ArchitectureMonitor.DescriptorImpl;
import hudson.util.IOUtils;
import hudson.util.Secret;
import static hudson.util.TimeUnit2.DAYS;
import static hudson.init.InitMilestone.COMPLETED;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.DataInputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import com.jcraft.jzlib.GZIPOutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UsageStatistics extends PageDecorator {
    private final String keyImage;

    /**
     * Lazily computed {@link PublicKey} representation of {@link #keyImage}.
     */
    private volatile transient RSAPublicKey key;

    /**
     * When was the last time we asked a browser to send the usage stats for us?
     */
    private volatile transient long lastAttempt = -1;

    public UsageStatistics() {
        this(DEFAULT_KEY_BYTES);
    }

    /**
     * Creates an instance with a specific public key image.
     */
    public UsageStatistics(String keyImage) {
        this.keyImage = keyImage;
        load();
    }

    /**
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        final Jenkins j = Jenkins.getInstanceOrNull();
        // user opted out or Jenkins not fully initialized. no data collection.
        if (j == null || j.isUsageStatisticsCollected() || DISABLED || COMPLETED.compareTo(j.getInitLevel()) > 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        if(now - lastAttempt > DAY) {
            lastAttempt = now;
            return true;
        }
        return false;
    }

    private RSAPublicKey getKey() {
        try {
            if (key == null) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                key = (RSAPublicKey)keyFactory.generatePublic(new X509EncodedKeySpec(Util.fromHexString(keyImage)));
            }
            return key;
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Gets the encrypted usage stat data to be sent to the Hudson server.
     */
    public String getStatData() throws IOException {
        Jenkins j = Jenkins.getInstance();

        JSONObject o = new JSONObject();
        o.put("stat",1);
        o.put("install", j.getLegacyInstanceId());
        o.put("servletContainer", j.servletContext.getServerInfo());
        o.put("version", Jenkins.VERSION);

        List<JSONObject> nodes = new ArrayList<JSONObject>();
        for( Computer c : j.getComputers() ) {
            JSONObject  n = new JSONObject();
            if(c.getNode()==j) {
                n.put("master",true);
                n.put("jvm-vendor", System.getProperty("java.vm.vendor"));
                n.put("jvm-name", System.getProperty("java.vm.name"));
                n.put("jvm-version", System.getProperty("java.version"));
            }
            n.put("executors",c.getNumExecutors());
            DescriptorImpl descriptor = j.getDescriptorByType(DescriptorImpl.class);
            n.put("os", descriptor.get(c));
            nodes.add(n);
        }
        o.put("nodes",nodes);

        List<JSONObject> plugins = new ArrayList<JSONObject>();
        for( PluginWrapper pw : j.getPluginManager().getPlugins() ) {
            if(!pw.isActive())  continue;   // treat disabled plugins as if they are uninstalled
            JSONObject p = new JSONObject();
            p.put("name",pw.getShortName());
            p.put("version",pw.getVersion());
            plugins.add(p);
        }
        o.put("plugins",plugins);

        JSONObject jobs = new JSONObject();
        List<TopLevelItem> items = j.getAllItems(TopLevelItem.class);
        for (TopLevelItemDescriptor d : Items.all()) {
            int cnt=0;
            for (TopLevelItem item : items) {
                if(item.getDescriptor()==d)
                    cnt++;
            }
            jobs.put(d.getJsonSafeClassName(),cnt);
        }
        o.put("jobs",jobs);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // json -> UTF-8 encode -> gzip -> encrypt -> base64 -> string
            OutputStreamWriter w = new OutputStreamWriter(new GZIPOutputStream(new CombinedCipherOutputStream(baos,getKey(),"AES")), "UTF-8");
            try {
                o.write(w);
            } finally {
                IOUtils.closeQuietly(w);
            }

            return new String(Base64.encode(baos.toByteArray()));
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            // for backward compatibility reasons, this configuration is stored in Jenkins
            Jenkins.getInstance().setNoUsageStatistics(json.has("usageStatisticsCollected") ? null : true);
            return true;
        } catch (IOException e) {
            throw new FormException(e,"usageStatisticsCollected");
        }
    }

    /**
     * Asymmetric cipher is slow and in case of Sun RSA implementation it can only encyrypt the first block.
     *
     * So first create a symmetric key, then place this key in the beginning of the stream by encrypting it
     * with the asymmetric cipher. The rest of the stream will be encrypted by a symmetric cipher.
     */
    public static final class CombinedCipherOutputStream extends FilterOutputStream {
        public CombinedCipherOutputStream(OutputStream out, Cipher asym, String algorithm) throws IOException, GeneralSecurityException {
            super(out);

            // create a new symmetric cipher key used for this stream
            String keyAlgorithm = getKeyAlgorithm(algorithm);
            SecretKey symKey = KeyGenerator.getInstance(keyAlgorithm).generateKey();

            // place the symmetric key by encrypting it with asymmetric cipher
            out.write(asym.doFinal(symKey.getEncoded()));

            // the rest of the data will be encrypted by this symmetric cipher
            Cipher sym = Secret.getCipher(algorithm);
            sym.init(Cipher.ENCRYPT_MODE,symKey, keyAlgorithm.equals(algorithm) ? null : new IvParameterSpec(symKey.getEncoded()));
            super.out = new CipherOutputStream(out,sym);
        }

        public CombinedCipherOutputStream(OutputStream out, RSAKey key, String algorithm) throws IOException, GeneralSecurityException {
            this(out,toCipher(key,Cipher.ENCRYPT_MODE),algorithm);
        }
    }

    /**
     * The opposite of the {@link CombinedCipherOutputStream}.
     */
    public static final class CombinedCipherInputStream extends FilterInputStream {
        /**
         * @param keyLength
         *      Block size of the asymmetric cipher, in bits. I thought I can get it from {@code asym.getBlockSize()}
         *      but that doesn't work with Sun's implementation.
         */
        public CombinedCipherInputStream(InputStream in, Cipher asym, String algorithm, int keyLength) throws IOException, GeneralSecurityException {
            super(in);

            String keyAlgorithm = getKeyAlgorithm(algorithm);

            // first read the symmetric key cipher
            byte[] symKeyBytes = new byte[keyLength/8];
            new DataInputStream(in).readFully(symKeyBytes);
            SecretKey symKey = new SecretKeySpec(asym.doFinal(symKeyBytes),keyAlgorithm);

            // the rest of the data will be decrypted by this symmetric cipher
            Cipher sym = Secret.getCipher(algorithm);
            sym.init(Cipher.DECRYPT_MODE,symKey, keyAlgorithm.equals(algorithm) ? null : new IvParameterSpec(symKey.getEncoded()));
            super.in = new CipherInputStream(in,sym);
        }

        public CombinedCipherInputStream(InputStream in, RSAKey key, String algorithm) throws IOException, GeneralSecurityException {
            this(in,toCipher(key,Cipher.DECRYPT_MODE),algorithm,key.getModulus().bitLength());
        }
    }

    private static String getKeyAlgorithm(String algorithm) {
        int index = algorithm.indexOf('/');
        return (index>0)?algorithm.substring(0,index):algorithm;
    }

    private static Cipher toCipher(RSAKey key, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(mode, (Key)key);
        return cipher;
    }

    /**
     * Public key to encrypt the usage statistics
     */
    private static final String DEFAULT_KEY_BYTES = "30819f300d06092a864886f70d010101050003818d0030818902818100c14970473bd90fd1f2d20e4fa6e36ea21f7d46db2f4104a3a8f2eb097d6e26278dfadf3fe9ed05bbbb00a4433f4b7151e6683a169182e6ff2f6b4f2bb6490b2cddef73148c37a2a7421fc75f99fb0fadab46f191806599a208652f4829fd6f76e13195fb81ff3f2fce15a8e9a85ebe15c07c90b34ebdb416bd119f0d74105f3b0203010001";

    private static final long DAY = DAYS.toMillis(1);

    public static boolean DISABLED = Boolean.getBoolean(UsageStatistics.class.getName()+".disabled");
}
