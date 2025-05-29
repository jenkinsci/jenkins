package hudson.util;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

public class RetrierTest {
    private static final Logger LOG = Logger.getLogger(RetrierTest.class.getName());

    @Test
    public void performedAtThirdAttemptTest() throws Exception {
        final int SUCCESSFUL_ATTEMPT = 3;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        () -> {
                            LOG.info("action performed");
                            return true;
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> currentAttempt == SUCCESSFUL_ATTEMPT,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(SUCCESSFUL_ATTEMPT + 1)
                .withDelay(100)

                // Construct the object
                .build();

        // Begin the process
        Boolean finalResult = r.start();
        Assert.assertTrue(finalResult != null && finalResult);

        String text = Messages.Retrier_Success(ACTION, SUCCESSFUL_ATTEMPT);
        assertTrue(String.format("The log should contain '%s'", text), handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)));
    }

    @Test
    public void sleepWorksTest() throws Exception {
        final int SUCCESSFUL_ATTEMPT = 2;
        final String ACTION = "print";
        final int SLEEP = 500;

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger retrierLogger = Logger.getLogger(Retrier.class.getName());
        // save current level, just in case it's needed in other tests
        Level currentLogLevel = retrierLogger.getLevel();
        retrierLogger.setLevel(Level.FINE);
        retrierLogger.addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        () -> {
                            LOG.info("action performed");
                            return true;
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> currentAttempt == SUCCESSFUL_ATTEMPT,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(SUCCESSFUL_ATTEMPT)

                // The time we want to wait between attempts. Let's set less time than default (1000) to have a faster
                // test
                .withDelay(SLEEP)

                // Construct the object
                .build();

        // Begin the process measuring how long it takes
        Instant start = Instant.now();
        Boolean finalResult = r.start();
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();

        // Check delay works
        Assert.assertTrue(timeElapsed >= SLEEP);

        // Check result is true
        Assert.assertTrue(finalResult != null && finalResult);

        // Check the log tell us the sleep time
        String text = Messages.Retrier_Sleeping(SLEEP, ACTION);
        assertTrue(String.format("The log should contain '%s'", text), handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)));

        // recover log level
        retrierLogger.setLevel(currentLogLevel);
    }

    @Test
    public void failedActionAfterThreeAttemptsTest() throws Exception {
        final int ATTEMPTS = 3;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        () -> {
                            LOG.info("action performed");
                            return false;
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> result,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(ATTEMPTS)
                .withDelay(100)

                // Construct the object
                .build();

        // Begin the process
        Boolean finalResult = r.start();

        Assert.assertFalse(finalResult != null && finalResult);

        String text = Messages.Retrier_NoSuccess(ACTION, ATTEMPTS);
        assertTrue(String.format("The log should contain '%s'", text), handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)));

    }

    @Test
    public void failedActionWithExceptionAfterThreeAttemptsWithoutListenerTest() throws Exception {
        final int ATTEMPTS = 3;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        (Callable<Boolean>) () -> {
                            throw new IndexOutOfBoundsException("Exception allowed considered as failure");
                        },
                        // check the result and return true (boolean primitive type) if success
                        (currentAttempt, result) -> result != null && result,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(ATTEMPTS)
                .withDelay(100)
                .withDuringActionExceptions(new Class[]{IndexOutOfBoundsException.class})
                // Construct the object
                .build();

        // Begin the process without catching the allowed exceptions
        Boolean finalResult = r.start();
        Assert.assertNull(finalResult);

        String textNoSuccess = Messages.Retrier_NoSuccess(ACTION, ATTEMPTS);
        assertTrue(String.format("The log should contain '%s'", textNoSuccess), handler.getView().stream().anyMatch(m -> m.getMessage().contains(textNoSuccess)));

        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(String.format("The log should contain '%s'", testException), handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)));

    }

    @Test
    public void failedActionWithAllowedExceptionWithListenerChangingResultTest() throws Exception {
        final int ATTEMPTS = 1;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        (Callable<Boolean>) () -> {
                            throw new IndexOutOfBoundsException("Exception allowed considered as failure");
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> result,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(ATTEMPTS)
                // Exceptions allowed
                .withDuringActionExceptions(new Class[]{IndexOutOfBoundsException.class})
                // Listener to call. It change the result to success
                .withDuringActionExceptionListener((attempt, exception) -> true)
                // Construct the object
                .build();

        // Begin the process catching the allowed exception
        Boolean finalResult = r.start();
        Assert.assertTrue(finalResult != null && finalResult);

        // The action was a success
        String textSuccess = Messages.Retrier_Success(ACTION, ATTEMPTS);
        assertTrue(String.format("The log should contain '%s'", textSuccess), handler.getView().stream().anyMatch(m -> m.getMessage().contains(textSuccess)));

        // And the message talking about the allowed raised is also there
        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(String.format("The log should contain '%s'", testException), handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)));
    }

    @Test
    public void failedActionWithAllowedExceptionByInheritanceTest() throws Exception {
        final int ATTEMPTS = 1;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        (Callable<Boolean>) () -> {
                            // This one is allowed because we allow IndexOutOfBoundsException (parent exception)
                            throw new ArrayIndexOutOfBoundsException("Unallowed exception breaks the process");
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> result,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(ATTEMPTS)
                // Exceptions allowed (not the one raised)
                .withDuringActionExceptions(new Class[]{IndexOutOfBoundsException.class})
                // Listener to call. It change the result to success
                .withDuringActionExceptionListener((attempt, exception) -> true)
                // Construct the object
                .build();

        // Begin the process catching the allowed exception
        Boolean finalResult = r.start();
        Assert.assertTrue(finalResult != null && finalResult);

        // The action was a success
        String textSuccess = Messages.Retrier_Success(ACTION, ATTEMPTS);
        assertTrue(String.format("The log should contain '%s'", textSuccess), handler.getView().stream().anyMatch(m -> m.getMessage().contains(textSuccess)));

        // And the message talking about the allowed raised is also there
        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(String.format("The log should contain '%s'", testException), handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)));
    }

    @Test
    public void failedActionWithUnAllowedExceptionTest() {
        final int ATTEMPTS = 1;
        final String ACTION = "print";

        RingBufferLogHandler handler = new RingBufferLogHandler(20);
        Logger.getLogger(Retrier.class.getName()).addHandler(handler);

        // Set the required params
        Retrier<Boolean> r = new Retrier.Builder<>(
                        // action to perform
                        (Callable<Boolean>) () -> {
                            // This one is not allowed, so it is raised out of the start method
                            throw new IOException("Unallowed exception breaks the process");
                        },
                        // check the result and return true if success
                        (currentAttempt, result) -> result,
                        //name of the action
                        ACTION
                )

                // Set the optional parameters
                .withAttempts(ATTEMPTS)
                // Exceptions allowed (not the one raised)
                .withDuringActionExceptions(new Class[]{IndexOutOfBoundsException.class})
                // Construct the object
                .build();

        // Begin the process that raises an unexpected exception
        assertThrows("The process should be exited with an unexpected exception", IOException.class, r::start);
        String testFailure = Messages.Retrier_ExceptionThrown(ATTEMPTS, ACTION);
        assertTrue(String.format("The log should contain '%s'", testFailure), handler.getView().stream().anyMatch(m -> m.getMessage().contains(testFailure)));
    }
}
