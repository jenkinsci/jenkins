package jenkins.util

import hudson.util.OneShotEvent
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

    def lock = new OneShotEvent()

    @Test
    public void doubleBooking() {
        def f1,f2;

        def base = Executors.newCachedThreadPool()
        def es = new AtmostOneTaskExecutor(base,
                { ->
                    counter.incrementAndGet()
                    lock.block()
                } as Callable);
        f1 = es.submit()
        while (counter.get() == 0)
        ;   // spin lock until executor gets to the choking point

        f2 = es.submit() // this should hang
        Thread.sleep(500)   // make sure the 2nd task is hanging
        assert counter.get() == 1
        assert !f2.isDone()

        lock.signal() // let the first one go

        f1.get();   // first one should complete

        // now 2nd one gets going and hits the choke point
        f2.get()
        assert counter.get()==2
    }
}
