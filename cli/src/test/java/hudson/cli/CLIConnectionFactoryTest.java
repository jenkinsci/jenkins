package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CLIConnectionFactoryTest {

    private CLIConnectionFactory cliFactory;

    @BeforeEach
    void setUp() {
        cliFactory = new CLIConnectionFactory();
    }

    @Test
    void testBearerFromToken() {
        assertEquals("Bearer some-token", cliFactory.bearerAuth("some-token").authorization);
    }

    @Test
    void testBasicFromUserAndPass() {
        assertEquals("Basic c29tZTpwYXNz", cliFactory.basicAuth("some:pass").authorization);
    }
}
