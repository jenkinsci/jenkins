/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class UserIdMapper {

    private static final XStream2 XSTREAM = new XStream2();
    static final String MAPPING_FILE = "users.xml";
    private static final Logger LOGGER = Logger.getLogger(UserIdMapper.class.getName());
    private static final int PREFIX_MAX = 15;
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[^A-Za-z0-9]");
    @SuppressFBWarnings(value = "SS_SHOULD_BE_STATIC", justification = "Reserved for future use")
    private final int version = 1; // Not currently used, but it may be helpful in the future to store a version.

    private transient File usersDirectory;
    private Map<String, String> idToDirectoryNameMap = new ConcurrentHashMap<>();

    static UserIdMapper getInstance() {
        return ExtensionList.lookupSingleton(UserIdMapper.class);
    }

    public UserIdMapper() {
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED, before = InitMilestone.JOB_LOADED)
    public File init() throws IOException {
        usersDirectory = createUsersDirectoryAsNeeded();
        load();
        return usersDirectory;
    }

    @CheckForNull File getDirectory(String userId) {
        String directoryName = idToDirectoryNameMap.get(getIdStrategy().keyFor(userId));
        return directoryName == null ? null : new File(usersDirectory, directoryName);
    }

    File putIfAbsent(String userId, boolean saveToDisk) throws IOException {
        String idKey = getIdStrategy().keyFor(userId);
        String directoryName = idToDirectoryNameMap.get(idKey);
        File directory = null;
        if (directoryName == null) {
            synchronized (this) {
                directoryName = idToDirectoryNameMap.get(idKey);
                if (directoryName == null) {
                    directory = createDirectoryForNewUser(userId);
                    directoryName = directory.getName();
                    idToDirectoryNameMap.put(idKey, directoryName);
                    if (saveToDisk) {
                        save();
                    }
                }
            }
        }
        return directory == null ? new File(usersDirectory, directoryName) : directory;
    }

    boolean isMapped(String userId) {
        return idToDirectoryNameMap.containsKey(getIdStrategy().keyFor(userId));
    }

    Set<String> getConvertedUserIds() {
        return Collections.unmodifiableSet(idToDirectoryNameMap.keySet());
    }

    void remove(String userId) throws IOException {
        idToDirectoryNameMap.remove(getIdStrategy().keyFor(userId));
        save();
    }

    void clear() {
        idToDirectoryNameMap.clear();
    }

    void reload() throws IOException {
        clear();
        load();
    }

    protected IdStrategy getIdStrategy() {
        return User.idStrategy();
    }

    protected File getUsersDirectory() {
        return User.getRootDir();
    }

    private XmlFile getXmlConfigFile() {
        File file = getConfigFile(usersDirectory);
        return new XmlFile(XSTREAM, file);
    }

    static File getConfigFile(File usersDirectory) {
        return new File(usersDirectory, MAPPING_FILE);
    }

    private File createDirectoryForNewUser(String userId) throws IOException {
        try {
            Path tempDirectory = Files.createTempDirectory(Util.fileToPath(usersDirectory), generatePrefix(userId));
            return tempDirectory.toFile();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating directory for user: " + userId, e);
            throw e;
        }
    }

    private String generatePrefix(String userId) {
        String fullPrefix = PREFIX_PATTERN.matcher(userId).replaceAll("");
        return fullPrefix.length() > PREFIX_MAX - 1 ? fullPrefix.substring(0, PREFIX_MAX - 1) + '_' : fullPrefix + '_';
    }

    private File createUsersDirectoryAsNeeded() throws IOException {
        File usersDirectory = getUsersDirectory();
        if (!usersDirectory.exists()) {
            try {
                Files.createDirectory(usersDirectory.toPath());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to create users directory: " + usersDirectory, e);
                throw e;
            }
        }
        return usersDirectory;
    }

    synchronized void save() throws IOException {
        try {
            getXmlConfigFile().write(this);
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error saving userId mapping file.", ioe);
            throw ioe;
        }
    }

    private void load() throws IOException {
        UserIdMigrator migrator = new UserIdMigrator(usersDirectory, getIdStrategy());
        if (migrator.needsMigration()) {
            try {
                migrator.migrateUsers(this);
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE, "Error migrating users.", ioe);
                throw ioe;
            }
        } else {
            XmlFile config = getXmlConfigFile();
            try {
                config.unmarshal(this);
            } catch (NoSuchFileException e) {
                LOGGER.log(Level.FINE, "User id mapping file does not exist. It will be created when a user is saved.");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + config, e);
                throw e;
            }
        }
    }

}
