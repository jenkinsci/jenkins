package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the BouncyCastle excludes configured in the shade {@code <filters>} in {@code pom.xml}.
 *
 * <p>BouncyCastle is bundled only to satisfy Apache MINA SSHD, and most of its post-quantum tree is
 * unreachable here, so the shade filter drops the algorithm families sshd never asks for. Nothing
 * else in the build would notice if that went wrong: sshd probes its post-quantum key exchanges
 * defensively and turns a missing class into {@code isSupported() == false}, so an over-eager
 * exclusion would silently downgrade every {@code -ssh} connection rather than fail.
 *
 * <p>This has to run against the shaded jar, not the module's ordinary test classpath, which still
 * carries the untrimmed {@code bcprov-jdk18on}. Hence the {@code IT} suffix: it runs after
 * {@code package}. {@link #relocatedClassesComeFromTheShadedJar()} pins that down rather than
 * assuming it.
 */
class ShadedJarCryptoIT {

    private static final String BC = "io.jenkins.cli.shaded.org.bouncycastle.";
    private static final String SSHD = "io.jenkins.cli.shaded.org.apache.sshd.";

    private static File shadedJar;

    @BeforeAll
    static void locateShadedJar() {
        String path = System.getProperty("shaded.cli.jar");
        assertNotNull(path, "shaded.cli.jar must point at the shaded uber jar");
        shadedJar = new File(path);
        assertTrue(shadedJar.isFile(), () -> "not a file: " + shadedJar);
    }

    /**
     * Everything below is only meaningful if the relocated classes really are the filtered ones we
     * ship, so check where they were loaded from before trusting any of it.
     */
    @Test
    void relocatedClassesComeFromTheShadedJar() throws Exception {
        URL source = load(SSHD + "common.util.security.SecurityUtils")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
        assertEquals(
                shadedJar.getCanonicalFile(),
                new File(source.toURI()).getCanonicalFile(),
                "relocated classes came from somewhere other than the shaded jar");
    }

    /**
     * {@code BouncyCastleProvider}'s constructor hard-references a {@code KeyFactorySpi} for every
     * post-quantum family, so excluding anything under {@code pqc/jcajce} stops the provider
     * constructing at all, which would cost us Ed25519 as well.
     */
    @Test
    void bouncyCastleProviderStillConstructs() throws Exception {
        assertEquals("BC", bouncyCastleProvider().getName());
    }

    /**
     * sshd 2.19 has no JDK-native EdDSA support, so BouncyCastle is the only thing standing between
     * us and users being unable to authenticate with {@code ~/.ssh/id_ed25519}.
     */
    @Test
    void ed25519SignsAndVerifies() throws Exception {
        Provider bc = bouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", bc);
        KeyPair keyPair = generator.generateKeyPair();

        byte[] message = "jenkins-cli".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519", bc);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519", bc);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);
        assertTrue(verifier.verify(signature), "Ed25519 signature did not verify");
    }

    @Test
    void sshdFindsBouncyCastleAndEd25519() throws Exception {
        Class<?> securityUtils = load(SSHD + "common.util.security.SecurityUtils");
        assertTrue(
                (Boolean) securityUtils.getMethod("isBouncyCastleRegistered").invoke(null),
                "sshd did not register BouncyCastle");
        assertTrue(
                (Boolean) securityUtils.getMethod("isEDDSACurveSupported").invoke(null),
                "sshd lost Ed25519 support");
    }

    /**
     * The load-bearing assertion. Every key exchange sshd knows about must still be usable, so that
     * a future sshd or BouncyCastle upgrade introducing an exchange backed by a family we excluded
     * fails here instead of quietly dropping off the negotiated list.
     */
    @Test
    void everyKeyExchangeSshdKnowsAboutIsStillSupported() throws Exception {
        Class<?> factories = load(SSHD + "common.kex.BuiltinDHFactories");
        List<String> unsupported = new ArrayList<>();
        for (Object factory : factories.getEnumConstants()) {
            if (!(Boolean) factories.getMethod("isSupported").invoke(factory)) {
                unsupported.add((String) factories.getMethod("getName").invoke(factory));
            }
        }
        assertEquals(
                List.of(),
                unsupported,
                "key exchanges lost their BouncyCastle backing; check the pqc excludes in pom.xml");
    }

    /**
     * {@code isSupported()} only reads a parameters constant, so exercise the primitives sshd
     * actually drives during a handshake: a full encapsulate/decapsulate has to agree on a secret.
     */
    @Test
    void sntrup761CompletesAKeyEncapsulationRoundTrip() throws Exception {
        Class<?> parameters = load(BC + "pqc.crypto.ntruprime.SNTRUPrimeParameters");
        Object keyGenerationParameters = load(BC + "pqc.crypto.ntruprime.SNTRUPrimeKeyGenerationParameters")
                .getConstructor(SecureRandom.class, parameters)
                .newInstance(new SecureRandom(), constant(parameters, "sntrup761"));

        assertSharedSecretAgrees(
                "sntrup761",
                keyGenerationParameters,
                BC + "pqc.crypto.ntruprime.SNTRUPrimeKeyPairGenerator",
                BC + "pqc.crypto.ntruprime.SNTRUPrimeKEMGenerator",
                BC + "pqc.crypto.ntruprime.SNTRUPrimeKEMExtractor",
                BC + "pqc.crypto.ntruprime.SNTRUPrimePrivateKeyParameters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ml_kem_768", "ml_kem_1024"})
    void mlKemCompletesAKeyEncapsulationRoundTrip(String parameterSet) throws Exception {
        Class<?> parameters = load(BC + "crypto.params.MLKEMParameters");
        Object keyGenerationParameters = load(BC + "crypto.params.MLKEMKeyGenerationParameters")
                .getConstructor(SecureRandom.class, parameters)
                .newInstance(new SecureRandom(), constant(parameters, parameterSet));

        assertSharedSecretAgrees(
                parameterSet,
                keyGenerationParameters,
                BC + "crypto.generators.MLKEMKeyPairGenerator",
                BC + "crypto.kems.MLKEMGenerator",
                BC + "crypto.kems.MLKEMExtractor",
                BC + "crypto.params.MLKEMPrivateKeyParameters");
    }

    private void assertSharedSecretAgrees(
            String label,
            Object keyGenerationParameters,
            String keyPairGenerator,
            String kemGenerator,
            String kemExtractor,
            String privateKeyParameters)
            throws Exception {
        Object generator = load(keyPairGenerator).getConstructor().newInstance();
        generator
                .getClass()
                .getMethod("init", load(BC + "crypto.KeyGenerationParameters"))
                .invoke(generator, keyGenerationParameters);
        Object keyPair = generator.getClass().getMethod("generateKeyPair").invoke(generator);
        Object publicKey = keyPair.getClass().getMethod("getPublic").invoke(keyPair);
        Object privateKey = keyPair.getClass().getMethod("getPrivate").invoke(keyPair);

        Object encapsulator = load(kemGenerator).getConstructor(SecureRandom.class).newInstance(new SecureRandom());
        Object encapsulated = encapsulator
                .getClass()
                .getMethod("generateEncapsulated", load(BC + "crypto.params.AsymmetricKeyParameter"))
                .invoke(encapsulator, publicKey);
        byte[] sent = (byte[]) encapsulated.getClass().getMethod("getSecret").invoke(encapsulated);
        byte[] ciphertext = (byte[]) encapsulated.getClass().getMethod("getEncapsulation").invoke(encapsulated);

        Object extractor = load(kemExtractor).getConstructor(load(privateKeyParameters)).newInstance(privateKey);
        byte[] received = (byte[]) extractor
                .getClass()
                .getMethod("extractSecret", byte[].class)
                .invoke(extractor, (Object) ciphertext);

        assertTrue(sent.length > 0, () -> label + " produced an empty shared secret");
        assertArrayEquals(sent, received, label + " shared secrets did not agree");
    }

    private Provider bouncyCastleProvider() throws Exception {
        return (Provider) load(BC + "jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor()
                .newInstance();
    }

    private static Object constant(Class<?> owner, String name) throws Exception {
        Field field = owner.getField(name);
        return field.get(null);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
}
