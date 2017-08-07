package hudson.model.view.operations;


/**
 * Created by haswell on 8/7/17.
 *
 * Reusable headers and sets of headers
 */
public class Headers {

    public static String EXPIRES = "Expires";

    public static String PRAGMA = "Pragma";

    public static String CACHE_CONTROL = "Cache Control";


    /**
     * Indicate that a response must not be cached
     */
    public static final HeaderSet NO_CACHE = new HeaderSet(
            new Header(
                    CACHE_CONTROL,
                    Values.NO_CACHE,
                    Values.NO_STORE,
                    Values.MUST_REVALIDATE
            ),
            new Header(PRAGMA, Values.NO_CACHE),
            new Header(EXPIRES, "0")
    );


    static final class Values {

        static final String NO_CACHE = "no cache";

        static final String NO_STORE = "no store";

        static final String MUST_REVALIDATE  = "must revalidate";


    }
}
