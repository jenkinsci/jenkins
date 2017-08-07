package hudson.model.operations;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Created by haswell on 8/7/17.
 */
public interface Operation<T, U> {

    /**
     * Get the owning object
     * @return
     */

    U getHost();

    /**
     * Invoke the operation and return the result
     * @param request the stapler request
     * @param response the stapler response
     * @param args the arguments passed to the
     * @return the value of the operation
     */
    T invoke(
            StaplerRequest request,
            StaplerResponse response,
            Object...args
    );


    /**
     * Resolve an argument for an operation and cast it to the expected type.
     * @param index the index of the argument
     * @param args the arguments passed to this invocation
     * @param <V> the expected type of the argument
     * @return the argument at the provided index cast to the type indicated by the call-site
     */
    @SuppressWarnings("unchecked")
    default <V> V argument(int index, Object...args) {
        if(index < args.length && index >= 0) {
            return (V) args[index];
        }

        throw new IllegalArgumentException(String.format("Index must be between 0 and %d ", args.length));
    }

}
