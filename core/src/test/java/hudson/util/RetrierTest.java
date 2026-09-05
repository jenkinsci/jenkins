package hudson.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class RetrierTest {
    private static final Logger LOG = Logger.getLogger(RetrierTest.class.getName());

    @Test
    void performedAtThirdAttemptTest() throws Exception {
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
        assertTrue(finalResult != null && finalResult);

        String text = Messages.Retrier_Success(ACTION, SUCCESSFUL_ATTEMPT);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)), String.format("The log should contain '%s'", text));
    }

    @Test
    void sleepWorksTest() throws Exception {
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
        assertTrue(timeElapsed >= SLEEP);

        // Check result is true
        assertTrue(finalResult != null && finalResult);

        // Check the log tell us the sleep time
        String text = Messages.Retrier_Sleeping(SLEEP, ACTION);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)), String.format("The log should contain '%s'", text));

        // recover log level
        retrierLogger.setLevel(currentLogLevel);
    }

    @Test
    void failedActionAfterThreeAttemptsTest() throws Exception {
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

        assertFalse(finalResult != null && finalResult);

        String text = Messages.Retrier_NoSuccess(ACTION, ATTEMPTS);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(text)), String.format("The log should contain '%s'", text));

    }

    @Test
    void failedActionWithExceptionAfterThreeAttemptsWithoutListenerTest() throws Exception {
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
        assertNull(finalResult);

        String textNoSuccess = Messages.Retrier_NoSuccess(ACTION, ATTEMPTS);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(textNoSuccess)), String.format("The log should contain '%s'", textNoSuccess));

        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)), String.format("The log should contain '%s'", testException));

    }

    @Test
    void failedActionWithAllowedExceptionWithListenerChangingResultTest() throws Exception {
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
        assertTrue(finalResult != null && finalResult);

        // The action was a success
        String textSuccess = Messages.Retrier_Success(ACTION, ATTEMPTS);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(textSuccess)), String.format("The log should contain '%s'", textSuccess));

        // And the message talking about the allowed raised is also there
        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)), String.format("The log should contain '%s'", testException));
    }

    @Test
    void failedActionWithAllowedExceptionByInheritanceTest() throws Exception {
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
        assertTrue(finalResult != null && finalResult);

        // The action was a success
        String textSuccess = Messages.Retrier_Success(ACTION, ATTEMPTS);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(textSuccess)), String.format("The log should contain '%s'", textSuccess));

        // And the message talking about the allowed raised is also there
        String testException = Messages.Retrier_ExceptionFailed(ATTEMPTS, ACTION);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().startsWith(testException)), String.format("The log should contain '%s'", testException));
    }

    @Test
    void failedActionWithUnAllowedExceptionTest() {
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
        assertThrows(IOException.class, r::start, "The process should be exited with an unexpected exception");
        String testFailure = Messages.Retrier_ExceptionThrown(ATTEMPTS, ACTION);
        assertTrue(handler.getView().stream().anyMatch(m -> m.getMessage().contains(testFailure)), String.format("The log should contain '%s'", testFailure));
    }
}
