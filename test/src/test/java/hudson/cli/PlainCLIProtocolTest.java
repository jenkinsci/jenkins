package hudson.cli;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.LoggerRule;

public class PlainCLIProtocolTest {

    @Rule public LoggerRule logger = new LoggerRule().record(PlainCLIProtocol.class, Level.FINE).capture(50);

    @Test
    public void streamCorruption() throws Exception {
        final PipedOutputStream upload = new PipedOutputStream();
        final PipedOutputStream download = new PipedOutputStream();

        class Server extends PlainCLIProtocol.ServerSide {
            Server() throws IOException {
                super(new PlainCLIProtocol.FramedOutput(download));
            }

            @Override
            protected void onArg(String text) {}

            @Override
            protected void onLocale(String text) {}

            @Override
            protected void onEncoding(String text) {}

            @Override
            protected synchronized void onStart() {}

            @Override
            protected void onStdin(byte[] chunk) throws IOException {}

            @Override
            protected void onEndStdin() throws IOException {}

            @Override
            protected void handleClose() {}
        }

        Server server = new Server();
        new PlainCLIProtocol.FramedReader(server, new PipedInputStream(upload)).start();
        // Trigger corruption
        upload.write(0xFF);
        upload.write(0xFF);
        upload.write(0xFF);
        upload.write(0xFF);
        upload.flush();
        await().until(logger::getMessages, hasItem(containsString("Read back: 0xff 0xff 0xff 0xff")));
    }
}
