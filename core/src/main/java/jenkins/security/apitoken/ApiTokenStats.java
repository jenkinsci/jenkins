/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.security.apitoken;

import com.google.common.annotations.VisibleForTesting;
import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class ApiTokenStats implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenStats.class.getName());
    
    /**
     * Normally a user will not have more 2-3 tokens at a time, 
     * so there is no need to store a map here
     */
    private List<SingleTokenStats> tokenStats;
    
    private transient User user;
    
    @VisibleForTesting 
    transient File parent;
    
    @VisibleForTesting 
    ApiTokenStats() {
        this.init();
    }
    
    private Object readResolve() {
        this.init();
        return this;
    }
    
    private void init() {
        if (this.tokenStats == null) {
            this.tokenStats = new ArrayList<>();
        } else {
            keepLastUpdatedUnique();
        }
    }
    
    /**
     * In case of duplicate entries, we keep only the last updated element
     */
    private void keepLastUpdatedUnique() {
        Map<String, SingleTokenStats> temp = new HashMap<>();
        this.tokenStats.forEach(candidate -> {
            SingleTokenStats current = temp.get(candidate.tokenUuid);
            if (current == null) {
                temp.put(candidate.tokenUuid, candidate);
            } else {
                int comparison = SingleTokenStats.COMP_BY_LAST_USE_THEN_COUNTER.compare(current, candidate);
                if (comparison < 0) {
                    // candidate was updated more recently (or has a bigger counter in case of perfectly equivalent dates)
                    temp.put(candidate.tokenUuid, candidate);
                }
            }
        });
        
        this.tokenStats = new ArrayList<>(temp.values());
    }
    
    /**
     * @deprecated use {@link #load(User)} instead of {@link #load(File)}
     * The method will be removed in a later version as it's an internal one
     */
    @Deprecated
    // to force even if someone wants to remove the one from the class
    @Restricted(NoExternalUse.class)
    void setParent(@NonNull File parent) {
        this.parent = parent;
    }
    
    private boolean areStatsDisabled(){
        return !ApiTokenPropertyConfiguration.get().isUsageStatisticsEnabled();
    }
    
    /**
     * Will trigger the save if there is some modifications
     */
    public synchronized void removeId(@NonNull String tokenUuid) {
        if(areStatsDisabled()){
            return;
        }
        
        boolean tokenRemoved = tokenStats.removeIf(s -> s.tokenUuid.equals(tokenUuid));
        if (tokenRemoved) {
            save();
        }
    }
    
    /**
     * Will trigger the save if there is some modifications
     */
    public synchronized void removeAll() {
        int size = tokenStats.size();
        tokenStats.clear();
        if (size > 0) {
            save();
        }
    }
    
    public synchronized void removeAllExcept(@NonNull String tokenUuid) {
        int sizeBefore = tokenStats.size();
        tokenStats.removeIf(s -> !s.tokenUuid.equals(tokenUuid));
        int sizeAfter = tokenStats.size();
        if (sizeBefore != sizeAfter) {
            save();
        }
    }
    
   /**
     * Will trigger the save
     */
    public @NonNull SingleTokenStats updateUsageForId(@NonNull String tokenUuid) {
        if(areStatsDisabled()){
            return new SingleTokenStats(tokenUuid);
        }
        
        return updateUsageForIdIfNeeded(tokenUuid);
    }
    
    
    private synchronized SingleTokenStats updateUsageForIdIfNeeded(@NonNull String tokenUuid) {
    	SingleTokenStats stats = findById(tokenUuid)
                .orElseGet(() -> {
                    SingleTokenStats result = new SingleTokenStats(tokenUuid);
                    tokenStats.add(result);
                    return result;
                });
        
        stats.notifyUse();
        save();
        
        return stats;
    }
    
    public synchronized @NonNull SingleTokenStats findTokenStatsById(@NonNull String tokenUuid) {
        if(areStatsDisabled()){
            return new SingleTokenStats(tokenUuid);
        }
        
        // if we create a new empty stats object, no need to add it to the list
        return findById(tokenUuid)
                .orElse(new SingleTokenStats(tokenUuid));
    }
    
    private @NonNull Optional<SingleTokenStats> findById(@NonNull String tokenUuid) {
        return tokenStats.stream()
                .filter(s -> s.tokenUuid.equals(tokenUuid))
                .findFirst();
    }
    
    /**
     * Saves the configuration info to the disk.
     */
    @Override
    public synchronized void save() {
        if(areStatsDisabled()){
            return;
        }
        
        if (BulkChange.contains(this))
            return;
        
        /*
         * Note: the userFolder should never be null at this point.
         * The userFolder could be null during User creation with the new storage approach
         * but when this code is called, from token used / removed, the folder exists.
         */
        File userFolder = getUserFolder();
        if (userFolder == null) {
            return;
        }
        
        XmlFile configFile = getConfigFile(userFolder);
        try {
            configFile.write(this);
            SaveableListener.fireOnChange(this, configFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + configFile, e);
        }
    }
    
    private @CheckForNull File getUserFolder(){
        File userFolder = parent;
        if (userFolder == null && this.user != null) {
            userFolder = user.getUserFolder();
            if (userFolder == null) {
                LOGGER.log(Level.INFO, "No user folder yet for user {0}", user.getId());
                return null;
            }
            this.parent = userFolder;
        }
        
        return userFolder;
    }
    
    /**
     * Loads the data from the disk into the new object.
     * <p>
     * If the file is not present, a fresh new instance is created.
     * 
     * @deprecated use {@link #load(User)} instead
     * The method will be removed in a later version as it's an internal one
     */
    @Deprecated
    // to force even if someone wants to remove the one from the class
    @Restricted(NoExternalUse.class) 
    public static @NonNull ApiTokenStats load(@CheckForNull File parent) {
        // even if we are not using statistics, we load the existing one in case the configuration
        // is enabled afterwards to avoid erasing data
        
        if (parent == null) {
            return new ApiTokenStats();
        }
    
        ApiTokenStats apiTokenStats = internalLoad(parent);
        if (apiTokenStats == null) {
            apiTokenStats = new ApiTokenStats();
        }
    
        apiTokenStats.setParent(parent);
        return apiTokenStats;
    }
    
    /**
     * Loads the data from the user folder into the new object.
     * <p>
     * If the folder does not exist yet, a fresh new instance is created.
     */
    public static @NonNull ApiTokenStats load(@NonNull User user) {
        // even if we are not using statistics, we load the existing one in case the configuration
        // is enabled afterwards to avoid erasing data
        
        ApiTokenStats apiTokenStats = null;
        
        File userFolder = user.getUserFolder();
        if (userFolder != null) {
            apiTokenStats = internalLoad(userFolder);
        }
        
        if (apiTokenStats == null) {
            apiTokenStats = new ApiTokenStats();
        }
        
        apiTokenStats.user = user;
        
        return apiTokenStats;
    }
    
    @VisibleForTesting
    static @CheckForNull ApiTokenStats internalLoad(@NonNull File userFolder) {
        ApiTokenStats apiTokenStats = null;
        XmlFile statsFile = getConfigFile(userFolder);
        if (statsFile.exists()) {
            try {
                apiTokenStats = (ApiTokenStats) statsFile.unmarshal(ApiTokenStats.class);
                apiTokenStats.parent = userFolder;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + statsFile, e);
            }
        }
        
        return apiTokenStats;
    }
    
    protected static @NonNull XmlFile getConfigFile(@NonNull File parent) {
        return new XmlFile(new File(parent, "apiTokenStats.xml"));
    }
    
    public static class SingleTokenStats {
        private static Comparator<SingleTokenStats> COMP_BY_LAST_USE_THEN_COUNTER =
                Comparator.comparing(SingleTokenStats::getLastUseDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(SingleTokenStats::getUseCounter);
        
        private final String tokenUuid;
        private Date lastUseDate;
        private Integer useCounter;
        
        private SingleTokenStats(String tokenUuid) {
            this.tokenUuid = tokenUuid;
        }
        
        private Object readResolve() {
            if (this.useCounter != null) {
                // to avoid negative numbers to be injected
                this.useCounter = Math.max(0, this.useCounter);
            }
            return this;
        }
        
        private void notifyUse() {
            this.useCounter = useCounter == null ? 1 : useCounter + 1;
            this.lastUseDate = new Date();
        }
        
        public String getTokenUuid() {
            return tokenUuid;
        }
        
        // used by Jelly view
        public int getUseCounter() {
            return useCounter == null ? 0 : useCounter;
        }
        
        // used by Jelly view
        public Date getLastUseDate() {
            return lastUseDate;
        }
        
        // used by Jelly view
        /**
         * Return the number of days since the last usage
         * Relevant only if the lastUseDate is not null
         */
        public long getNumDaysUse() {
            return lastUseDate == null ? 0 : Util.daysElapsedSince(lastUseDate);
        }
    }
}
