import hudson.remoting.PipeTest;
import org.jvnet.hudson.test.Bug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Test bed to reproduce JENKINS-8703.
 *
 * @author Kohsuke Kawaguchi
 */
public class Driver8703 {
    @Bug(8703)
    public static void main(String[] args) throws Throwable {
//        int i=0;
//        while (true) {
//            System.out.println(i++);
//            foo();
//        }
        
        ExecutorService es = Executors.newCachedThreadPool();
        List<Future> flist = new ArrayList<Future>();
        for (int i=0; i<10000; i++) {
            flist.add(es.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    Thread.currentThread().setName("testing");
                    try {
                        foo();
                        return null;
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        throw new Exception(t);
                    } finally {
                        Thread.currentThread().setName("done");
                    }
                }
            }));
        }

        for (Future ff : flist) {
            ff.get();
        }

        System.out.println("All done");
        es.shutdown();
    }

    private static void foo() throws Throwable {
        PipeTest t = new PipeTest();
        t.setName("testSaturation");
        t.runBare();
    }
}
