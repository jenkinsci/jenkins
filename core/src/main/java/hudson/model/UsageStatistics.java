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

import static java.util.concurrent.TimeUnit.DAYS;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.node_monitors.ArchitectureMonitor;
import hudson.security.Permission;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jenkins.model.Jenkins;
import jenkins.security.FIPS140;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class UsageStatistics extends PageDecorator implements PersistentDescriptor {
    private static final Logger LOG = Logger.getLogger(UsageStatistics.class.getName());

    private final String keyImage;

    /**
     * Lazily computed {@link PublicKey} representation of {@link #keyImage}.
     */
    private transient volatile RSAPublicKey key;

    /**
     * When was the last time we asked a browser to send the usage stats for us?
     */
    private transient volatile long lastAttempt = -1;

    public UsageStatistics() {
        this(DEFAULT_KEY_BYTES);
    }

    /**
     * Creates an instance with a specific public key image.
     */
    public UsageStatistics(String keyImage) {
        this.keyImage = keyImage;
    }

    /**
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        if (!isEnabled()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttempt > DAY) {
            lastAttempt = now;
            return true;
        }
        return false;
    }

    /**
     * Returns whether between UI configuration, system property, and environment,
     * usage statistics should be submitted.
     *
     * @return true if and only if usage stats should be submitted
     * @since TODO
     */
    @Restricted(NoExternalUse.class)
    public static boolean isEnabled() {
        // user opted out (explicitly or FIPS is requested). no data collection
        return Jenkins.get().isUsageStatisticsCollected() && !DISABLED && !FIPS140.useCompliantAlgorithms();
    }

    private RSAPublicKey getKey() {
        try {
            if (key == null) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                key = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(Util.fromHexString(keyImage)));
            }
            return key;
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Gets the encrypted usage stat data to be sent to the Hudson server.
     * Used exclusively by jelly: resources/hudson/model/UsageStatistics/footer.jelly
     */
    public String getStatData() throws IOException {
        Jenkins j = Jenkins.get();

        JSONObject o = new JSONObject();
        o.put("stat", 1);
        o.put("install", j.getLegacyInstanceId());
        o.put("servletContainer", j.getServletContext().getServerInfo());
        o.put("version", Jenkins.VERSION);

        List<JSONObject> nodes = new ArrayList<>();
        for (Computer c : j.getComputers()) {
            JSONObject  n = new JSONObject();
            if (c.getNode() == j) {
                n.put("master", true);
                n.put("jvm-vendor", System.getProperty("java.vm.vendor"));
                n.put("jvm-name", System.getProperty("java.vm.name"));
                n.put("jvm-version", System.getProperty("java.version"));
            }
            n.put("executors", c.getNumExecutors());
            ArchitectureMonitor.DescriptorImpl descriptor = j.getDescriptorByType(ArchitectureMonitor.DescriptorImpl.class);
            n.put("os", descriptor.get(c));
            nodes.add(n);
        }
        o.put("nodes", nodes);

        List<JSONObject> plugins = new ArrayList<>();
        for (PluginWrapper pw : j.getPluginManager().getPlugins()) {
            if (!pw.isActive())  continue;   // treat disabled plugins as if they are uninstalled
            JSONObject p = new JSONObject();
            p.put("name", pw.getShortName());
            p.put("version", pw.getVersion());
            plugins.add(p);
        }
        o.put("plugins", plugins);

        JSONObject jobs = new JSONObject();
        // capture the descriptors as these should be small compared with the number of items
        // so we will walk all items only once and we can short-cut the search of descriptors
        TopLevelItemDescriptor[] descriptors = Items.all().toArray(new TopLevelItemDescriptor[0]);
        int[] counts = new int[descriptors.length];
        for (TopLevelItem item : j.allItems(TopLevelItem.class)) {
            TopLevelItemDescriptor d = item.getDescriptor();
            for (int i = 0; i < descriptors.length; i++) {
                if (d == descriptors[i]) {
                    counts[i]++;
                    // no point checking any more, we found the match
                    break;
                }
            }
        }
        for (int i = 0; i < descriptors.length; i++) {
            jobs.put(descriptors[i].getJsonSafeClassName(), counts[i]);
        }
        o.put("jobs", jobs);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // json -> UTF-8 encode -> gzip -> encrypt -> base64 -> string
            try (OutputStream cipheros = new CombinedCipherOutputStream(baos, getKey(), "AES");
                 OutputStream zipos = new GZIPOutputStream(cipheros);
                 OutputStreamWriter w = new OutputStreamWriter(zipos, StandardCharsets.UTF_8)) {
                o.write(w);
            }

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Throwable e) { // the exception could be GeneralSecurityException, InvalidParameterException or any other depending on the security provider you have installed
            LOG.log(Level.INFO, "Usage statistics could not be sent ({0})", e.getMessage());
            LOG.log(Level.FINE, "Error sending usage statistics", e);
            return null;
        }
    }

    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.MANAGE;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            // for backward compatibility reasons, this configuration is stored in Jenkins
            if (DISABLED) {
                Jenkins.get().setNoUsageStatistics(Boolean.TRUE);
            } else {
                Jenkins.get().setNoUsageStatistics(json.has("usageStatisticsCollected") ? null : Boolean.TRUE);
            }
            return true;
        } catch (IOException e) {
            throw new FormException(e, "usageStatisticsCollected");
        }
    }

    /**
     * Asymmetric cipher is slow and in case of Sun RSA implementation it can only encrypt the first block.
     *
     * So first create a symmetric key, then place this key in the beginning of the stream by encrypting it
     * with the asymmetric cipher. The rest of the stream will be encrypted by a symmetric cipher.
     */
    public static final class CombinedCipherOutputStream extends FilterOutputStream {
        @SuppressFBWarnings(value = "STATIC_IV", justification = "TODO needs triage")
        public CombinedCipherOutputStream(OutputStream out, Cipher asym, String algorithm) throws IOException, GeneralSecurityException {
            super(out);

            // create a new symmetric cipher key used for this stream
            String keyAlgorithm = getKeyAlgorithm(algorithm);
            SecretKey symKey = KeyGenerator.getInstance(keyAlgorithm).generateKey();

            // place the symmetric key by encrypting it with asymmetric cipher
            out.write(asym.doFinal(symKey.getEncoded()));

            // the rest of the data will be encrypted by this symmetric cipher
            Cipher sym = Secret.getCipher(algorithm);
            sym.init(Cipher.ENCRYPT_MODE, symKey, keyAlgorithm.equals(algorithm) ? null : new IvParameterSpec(symKey.getEncoded()));
            super.out = new CipherOutputStream(out, sym);
        }

        public CombinedCipherOutputStream(OutputStream out, RSAKey key, String algorithm) throws IOException, GeneralSecurityException {
            this(out, toCipher(key, Cipher.ENCRYPT_MODE), algorithm);
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
            byte[] symKeyBytes = new byte[keyLength / 8];
            new DataInputStream(in).readFully(symKeyBytes);
            SecretKey symKey = new SecretKeySpec(asym.doFinal(symKeyBytes), keyAlgorithm);

            // the rest of the data will be decrypted by this symmetric cipher
            Cipher sym = Secret.getCipher(algorithm);
            sym.init(Cipher.DECRYPT_MODE, symKey, keyAlgorithm.equals(algorithm) ? null : new IvParameterSpec(symKey.getEncoded()));
            super.in = new CipherInputStream(in, sym);
        }

        public CombinedCipherInputStream(InputStream in, RSAKey key, String algorithm) throws IOException, GeneralSecurityException {
            this(in, toCipher(key, Cipher.DECRYPT_MODE), algorithm, key.getModulus().bitLength());
        }
    }

    private static String getKeyAlgorithm(String algorithm) {
        int index = algorithm.indexOf('/');
        return index > 0 ? algorithm.substring(0, index) : algorithm;
    }

    private static Cipher toCipher(RSAKey key, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(mode, (Key) key);
        return cipher;
    }

    /**
     * Public key to encrypt the usage statistics
     */
    private static final String DEFAULT_KEY_BYTES =
            "30819f300d06092a864886f70d010101050003818d0030818902818100c14970473bd90fd1f2d20e"
                + "4fa6e36ea21f7d46db2f4104a3a8f2eb097d6e26278dfadf3fe9ed05bbbb00a4433f4b7151e6683a"
                + "169182e6ff2f6b4f2bb6490b2cddef73148c37a2a7421fc75f99fb0fadab46f191806599a208652f"
                + "4829fd6f76e13195fb81ff3f2fce15a8e9a85ebe15c07c90b34ebdb416bd119f0d74105f3b020301"
                + "0001";

    private static final long DAY = DAYS.toMillis(1);

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean DISABLED = SystemProperties.getBoolean(UsageStatistics.class.getName() + ".disabled");
}
