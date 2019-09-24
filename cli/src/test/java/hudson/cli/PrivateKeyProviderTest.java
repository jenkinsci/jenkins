package hudson.cli;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
keys were generated with ssh-keygen from OpenSSH_7.9p1, LibreSSL 2.7.3
*/
@Execution(ExecutionMode.CONCURRENT)
public class PrivateKeyProviderTest {

    /**
    key command: ssh-keygen -f dsa -t dsa -b 1024 -m PEM
    */
    @Test
    public void loadKeyDSA() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("dsa").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    /**
    key command: ssh-keygen -f dsa-password -t dsa -b 1024 -m PEM -p password
    */
    @Test
    public void loadKeyDSAPassword() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("dsa-password").getFile());
        String password = "password";
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }
    
    /**
    key command: ssh-keygen -f rsa -t rsa -b 1024 -m PEM
    */
    @Test
    public void loadKeyRSA() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("rsa").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }

    /**
    key command: ssh-keygen -f rsa-password -t rsa -b 1024 -m PEM -p password
    */
    @Test
    public void loadKeyRSAPassword() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("rsa-password").getFile());
        String password = "password";
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }
    
    /**
    key command: ssh-keygen -f openssh -t rsa -b 1024
    */
    @Test
    public void loadKeyOpenSSH() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh").getFile());
        String password = null;
        KeyPair keyPair = PrivateKeyProvider.loadKey(file, password);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
    }
    
    /**
    key command: ssh-keygen -f openssh-unsupported -t rsa -b 1024 -p password
    */
    @Test
    public void loadKeyUnsupportedCipher() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh-unsuported").getFile());
        String password = "password";
        assertThrows(NoSuchAlgorithmException.class, () -> PrivateKeyProvider.loadKey(file, password));
    }

    /**
    key command: ssh-keygen -f openssh -t rsa -b 1024
    in this key we remove some lines to break the key.
    */    
    @Test
    public void loadKeyBroken() throws IOException, GeneralSecurityException {
        File file = new File(this.getClass().getResource("openssh-broken").getFile());
        String password = "password";
        assertThrows(IllegalArgumentException.class, () -> PrivateKeyProvider.loadKey(file, password));
    }
}
