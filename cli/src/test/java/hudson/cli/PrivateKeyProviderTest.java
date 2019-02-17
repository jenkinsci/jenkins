package hudson.cli;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.Assert.assertNotNull;

public class PrivateKeyProviderTest {

    @Test
    public void loadKeyDSA() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("dsa").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    @Test
    public void loadKeyDSAPassword() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("dsa-password").getFile());
        String password = "password";
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }
    
    @Test
    public void loadKeyRSA() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("rsa").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    @Test
    public void loadKeyRSAPassword() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("rsa-password").getFile());
        String password = "password";
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    @Test
    public void loadKeyOpenSSH() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    @Test(expected = java.security.NoSuchAlgorithmException.class)
    public void loadKeyUnsupportedCipher() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh-unsuported").getFile());
        String password = "password";
        PrivateKeyProvider.loadKey(file, password);
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void loadKeyBroken() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh-broken").getFile());
        String password = "password";
        PrivateKeyProvider.loadKey(file, password);
    }
}