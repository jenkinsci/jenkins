package hudson.cli;

import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Mode;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * {@link AgentProtocol} that accepts connection from CLI clients.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 */
@Extension
public class CliProtocol extends AgentProtocol {
    @Override
    public String getName() {
        return "CLI-connect";
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        new Handler(socket).run();
    }

    protected static class Handler {
        protected final Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() throws IOException, InterruptedException {
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),"UTF-8")),true);
            out.println("Welcome");
            runCli(new Connection(socket));
        }

        protected void runCli(Connection c) throws IOException, InterruptedException {
            Channel channel = new Channel("CLI channel from " + socket.getInetAddress(),
                    Computer.threadPoolForRemoting, Mode.BINARY,
                    new BufferedInputStream(c.in), new BufferedOutputStream(c.out), null, true, Jenkins.getInstance().pluginManager.uberClassLoader);
            channel.setProperty(CliEntryPoint.class.getName(),new CliManagerImpl(channel));
            channel.join();
        }
    }
}
