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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.Console;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;

/**
 * Read DSA or RSA key from file(s) asking for password interactively.
 *
 * @author ogondza
 * @since 1.556
 */
public class PrivateKeyProvider {

    private List<KeyPair> privateKeys = new ArrayList<>();

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
     * {@code .ssh/id_rsa}, {@code .ssh/id_dsa} and {@code .ssh/identity}.
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
            } catch (IOException | GeneralSecurityException e) {

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
        try (InputStream is = Files.newInputStream(f.toPath());
             DataInputStream dis = new DataInputStream(is)) {
            byte[] bytes = new byte[(int) f.length()];
            dis.readFully(bytes);
            return new String(bytes);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    public static KeyPair loadKey(String pemString, String passwd) throws IOException, GeneralSecurityException {
        return SecurityUtils.loadKeyPairIdentity("key",
                new ByteArrayInputStream(pemString.getBytes(UTF_8)),
                FilePasswordProvider.of(passwd));
    }

    private static final Logger LOGGER = Logger.getLogger(PrivateKeyProvider.class.getName());
}
