package hudson.cli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CLIConnectionFactoryTest {

    CLIConnectionFactory cliFactory;

    @BeforeEach
    void setUp() {
        cliFactory = new CLIConnectionFactory();
    }

    @Test
    void testBearerFromToken() {
        Assertions.assertEquals("Bearer some-token", cliFactory.bearerAuth("some-token").authorization);
    }

    @Test
    void testBasicFromUserAndPass() {
        Assertions.assertEquals("Basic c29tZTpwYXNz", cliFactory.basicAuth("some:pass").authorization);
    }
}
