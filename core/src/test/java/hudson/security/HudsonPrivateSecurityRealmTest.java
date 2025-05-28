package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.security.HudsonPrivateSecurityRealm.JBCryptEncoder;
import hudson.security.HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import javax.crypto.SecretKeyFactory;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

public class HudsonPrivateSecurityRealmTest {

    // MySecurePassword
    private static final String PBKDF2_HMAC_SHA512_ENCODED_PASSWORD =
            "$HMACSHA512:210000:30f9e0a5470a8bc67f128ca1aae25dd4$88abaca4f442caeff0096ec0f75df2d77cc31a956c564133232f4d2532a72c8d4380a718d5b2a3dccab9e752027eeadd8f9f2c0c624505531bf3a57ec7d08aad";

    /*
     * This exists so that we can easily  check the complexity of how long this takes (ie is the number of iterations we
     * use correct for the state of CPUs).
     * We do not want to assert that the range < x and  > y  as that would make the test flaky on overloaded
     * or slow hardware, so this is commented out but left for ease of running locally when desired.
     */
    //@Test
    public void timingPBKDF2() {
        // ignore the salt generation - check just matching....
        PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder();
        String encoded = encoder.encode("thisIsMyPassword1");

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            System.out.println(encoder.matches("thisIsMyPassword" + i, encoded));
        }
        long end = System.nanoTime();
        long duration = end - start;
        long duration_per_iteration = duration / 10;

        Duration d = Duration.ofNanos(duration_per_iteration);
        System.out.println("PBKDF2 took " + d.toNanos() + "ns");
        System.out.println("PBKDF2 took " + d.toMillis() + "ms");
        System.out.println("PBKDF2 took " + d.toSeconds() + "s");
    }

    /*
     * This exists so that we can easily  check the complexity of how long this takes (ie is the number of iterations we
     * use correct for the state of CPUs).
     * We do not want to assert that the range < x and  > y  as that would make the test flaky on overloaded
     * or slow hardware, so this is commented out but left for ease of running locally when desired.
     */
    //@Test
    public void timingJBCrypt() {
        // ignore the salt generation - check just matching....
        JBCryptEncoder encoder = new JBCryptEncoder();
        String encoded = encoder.encode("thisIsMyPassword1");

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            System.out.println(encoder.matches("thisIsMyPassword" + i, encoded));
        }
        long end = System.nanoTime();
        long duration = end - start;
        long duration_per_iteration = duration / 10;

        Duration d = Duration.ofNanos(duration_per_iteration);
        System.out.println("BCrypt took " + d.toNanos() + "ns");
        System.out.println("BCrypt took " + d.toMillis() + "ms");
        System.out.println("BCrypt took " + d.toSeconds() + "s");
    }

    @Test
    void testPBKDF2RegExp() {
        PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder();
        String encoded = encoder.encode("thisIsMyPassword");
        assertTrue(encoder.isHashValid(encoded));

        // and a static one for other tests...
        assertTrue(encoder.isHashValid(PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));

        assertFalse(encoder.isHashValid(
                "$HMACSHA512:1000:fe899fcfcef4302ec3f0d36164efefdc$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "not enough iterations");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:500000:fe899fcfcef4302ec3f0d36164efefdc$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "too many iterations");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:210000:fe899fcfcef4302ec3f0d36164efef$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "salt too short");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:210000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b"),
                "salt too long");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:210000:f6865c02cc759fd061db0f3121a093e094$079bd3a0c2851248343584a9a4625360e9ebb13c36be49542268d2ebdbd1fb71f004db9ce7335a61885985e32e08cb20215f"),
                "hash result too short");
        assertFalse(encoder.isHashValid(
                "$HMACSHA512:210000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b42662"),
                "hash result too long");
        assertFalse(encoder.isHashValid(
                "$HMACSHA256:210000:f6865c02cc759fd061db0f3121a093e094$0781364eae9dac4ef1c4c3bf34c28e13965b46105fec0b6fcf4bae78e246fb5e51a1694fff19acac2dfb37b16055092644f3682c25beea9a7a286bf94e52f63b42662"),
                "wrong format");
        assertFalse(encoder.isHashValid(
                "::$sfdfssdf"),
                "wrong format");

    }

    @Test
    void testPBKDF2PasswordMatching() {
        PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder();
        String encoded = encoder.encode("thisIsMyPassword");
        assertTrue(encoder.matches("thisIsMyPassword", encoded));
        assertFalse(encoder.matches("thisIsNotMyPassword", encoded));
    }

    @Test
    void passwordPBKDF2WithMissingAgorithm() throws Exception {
        HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder pbkdf2PasswordEncoder = new HudsonPrivateSecurityRealm.PBKDF2PasswordEncoder();
        try (var ignored = mockStatic(SecretKeyFactory.class)) {
            when(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")).thenThrow(NoSuchAlgorithmException.class);
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.encode("password"));

            assertTrue(pbkdf2PasswordEncoder.isHashValid(PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
            assertThrows(RuntimeException.class, () -> pbkdf2PasswordEncoder.matches("MySecurePassword", PBKDF2_HMAC_SHA512_ENCODED_PASSWORD));
        }
    }

    @Test
    void passwordPBKDF2HashWithInvalidKeySpec() throws Exception {
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

    @Test
    void testJBCryptPasswordMatching() {
        JBCryptEncoder encoder = new JBCryptEncoder();
        String encoded = encoder.encode("thisIsMyPassword");
        assertTrue(encoder.matches("thisIsMyPassword", encoded));
        assertFalse(encoder.matches("thisIsNotMyPassword", encoded));
    }

    @Issue("JENKINS-75533")
    public void ensureExpectedMessageAscii() {
        final IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.encode("1234567890123456789012345678901234567890123456789012345678901234567890123"));
        assertThat(ex.getMessage(), is(Messages.HudsonPrivateSecurityRealm_CreateAccount_BCrypt_PasswordTooLong_ASCII()));
    }

    @Issue("JENKINS-75533")
    public void ensureExpectedMessageEmoji() {
        final IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.encode(
                "\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20" +
                        "\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20\uD83E\uDD20")); // 🤠
        assertThat(ex.getMessage(), is(Messages.HudsonPrivateSecurityRealm_CreateAccount_BCrypt_PasswordTooLong()));
    }
}
