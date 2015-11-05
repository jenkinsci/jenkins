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

import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.util.Arrays;

import org.hamcrest.Description;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CLI.class) // When mocking new operator caller has to be @PreparedForTest, not class itself
public class PrivateKeyProviderTest {

    @Test
    public void specifyKeysExplicitly() throws Exception {
        final CLI cli = fakeCLI();

        final File dsaKey = keyFile(".ssh/id_dsa");
        final File rsaKey = keyFile(".ssh/id_rsa");

        run("-i", dsaKey.getAbsolutePath(), "-i", rsaKey.getAbsolutePath(), "-s", "http://example.com");

        verify(cli).authenticate(withKeyPairs(
                keyPair(dsaKey),
                keyPair(rsaKey)
        ));
    }

    @Test
    public void useDefaultKeyLocations() throws Exception {
        final CLI cli = fakeCLI();

        final File rsaKey = keyFile(".ssh/id_rsa");
        final File dsaKey = keyFile(".ssh/id_dsa");

        fakeHome();
        run("-s", "http://example.com");

        verify(cli).authenticate(withKeyPairs(
                keyPair(rsaKey),
                keyPair(dsaKey)
        ));
    }

    private CLI fakeCLI() throws Exception {
        final CLI cli = mock(CLI.class);

        final CLIConnectionFactory factory = mock(CLIConnectionFactory.class, Mockito.CALLS_REAL_METHODS);
        factory.jenkins = new URL("http://example.com");
        doReturn(cli).when(factory).connect();

        mockStatic(CLIConnectionFactory.class);
        whenNew(CLIConnectionFactory.class).withNoArguments().thenReturn(factory);

        return cli;
    }

    private void fakeHome() throws URISyntaxException {
        final File home = new File(this.getClass().getResource(".ssh").toURI()).getParentFile();
        System.setProperty("user.home", home.getAbsolutePath());
    }

    private int run(String... args) throws Exception {
        return CLI._main(args);
    }

    private File keyFile(String name) throws URISyntaxException {
        return new File(this.getClass().getResource(name).toURI());
    }

    private KeyPair keyPair(File file) throws IOException, GeneralSecurityException {
        return PrivateKeyProvider.loadKey(file, null);
    }

    private Iterable<KeyPair> withKeyPairs(final KeyPair... expected) {
        return Mockito.argThat(new ArgumentMatcher<Iterable<KeyPair>>() {
            @Override public void describeTo(Description description) {
                description.appendText(Arrays.asList(expected).toString());
            }

            @Override public boolean matches(Object argument) {
                if (!(argument instanceof Iterable)) throw new IllegalArgumentException("Not an instance of Iterrable");

                @SuppressWarnings("unchecked")
                final Iterable<KeyPair> actual = (Iterable<KeyPair>) argument;
                int i = 0;
                for (KeyPair akp: actual) {
                    if (!eq(expected[i].getPublic(), akp.getPublic())) return false;
                    if (!eq(expected[i].getPrivate(), akp.getPrivate())) return false;
                    i++;
                }

                return i == expected.length;
            }

            private boolean eq(final Key expected, final Key actual) {
                return Arrays.equals(expected.getEncoded(), actual.getEncoded());
            }
        });
    }
}
