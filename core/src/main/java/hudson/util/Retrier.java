package hudson.util;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * This class implements a process of doing some action repeatedly synchronously until it is performed successfully.
 * You can set the number of attempts, the action to perform, the milliseconds to wait for, the definition of success,
 * the exceptions that are considered as a failed action, but not an unexpected exception in the action and also the
 * listener to manage the expected exceptions happened, just in case it is helpful.
 * @param <V> The return type of the action to perform.
 */

// Limit the use of this class until it is mature enough
@Restricted(NoExternalUse.class)
public class Retrier <V>{
    private static final Logger LOGGER = Logger.getLogger(Retrier.class.getName());

    private int attempts;
    private long delay;
    private Callable<V> callable;
    private BiPredicate<Integer, V> checkResult;
    private String action;
    private BiFunction<Integer, Exception, V> duringActionExceptionListener;
    private Class<?>[] duringActionExceptions;
    
    private Retrier(Builder<V> builder){
        this.attempts = builder.attempts;
        this.delay = builder.delay;
        this.callable = builder.callable;
        this.checkResult = builder.checkResult;
        this.action = builder.action;
        this.duringActionExceptionListener = builder.duringActionExceptionListener;
        this.duringActionExceptions = builder.duringActionExceptions;
    }

    /**
     * Start to do retries to perform the set action.
     * @return the result of the action, it could be null if there was an exception or if the action itself returns null
     * @throws Exception If a unallowed exception is raised during the action
     */
    public @CheckForNull V start() throws Exception {
        V result = null;
        int currentAttempt = 0;
        boolean success = false;

        while (currentAttempt < attempts && !success) {
            currentAttempt++;
            try {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, Messages.Retrier_Attempt(currentAttempt, action));
                }
                result = callable.call();

            } catch (Exception e) {
                if(duringActionExceptions == null || Stream.of(duringActionExceptions).noneMatch(exception -> exception.isAssignableFrom(e.getClass()))) {
                    // if the raised exception is not considered as a controlled exception doing the action, rethrow it
                    LOGGER.log(Level.WARNING, Messages.Retrier_ExceptionThrown(currentAttempt, action), e);
                    throw e;
                } else {
                    // if the exception is considered as a failed action, notify it to the listener
                    LOGGER.log(Level.INFO, Messages.Retrier_ExceptionFailed(currentAttempt, action), e);
                    if (duringActionExceptionListener != null) {
                        LOGGER.log(Level.INFO, Messages.Retrier_CallingListener(e.getLocalizedMessage(), currentAttempt, action));
                        result = duringActionExceptionListener.apply(currentAttempt, e);
                    }
                }
            }

            // After the call and the call to the listener, which can change the result, test the result
            success = checkResult.test(currentAttempt, result);
            if (!success) {
                if (currentAttempt < attempts) {
                    LOGGER.log(Level.WARNING, Messages.Retrier_AttemptFailed(currentAttempt, action));
                    LOGGER.log(Level.FINE, Messages.Retrier_Sleeping(delay, action));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        LOGGER.log(Level.FINE, Messages.Retrier_Interruption(action));
                        Thread.currentThread().interrupt(); // flag this thread as interrupted
                        currentAttempt = attempts; // finish
                    }
                } else {
                    // Failed to perform the action
                    LOGGER.log(Level.INFO, Messages.Retrier_NoSuccess(action, attempts));
                }
            } else {
                LOGGER.log(Level.INFO, Messages.Retrier_Success(action, currentAttempt));
            }
        }

        return result;
    }

    /**
     * Builder to create a Retrier object. The action to perform, the way of check whether is was
     * successful and the name of the action are required.
     * @param <V> The return type of the action to perform.
     */
    public static class Builder <V> {
        private Callable<V> callable;
        private String action;
        private BiPredicate<Integer, V> checkResult;

        private int attempts = 3;
        private long delay = 1000;
        private BiFunction<Integer, Exception, V> duringActionExceptionListener;
        private Class<?>[] duringActionExceptions;

        /**
         * Set the number of attempts trying to perform the action.
         * @param attempts number of attempts
         * @return this builder
         */
        public @Nonnull Builder<V> withAttempts(int attempts) {
            this.attempts = attempts;
            return this;
        }

        /**
         * Set the time in milliseconds to wait for the next attempt.
         * @param millis milliseconds to wait
         * @return this builder
         */
        public @Nonnull Builder<V> withDelay(long millis) {
            this.delay = millis;
            return this;
        }

        /**
         * Set all the exceptions that are allowed and indicate that the action was failed. When an exception of this
         * type or a child type is raised, a listener can be called ({@link #withDuringActionExceptionListener(BiFunction)}).
         * In any case, the retrier continues its process, retrying to perform the action again, as it was a normal failure.
         * @param exceptions exceptions that indicate that the action was failed.
         * @return this builder
         */
        public @Nonnull Builder<V> withDuringActionExceptions(@CheckForNull Class<?>[] exceptions) {
            this.duringActionExceptions = exceptions;
            return this;
        }

        /**
         * Set the listener to be executed when an allowed exception is raised when performing the action. The listener
         * could even change the result of the action if needed.
         * @param exceptionListener the listener to call to
         * @return this builder
         */
        public @Nonnull Builder<V> withDuringActionExceptionListener(@Nonnull BiFunction<Integer, Exception, V>  exceptionListener) {
            this.duringActionExceptionListener = exceptionListener;
            return this;
        }

        /**
         * Constructor of the builder with the required parameters.
         * @param callable Action to perform
         * @param checkResult Method to check if the result of the action was a success
         * @param action name of the action to perform, for messages purposes.
         */
        
        public Builder(@Nonnull Callable<V> callable, @Nonnull BiPredicate<Integer, V> checkResult, @Nonnull String action) {
            this.callable = callable;
            this.action = action;
            this.checkResult = checkResult;
        }

        /**
         * Create a Retrier object with the specification set in this builder.
         * @return the retrier
         */
        public @Nonnull Retrier<V> build() {
            return new Retrier<>(this);
        }
    }
}
