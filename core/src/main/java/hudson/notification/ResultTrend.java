package hudson.notification;

import java.util.Locale;

import hudson.model.AbstractBuild;
import hudson.model.Result;

/**
 * Describes an {@link Result} trend by taking the comparing the result of the current and the previous build.
 * 
 * @author kutzi
 * @since TODO
 */
public enum ResultTrend {
    /**
     * Previous build was {@link Result#FAILURE} or {@link Result#UNSTABLE}
     * and is now {@link Result#SUCCESS}.
     */
    FIXED,
    /**
     * Build as well as previous build (if it has a previous build) are {@link Result#SUCCESS}
     */
    SUCCESS,
    /**
     * Previous build was {@link Result#FAILURE} and is now 'only' {@link Result#UNSTABLE}.
     */
    NOW_UNSTABLE("Now unstable"),
    /**
     * Build as well as previous build are {@link Result#UNSTABLE}.
     */
    STILL_UNSTABLE("Still unstable"),
    /**
     * Previous build (if there is one) was {@link Result#SUCCESS} and current build is {@link Result#UNSTABLE}.
     */
    UNSTABLE,
    /**
     * Build as well as previous build are {@link Result#FAILURE}.
     */
    STILL_FAILING("Still failing"),
    /**
     * Previous build (if there is one) was {@link Result#SUCCESS} or {@link Result#UNSTABLE}
     * and current build is {@link Result#UNSTABLE}.
     */
    FAILURE,
    /**
     * Build was aborted.
     */
    ABORTED,
    /**
     * Build didn't run (yet).
     */
    NOT_BUILT("Not built");
    
    private final String description;

    private ResultTrend() {
        this.description = name().charAt(0) + name().substring(1).toLowerCase(Locale.ENGLISH);
    }
    
    private ResultTrend(String description) {
        this.description = description;
    }
    
    /**
     * Returns a short english description of the result.
     */
    public String getDescription() {
        return this.description;
    }
    
    public String getUpperCaseDescription() {
        return this.description.toUpperCase(Locale.ENGLISH);
    }
    
    /**
     * Returns the result trend of a build.
     * 
     * @param build the current build
     * @return the result trend
     */
    public static ResultTrend getResultTrend(AbstractBuild<?, ?> build) {
        Result result = build.getResult();
        
        if (result == Result.ABORTED) {
            return ABORTED;
        } else if (result == Result.NOT_BUILT) {
            return NOT_BUILT;
        }
        
        if (result == Result.SUCCESS) {
            if (isFix(build)) {
                return FIXED;
            } else {
                return SUCCESS;
            }
        }
        
        AbstractBuild<?, ?> previousBuild = getPreviousNonAbortedBuild(build);
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
        
        throw new IllegalArgumentException("Unknown result: '" + result + "' for build: " + build);
    }
    
    /**
     * Returns the previous 'not aborted' build (i.e. ignores ABORTED and NOT_BUILT builds)
     * or null.
    */
    private static AbstractBuild<?, ?> getPreviousNonAbortedBuild(AbstractBuild<?, ?> build) {
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
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
    private static boolean isFix(AbstractBuild<?, ?> build) {
        if (build.getResult() != Result.SUCCESS) {
            return false;
        }
        
        AbstractBuild<?, ?> previousBuild = getPreviousNonAbortedBuild(build);
        if (previousBuild != null) {
            return previousBuild.getResult().isWorseThan(Result.SUCCESS);
        }
        return false;
    }
}
