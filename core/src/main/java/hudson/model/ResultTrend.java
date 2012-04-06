/*
 * The MIT License
 *
 * Copyright (c) 2011, Christoph Kutzinski
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

import org.jvnet.localizer.Localizable;

import java.util.Locale;

/**
 * Describes an {@link Result} trend by taking the comparing the result of the current and the previous build.
 * 
 * @author kutzi
 * @since 1.416
 */
public enum ResultTrend {
    /**
     * Previous build was {@link Result#FAILURE} or {@link Result#UNSTABLE}
     * and is now {@link Result#SUCCESS}.
     */
    FIXED(Messages._ResultTrend_Fixed()),
    /**
     * Build as well as previous build (if it has a previous build) are {@link Result#SUCCESS}
     */
    SUCCESS(Messages._ResultTrend_Success()),
    /**
     * Previous build was {@link Result#FAILURE} and is now 'only' {@link Result#UNSTABLE}.
     */
    NOW_UNSTABLE(Messages._ResultTrend_NowUnstable()),
    /**
     * Build as well as previous build are {@link Result#UNSTABLE}.
     */
    STILL_UNSTABLE(Messages._ResultTrend_StillUnstable()),
    /**
     * Previous build (if there is one) was {@link Result#SUCCESS} and current build is {@link Result#UNSTABLE}.
     */
    UNSTABLE(Messages._ResultTrend_Unstable()),
    /**
     * Build as well as previous build are {@link Result#FAILURE}.
     */
    STILL_FAILING(Messages._ResultTrend_StillFailing()),
    /**
     * Previous build (if there is one) was {@link Result#SUCCESS} or {@link Result#UNSTABLE}
     * and current build is {@link Result#FAILURE}.
     */
    FAILURE(Messages._ResultTrend_Failure()),
    /**
     * Build was aborted.
     */
    ABORTED(Messages._ResultTrend_Aborted()),
    /**
     * Build didn't run (yet).
     */
    NOT_BUILT(Messages._ResultTrend_NotBuilt());
    
    private final Localizable description;

    private ResultTrend(Localizable description) {
        this.description = description;
    }
    
    /**
     * Returns a short human-readable description of the result.
     */
    public String getDescription() {
        return this.description.toString();
    }

    /**
     * Gets all upper case ID like token of the build status.
     */
    public String getID() {
        return this.description.toString(Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
    }
    
    /**
     * Returns the result trend of a build.
     * 
     * @param build the build
     * @return the result trend
     */
    public static ResultTrend getResultTrend(AbstractBuild<?, ?> build) {
        return getResultTrend((Run<?,?>)build);
    }

    /**
     * Returns the result trend of a run.
     * 
     * @param run the run
     * @return the result trend
     * 
     * @since 1.441
     */
    public static ResultTrend getResultTrend(Run<?, ?> run) {
        
        Result result = run.getResult();
        
        if (result == Result.ABORTED) {
            return ABORTED;
        } else if (result == Result.NOT_BUILT) {
            return NOT_BUILT;
        }
        
        if (result == Result.SUCCESS) {
            if (isFix(run)) {
                return FIXED;
            } else {
                return SUCCESS;
            }
        }
        
        Run<?, ?> previousBuild = getPreviousNonAbortedBuild(run);
        if (result == Result.UNSTABLE) {
            if (previousBuild == null) {
                return UNSTABLE;
            }
            
            
            if (previousBuild.getResult() == Result.UNSTABLE) {
                return STILL_UNSTABLE;
            } else if (previousBuild.getResult() == Result.FAILURE) {
                return NOW_UNSTABLE;
            } else {
                return UNSTABLE;
            }
        } else if (result == Result.FAILURE) {
            if (previousBuild != null && previousBuild.getResult() == Result.FAILURE) {
                return STILL_FAILING;
            } else {
                return FAILURE;
            }
        }
        
        throw new IllegalArgumentException("Unknown result: '" + result + "' for build: " + run);
    }
    
    /**
     * Returns the previous 'not aborted' build (i.e. ignores ABORTED and NOT_BUILT builds)
     * or null.
    */
    private static Run<?, ?> getPreviousNonAbortedBuild(Run<?, ?> build) {
        Run<?, ?> previousBuild = build.getPreviousBuild();
        while (previousBuild != null) {
            if (previousBuild.getResult() == null 
                || previousBuild.getResult() == Result.ABORTED
                || previousBuild.getResult() == Result.NOT_BUILT) {
                
                previousBuild = previousBuild.getPreviousBuild();
            } else {
                return previousBuild;
            }
        }
        return previousBuild;
    }
    
    /**
     * Returns true if this build represents a 'fix'.
     * I.e. it is the first successful build after previous
     * 'failed' and/or 'unstable' builds.
     * Ignores 'aborted' and 'not built' builds.
     */
    private static boolean isFix(Run<?, ?> build) {
        if (build.getResult() != Result.SUCCESS) {
            return false;
        }
        
        Run<?, ?> previousBuild = getPreviousNonAbortedBuild(build);
        if (previousBuild != null) {
            return previousBuild.getResult().isWorseThan(Result.SUCCESS);
        }
        return false;
    }
}
