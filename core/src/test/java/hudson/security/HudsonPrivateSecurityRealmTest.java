package hudson.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.security.HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class HudsonPrivateSecurityRealmTest {

    // MySecurePassword
    private static final String PBKDF2_HMAC_SHA512_ENCODED_PASSWORD =
            "$HMACSHA512:1000:fe899fcfcef4302ec3f0d36164efefdc$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b";

    @Test
    public void testPBKDF2RegExp() {
        PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder();
        String encoded = encoder.encode("thisIsMyPassword");
        assertTrue(encoder.isHashValid(encoded));

        assertTrue(encoder.isHashValid(
                "$HMACSHA512:1000:f6865c02cc759fd061db0f3121a093e0$079bd3a0c2851248343584a9a4625360e9ebb13c36be49542268d2ebdbd1fb71f004db9ce7335a61885985e32e08cb20215ff7bf64b2af5792581039faa62b52"));

        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1:fe899fcfcef4302ec3f0d36164efefdc$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "not enough iterations");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:9999:fe899fcfcef4302ec3f0d36164efefdc$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "too many iterations");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1000:fe899fcfcef4302ec3f0d36164efef$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "salt too short");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "salt too long");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1000:f6865c02cc759fd061db0f3121a093e094$079bd3a0c2851248343584a9a4625360e9ebb13c36be49542268d2ebdbd1fb71f004db9ce7335a61885985e32e08cb20215f"),
                "hash result too short");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b42662"),
                "hash result too long");
        assertFalse(encoder.isHashValid(
                "$HMACSHA256:1000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b42662"),
                "wrong format");
        assertFalse(encoder.isHashValid(
                "::$sfdfssdf"),
                "wrong format");

    }

    @Test
    public void testPBKDF2PasswordMatching() {
        PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder();
        String encoded = encoder.encode("thisIsMyPassword");
        assertTrue(encoder.matches("thisIsMyPassword", encoded));
        assertFalse(encoder.matches("thisIsNotMyPassword", encoded));
    }

    @Test
    public void passwordPBKDF2WithMissingAgorithm() throws Exception {
        HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder pbkdf2PasswordEncoder = new HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder();
        try (var ignored = mockStatic(SecretKeyFactory.class)) {
            when(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")).thenThrow(NoSuchAlgorithmException.class);
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.encode("password"));

            assertTrue(pbkdf2PasswordEncoder.isHashValid(PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.matches("MySecurePassword", PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
        }
    }

    @Test
    public void passwordPBKDF2HashWithInvalidKeySpec() throws Exception {
        HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder pbkdf2PasswordEncoder = new HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder();
        try (var ignored = mockStatic(SecretKeyFactory.class)) {
            SecretKeyFactory skf = mock(SecretKeyFactory.class);
            when(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")).thenReturn(skf);
            when(skf.generateSecret(Mockito.any())).thenThrow(InvalidKeySpecException.class);
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.encode("password"));

            assertTrue(pbkdf2PasswordEncoder.isHashValid(PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.matches("MySecurePassword", PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
        }
    }
}
