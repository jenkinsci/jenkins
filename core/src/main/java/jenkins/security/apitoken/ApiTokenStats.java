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

import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
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
    
    private transient File parent;
    
    public ApiTokenStats() {
        this.init();
    }
    
    private ApiTokenStats readResolve() {
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
    
    void setParent(@Nonnull File parent) {
        this.parent = parent;
    }
    
    private boolean areStatsDisabled(){
        return !ApiTokenPropertyConfiguration.get().isUsageStatisticsEnabled();
    }
    
    /**
     * Will trigger the save if there is some modification
     */
    public synchronized void removeId(@Nonnull String tokenUuid) {
        if(areStatsDisabled()){
            return;
        }
        
        boolean tokenRemoved = tokenStats.removeIf(s -> s.tokenUuid.equals(tokenUuid));
        if (tokenRemoved) {
            save();
        }
    }
    
    /**
     * Will trigger the save
     */
    public synchronized @Nonnull SingleTokenStats updateUsageForId(@Nonnull String tokenUuid) {
        if(areStatsDisabled()){
            return new SingleTokenStats(tokenUuid);
        }
        
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
    
    public synchronized @Nonnull SingleTokenStats findTokenStatsById(@Nonnull String tokenUuid) {
        if(areStatsDisabled()){
            return new SingleTokenStats(tokenUuid);
        }
        
        // if we create a new empty stats object, no need to add it to the list
        return findById(tokenUuid)
                .orElse(new SingleTokenStats(tokenUuid));
    }
    
    private @Nonnull Optional<SingleTokenStats> findById(@Nonnull String tokenUuid) {
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
        
        XmlFile configFile = getConfigFile(parent);
        try {
            configFile.write(this);
            SaveableListener.fireOnChange(this, configFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + configFile, e);
        }
    }
    
    /**
     * Loads the data from the disk into the new object.
     * <p>
     * If the file is not present, a fresh new instance is created.
     */
    public static @Nonnull ApiTokenStats load(@Nonnull File parent) {
        // even if we are not using statistics, we load the existing one in case the configuration
        // is enabled afterwards to avoid erasing data
        
        XmlFile file = getConfigFile(parent);
        ApiTokenStats apiTokenStats;
        if (file.exists()) {
            try {
                apiTokenStats = (ApiTokenStats) file.unmarshal(ApiTokenStats.class);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
                apiTokenStats = new ApiTokenStats();
            }
        } else {
            apiTokenStats = new ApiTokenStats();
        }
    
        apiTokenStats.setParent(parent);
        return apiTokenStats;
    }
    
    protected static XmlFile getConfigFile(File parent) {
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
        
        private SingleTokenStats readResolve() {
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
