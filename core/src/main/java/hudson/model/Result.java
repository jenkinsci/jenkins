/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.basic.AbstractBasicConverter;
import org.kohsuke.stapler.export.CustomExportedBean;

import java.io.Serializable;

/**
 * The build outcome.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Result implements Serializable, CustomExportedBean {
    /**
     * The build had no errors.
     */
    public static final Result SUCCESS = new Result("SUCCESS",BallColor.BLUE,0);
    /**
     * The build had some errors but they were not fatal.
     * For example, some tests failed.
     */
    public static final Result UNSTABLE = new Result("UNSTABLE",BallColor.YELLOW,1);
    /**
     * The build had a fatal error.
     */
    public static final Result FAILURE = new Result("FAILURE",BallColor.RED,2);
    /**
     * The module was not built.
     * <p>
     * This status code is used in a multi-stage build (like maven2)
     * where a problem in earlier stage prevented later stages from building.
     */
    public static final Result NOT_BUILT = new Result("NOT_BUILT",BallColor.GREY,3);
    /**
     * The build was manually aborted.
     */
    public static final Result ABORTED = new Result("ABORTED",BallColor.ABORTED,4);

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

    public boolean isBetterThan(Result that) {
        return this.ordinal < that.ordinal;
    }

    public boolean isBetterOrEqualTo(Result that) {
        return this.ordinal <= that.ordinal;
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

    public String toExportedObject() {
        return name;
    }

    private static final long serialVersionUID = 1L;

    private static final Result[] all = new Result[] {SUCCESS,UNSTABLE,FAILURE,NOT_BUILT,ABORTED};

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
