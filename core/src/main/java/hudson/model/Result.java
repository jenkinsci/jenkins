package hudson.model;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.basic.AbstractBasicConverter;

import java.io.Serializable;

/**
 * The build outcome.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Result implements Serializable {
    /**
     * The build didn't have any fatal errors not errors.
     */
    public static final Result SUCCESS = new Result("SUCCESS",BallColor.BLUE,0);
    /**
     * The build didn't have any fatal errors but some errors.
     */
    public static final Result UNSTABLE = new Result("UNSTABLE",BallColor.YELLOW,1);
    /**
     * The build had a fatal error.
     */
    public static final Result FAILURE = new Result("FAILURE",BallColor.RED,2);
    /**
     * The build was manually aborted.
     */
    public static final Result ABORTED = new Result("ABORTED",BallColor.GREY,3);

    private final String name;

    /**
     * Bigger numbers are worse.
     */
    private final int ordinal;

    /**
     * Default ball color for this status.
     */
    public final BallColor color;

    private Result(String name, BallColor color, int ordinal) {
        this.name = name;
        this.color = color;
        this.ordinal = ordinal;
    }

    /**
     * Combines two {@link Result}s and returns the worse one.
     */
    public Result combine(Result that) {
        if(this.ordinal < that.ordinal)
            return that;
        else
            return this;
    }

    public boolean isWorseThan(Result that) {
        return this.ordinal > that.ordinal;
    }

    public boolean isWorseOrEqualTo(Result that) {
        return this.ordinal >= that.ordinal;
    }

    public String toString() {
        return name;
    }
    
    private Object readResolve() {
        for (Result r : all)
            if (ordinal==r.ordinal)
                return r;
        return FAILURE;
    }

    private static final long serialVersionUID = 1L;

    private static final Result[] all = new Result[] {SUCCESS,UNSTABLE,FAILURE,ABORTED};

    public static final Converter conv = new AbstractBasicConverter () {
        public boolean canConvert(Class clazz) {
            return clazz==Result.class;
        }

        protected Object fromString(String s) {
            for (Result r : all)
                if (s.equals(r.name))
                    return r;
            return FAILURE;
        }
    };
}
