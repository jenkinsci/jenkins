package jenkins.util

import org.junit.Test

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class AtmostOneTaskExecutorTest {
    def counter = new AtomicInteger()

    def lock = new Object()

    @Test
    public void doubleBooking() {
        synchronized (lock) {
            def base = Executors.newCachedThreadPool()
            def es = new AtmostOneTaskExecutor(base,
                    { ->
                        counter.incrementAndGet()
                        synchronized (lock) {
                            lock.wait()
                        }
                    } as Callable);
            es.submit()
            while (counter.get()==0)
                ;   // spin lock until executor gets to the choking point

            def f = es.submit() // this should hang
            Thread.sleep(500)   // make sure the 2nd task is hanging
            assert counter.get()==1
            assert !f.isDone()

            notifyAll() // let the first one go
        }
    }
}
