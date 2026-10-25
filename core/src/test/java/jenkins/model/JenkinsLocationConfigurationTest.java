/*
 * The MIT License
 *
 * Copyright 2015 CloudBees Inc., Oleg Nenashev.
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

package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link JenkinsLocationConfiguration}.
 * @author Oleg Nenashev
 */
class JenkinsLocationConfigurationTest {

    private JenkinsLocationConfiguration config;

    @BeforeEach
    void setUp() {
        config = mock(JenkinsLocationConfiguration.class, Mockito.CALLS_REAL_METHODS);
        Answer<String> mockVoid = invocation -> "stub";
        Mockito.doAnswer(mockVoid).when(config).save();
        Mockito.doAnswer(mockVoid).when(config).save();
    }

    @Test
    void setAdminEmail() {
        final String email = "test@foo.bar";
        final String email2 = "test@bar.foo";

        // Assert the default value
        assertEquals(Messages.Mailer_Address_Not_Configured(), config.getAdminAddress());

        // Basic case
        config.setAdminAddress(email);
        assertEquals(email, config.getAdminAddress());

        // Quoted value
        config.setAdminAddress("\"" + email2 + "\"");
        assertEquals(email2, config.getAdminAddress());

        config.setAdminAddress("    test@foo.bar     ");
        assertEquals(email, config.getAdminAddress());
    }

    @Test
    @Issue("JENKINS-28419")
    void resetAdminEmail() {
        final String email = "test@foo.bar";

        // Set the e-mail
        config.setAdminAddress(email);
        assertEquals(email, config.getAdminAddress());

        // Reset it
        config.setAdminAddress(null);
        assertEquals(Messages.Mailer_Address_Not_Configured(), config.getAdminAddress());
    }
}
