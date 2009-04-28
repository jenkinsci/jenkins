package hudson.cli;

import hudson.remoting.Callable;
import hudson.remoting.Channel;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.reflect.Method;

/**
 * @author Kohsuke Kawaguchi
 */
public class CLI {
    public static void main(final String[] args) throws Exception {
        URL target = new URL("http://localhost:8080/duplexChannel");
        FullDuplexHttpStream con = new FullDuplexHttpStream(target);
        ExecutorService pool = Executors.newCachedThreadPool();
        Channel channel = new Channel("Chunked connection to "+target,
                pool,con.getInputStream(),con.getOutputStream());

        // execute the command
        int r=-1;
        try {
            r = channel.call(new Callable<Integer,Exception>() {
                public Integer call() throws Exception {
                    Method m = Class.forName("hudson.model.Hudson").getMethod("cli", String[].class);
                    return (Integer)m.invoke(null,new Object[]{args});
                }
            });
        } finally {
            channel.close();
            pool.shutdown();
        }

        System.exit(r);
    }
}
