/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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
package hudson.cli;

import static java.util.logging.Level.FINE;

import java.io.Console;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.trilead.ssh2.crypto.PEMDecoder;

/**
 * Read DSA or RSA key from file(s) asking for password interactively.
 *
 * @author ogondza
 * @since 1.556
 */
public class PrivateKeyProvider {

    private List<KeyPair> privateKeys = new ArrayList<KeyPair>();

    /**
     * Get keys read so far.
     *
     * @return Possibly empty list. Never null.
     */
    public List<KeyPair> getKeys() {
        return Collections.unmodifiableList(privateKeys);
    }

    public boolean hasKeys() {
        return !privateKeys.isEmpty();
    }

    /**
     * Read keys from default keyFiles
     *
     * <tt>.ssh/id_rsa</tt>, <tt>.ssh/id_dsa</tt> and <tt>.ssh/identity</tt>.
     *
     * @return true if some key was read successfully.
     */
    public boolean readFromDefaultLocations() {
        final File home = new File(System.getProperty("user.home"));

        boolean read = false;
        for (String path : new String[] {".ssh/id_rsa", ".ssh/id_dsa", ".ssh/identity"}) {
            final File key = new File(home, path);
            if (!key.exists()) continue;

            try {

                readFrom(key);
                read = true;
            } catch (IOException e) {

                LOGGER.log(FINE, "Failed to load " + key, e);
            } catch (GeneralSecurityException e) {

                LOGGER.log(FINE, "Failed to load " + key, e);
            }
        }

        return read;
    }

    /**
     * Read key from keyFile.
     */
    public void readFrom(File keyFile) throws IOException, GeneralSecurityException {
        final String password = isPemEncrypted(keyFile)
                ? askForPasswd(keyFile.getCanonicalPath())
                : null
        ;
        privateKeys.add(loadKey(keyFile, password));
    }

    private static boolean isPemEncrypted(File f) throws IOException{
        //simple check if the file is encrypted
        return readPemFile(f).contains("4,ENCRYPTED");
    }

    private static String askForPasswd(String filePath){
        Console cons = System.console();
        String passwd = null;
        if (cons != null){
            char[] p = cons.readPassword("%s", "Enter passphrase for " + filePath + ":");
            passwd = String.valueOf(p);
        }
        return passwd;
    }

    public static KeyPair loadKey(File f, String passwd) throws IOException, GeneralSecurityException {
        return loadKey(readPemFile(f), passwd);
    }

    private static String readPemFile(File f) throws IOException{
        FileInputStream is = new FileInputStream(f);
        try {
            DataInputStream dis = new DataInputStream(is);
            byte[] bytes = new byte[(int) f.length()];
            dis.readFully(bytes);
            dis.close();
            return new String(bytes);
        } finally {
            is.close();
        }
    }

    public static KeyPair loadKey(String pemString, String passwd) throws IOException, GeneralSecurityException {
        Object key = PEMDecoder.decode(pemString.toCharArray(), passwd);
        if (key instanceof com.trilead.ssh2.signature.RSAPrivateKey) {
            com.trilead.ssh2.signature.RSAPrivateKey x = (com.trilead.ssh2.signature.RSAPrivateKey)key;

            return x.toJCEKeyPair();
        }
        if (key instanceof com.trilead.ssh2.signature.DSAPrivateKey) {
            com.trilead.ssh2.signature.DSAPrivateKey x = (com.trilead.ssh2.signature.DSAPrivateKey)key;
            KeyFactory kf = KeyFactory.getInstance("DSA");

            return new KeyPair(
                    kf.generatePublic(new DSAPublicKeySpec(x.getY(), x.getP(), x.getQ(), x.getG())),
                    kf.generatePrivate(new DSAPrivateKeySpec(x.getX(), x.getP(), x.getQ(), x.getG())));
        }

        throw new UnsupportedOperationException("Unrecognizable key format: " + key);
    }

    private static final Logger LOGGER = Logger.getLogger(PrivateKeyProvider.class.getName());
}
