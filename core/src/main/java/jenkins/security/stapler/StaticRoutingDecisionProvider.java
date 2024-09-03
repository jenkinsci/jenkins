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

package jenkins.security.stapler;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Saveable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Function;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.lang.FieldRef;

/**
 * Fill the list of getter methods that are whitelisted for Stapler
 * Each item in the set are formatted to correspond exactly to what {@link Function#getDisplayName()} returns
 */
@Restricted(NoExternalUse.class)
@Extension
public class StaticRoutingDecisionProvider extends RoutingDecisionProvider implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(StaticRoutingDecisionProvider.class.getName());

    private Set<String> whitelistSignaturesFromFixedList;
    private Set<String> whitelistSignaturesFromUserControlledList;

    private Set<String> blacklistSignaturesFromFixedList;
    private Set<String> blacklistSignaturesFromUserControlledList;

    public StaticRoutingDecisionProvider() {
        reload();
    }

    /**
     * Return the singleton instance of this class, typically for script console use
     */
    public static StaticRoutingDecisionProvider get() {
        return ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class);
    }

    /**
     * @see Function#getSignature()
     * @see FieldRef#getSignature()
     */
    @Override
    @NonNull
    public synchronized Decision decide(@NonNull String signature) {
        if (whitelistSignaturesFromFixedList == null || whitelistSignaturesFromUserControlledList == null ||
                blacklistSignaturesFromFixedList == null || blacklistSignaturesFromUserControlledList == null) {
            reload();
        }

        LOGGER.log(Level.CONFIG, "Checking whitelist for " + signature);

        // priority to blacklist
        if (blacklistSignaturesFromFixedList.contains(signature) || blacklistSignaturesFromUserControlledList.contains(signature)) {
            return Decision.REJECTED;
        }

        if (whitelistSignaturesFromFixedList.contains(signature) || whitelistSignaturesFromUserControlledList.contains(signature)) {
            return Decision.ACCEPTED;
        }

        return Decision.UNKNOWN;
    }

    public synchronized void reload() {
        reloadFromDefault();
        reloadFromUserControlledList();

        resetMetaClassCache();
    }

    @VisibleForTesting
    synchronized void resetAndSave() {
        this.whitelistSignaturesFromFixedList = new HashSet<>();
        this.whitelistSignaturesFromUserControlledList = new HashSet<>();
        this.blacklistSignaturesFromFixedList = new HashSet<>();
        this.blacklistSignaturesFromUserControlledList = new HashSet<>();

        this.save();
    }

    private void resetMetaClassCache() {
        // to allow the change to be effective, i.e. rebuild the MetaClass using the new whitelist
        WebApp.get(Jenkins.get().getServletContext()).clearMetaClassCache();
    }

    private synchronized void reloadFromDefault() {
        try (InputStream is = StaticRoutingDecisionProvider.class.getResourceAsStream("default-whitelist.txt")) {
            whitelistSignaturesFromFixedList = new HashSet<>();
            blacklistSignaturesFromFixedList = new HashSet<>();

            parseFileIntoList(
                    IOUtils.readLines(is, StandardCharsets.UTF_8),
                    whitelistSignaturesFromFixedList,
                    blacklistSignaturesFromFixedList
            );
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        LOGGER.log(Level.FINE, "Found {0} getter in the standard whitelist", whitelistSignaturesFromFixedList.size());
    }

    public synchronized StaticRoutingDecisionProvider add(@NonNull String signature) {
        if (this.whitelistSignaturesFromUserControlledList.add(signature)) {
            LOGGER.log(Level.INFO, "Signature [{0}] added to the whitelist", signature);
            save();
            resetMetaClassCache();
        } else {
            LOGGER.log(Level.INFO, "Signature [{0}] was already present in the whitelist", signature);
        }
        return this;
    }

    public synchronized StaticRoutingDecisionProvider addBlacklistSignature(@NonNull String signature) {
        if (this.blacklistSignaturesFromUserControlledList.add(signature)) {
            LOGGER.log(Level.INFO, "Signature [{0}] added to the blacklist", signature);
            save();
            resetMetaClassCache();
        } else {
            LOGGER.log(Level.INFO, "Signature [{0}] was already present in the blacklist", signature);
        }
        return this;
    }

    public synchronized StaticRoutingDecisionProvider remove(@NonNull String signature) {
        if (this.whitelistSignaturesFromUserControlledList.remove(signature)) {
            LOGGER.log(Level.INFO, "Signature [{0}] removed from the whitelist", signature);
            save();
            resetMetaClassCache();
        } else {
            LOGGER.log(Level.INFO, "Signature [{0}] was not present in the whitelist", signature);
        }
        return this;
    }

    public synchronized StaticRoutingDecisionProvider removeBlacklistSignature(@NonNull String signature) {
        if (this.blacklistSignaturesFromUserControlledList.remove(signature)) {
            LOGGER.log(Level.INFO, "Signature [{0}] removed from the blacklist", signature);
            save();
            resetMetaClassCache();
        } else {
            LOGGER.log(Level.INFO, "Signature [{0}] was not present in the blacklist", signature);
        }
        return this;
    }

    /**
     * Saves the configuration info to the disk.
     */
    @Override
    public synchronized void save() {
        if (BulkChange.contains(this)) {
            return;
        }

        File file = getConfigFile();
        try {
            List<String> allSignatures = new ArrayList<>(whitelistSignaturesFromUserControlledList);
            blacklistSignaturesFromUserControlledList.stream()
                    .map(signature -> "!" + signature)
                    .forEach(allSignatures::add);

            Files.write(Util.fileToPath(file), allSignatures, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     *
     * <p>
     * The constructor of the derived class must call this method.
     * (If we do that in the base class, the derived class won't
     * get a chance to set default values.)
     */
    private synchronized void reloadFromUserControlledList() {
        File file = getConfigFile();
        if (!file.exists()) {
            if ((whitelistSignaturesFromUserControlledList != null && whitelistSignaturesFromUserControlledList.isEmpty()) ||
                    (blacklistSignaturesFromUserControlledList != null && blacklistSignaturesFromUserControlledList.isEmpty())) {
                LOGGER.log(Level.INFO, "No whitelist source file found at " + file + " so resetting user-controlled whitelist");
            }
            whitelistSignaturesFromUserControlledList = new HashSet<>();
            blacklistSignaturesFromUserControlledList = new HashSet<>();
            return;
        }

        LOGGER.log(Level.INFO, "Whitelist source file found at " + file);

        try {
            whitelistSignaturesFromUserControlledList = new HashSet<>();
            blacklistSignaturesFromUserControlledList = new HashSet<>();

            parseFileIntoList(
                    Files.readAllLines(Util.fileToPath(file), StandardCharsets.UTF_8),
                    whitelistSignaturesFromUserControlledList,
                    blacklistSignaturesFromUserControlledList
            );
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + file.getAbsolutePath(), e);
        }
    }

    private File getConfigFile() {
        return new File(WHITELIST_PATH == null ? new File(Jenkins.get().getRootDir(), "stapler-whitelist.txt").toString() : WHITELIST_PATH);
    }

    private void parseFileIntoList(List<String> lines, Set<String> whitelist, Set<String> blacklist) {
        lines.stream()
                .filter(line -> !line.matches("#.*|\\s*"))
                .forEach(line -> {
                    if (line.startsWith("!")) {
                        String withoutExclamation = line.substring(1);
                        if (!withoutExclamation.isEmpty()) {
                            blacklist.add(withoutExclamation);
                        }
                    } else {
                        whitelist.add(line);
                    }
                });
    }

    /** Allow script console access */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static String WHITELIST_PATH = SystemProperties.getString(StaticRoutingDecisionProvider.class.getName() + ".whitelist");

}
