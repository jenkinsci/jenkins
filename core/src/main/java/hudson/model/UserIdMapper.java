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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    private static final Logger LOGGER = Logger.getLogger(UserIdMapper.class.getName());
    private static final int PREFIX_MAX = 15;
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    private File usersDirectory;
    private Map<String, String> idToDirectoryNameMap = new ConcurrentHashMap<>();

    static UserIdMapper getInstance() {
        return ExtensionList.lookupSingleton(UserIdMapper.class);
    }

    public UserIdMapper() {
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED, before = InitMilestone.JOB_LOADED)
    public File init() throws IOException {
        usersDirectory = createUsersDirectoryAsNeeded();
        return usersDirectory;
    }

    @CheckForNull File getDirectory(String userId) {
        String directoryName = idToDirectoryNameMap.get(getIdStrategy().keyFor(userId));
        return directoryName == null ? null : new File(usersDirectory, directoryName);
    }

    File putIfAbsent(String userId) throws IOException {
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
    }

    void clear() {
        idToDirectoryNameMap.clear();
    }

    protected IdStrategy getIdStrategy() {
        return User.idStrategy();
    }

    protected File getUsersDirectory() {
        return User.getRootDir();
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

    void load() throws IOException {
        try { // in case users.xml exists, load it, as UserIdMigrator neglected to resave users/…/config.xml with <id>…</id>
            new XmlFile(XSTREAM, new File(usersDirectory, "users.xml")).unmarshal(this);
        } catch (NoSuchFileException x) {
            // normal for new $JENKINS_HOME
        }
        var children = usersDirectory.listFiles();
        if (children == null) {
            return;
        }
        var idStrategy = getIdStrategy();
        Arrays.sort(children);
        var directoryNames = new HashSet<>(idToDirectoryNameMap.values());
        for (var d : children) {
            var dirName = d.getName();
            if (directoryNames.contains(dirName)) {
                continue; // was handled by users.xml, above
            }
            var xmlFile = new XmlFile(User.XSTREAM, new File(d, User.CONFIG_XML));
            if (xmlFile.exists()) {
                String id;
                try {
                    id = ((User) xmlFile.read()).getId();
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, null, x);
                    continue;
                }
                if (id != null) {
                    var idKey = idStrategy.keyFor(id);
                    var prev = idToDirectoryNameMap.put(idKey, dirName);
                    if (prev != null) {
                        LOGGER.warning(() -> id + " ~ " + idKey + " seems to be registered under both " + prev + " and " + dirName);
                    }
                } else {
                    LOGGER.warning(() -> "Missing both <id> and users.xml for " + d); // @LocalData?
                    idToDirectoryNameMap.put(dirName, dirName);
                }
            }
        }
    }

}
