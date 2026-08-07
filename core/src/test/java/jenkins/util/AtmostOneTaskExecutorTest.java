package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.util.OneShotEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AtmostOneTaskExecutorTest {

    @SuppressWarnings("empty-statement")
    @Test
    void doubleBooking() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        OneShotEvent lock = new OneShotEvent();
        Future<?> f1, f2;

        ExecutorService base = Executors.newCachedThreadPool();
        AtmostOneTaskExecutor<?> es = new AtmostOneTaskExecutor<Void>(base, () -> {
            counter.incrementAndGet();
            try {
                lock.block();
            } catch (InterruptedException x) {
                throw new RuntimeException(x);
            }
            return null;
        });
        f1 = es.submit();
        while (counter.get() == 0) {
            // spin lock until executor gets to the choking point
        }

        f2 = es.submit(); // this should hang
        Thread.sleep(500);   // make sure the 2nd task is hanging
        assertEquals(1, counter.get());
        assertFalse(f2.isDone());

        lock.signal(); // let the first one go

        f1.get();   // first one should complete

        // now 2nd one gets going and hits the choke point
        f2.get();
        assertEquals(2, counter.get());
    }

}
