/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
class UserIdMigrator {

    private static final Logger LOGGER = Logger.getLogger(UserIdMigrator.class.getName());
    private static final String EMPTY_USERNAME_DIRECTORY_NAME = "emptyUsername";

    private final File usersDirectory;
    private final IdStrategy idStrategy;

    UserIdMigrator(File usersDirectory, IdStrategy idStrategy) {
        this.usersDirectory = usersDirectory;
        this.idStrategy = idStrategy;
    }

    boolean needsMigration() {
        File mappingFile = UserIdMapper.getConfigFile(usersDirectory);
        if (mappingFile.exists() && mappingFile.isFile()) {
            LOGGER.finest("User mapping file already exists. No migration needed.");
            return false;
        }
        File[] userDirectories = listUserDirectories();
        return userDirectories != null && userDirectories.length > 0;
    }

    private File[] listUserDirectories() {
        return usersDirectory.listFiles(file -> file.isDirectory() && new File(file, User.CONFIG_XML).exists());
    }

    Map<String, File> scanExistingUsers() throws IOException {
        Map<String, File> users = new HashMap<>();
        File[] userDirectories = listUserDirectories();
        if (userDirectories != null) {
            for (File directory : userDirectories) {
                String userId = idStrategy.idFromFilename(directory.getName());
                users.put(userId, directory);
            }
        }
        addEmptyUsernameIfExists(users);
        return users;
    }

    private void addEmptyUsernameIfExists(Map<String, File> users) throws IOException {
        File emptyUsernameConfigFile = new File(usersDirectory, User.CONFIG_XML);
        if (emptyUsernameConfigFile.exists()) {
            File newEmptyUsernameDirectory = new File(usersDirectory, EMPTY_USERNAME_DIRECTORY_NAME);
            Files.createDirectory(newEmptyUsernameDirectory.toPath());
            File newEmptyUsernameConfigFile = new File(newEmptyUsernameDirectory, User.CONFIG_XML);
            Files.move(emptyUsernameConfigFile.toPath(), newEmptyUsernameConfigFile.toPath());
            users.put("", newEmptyUsernameDirectory);
        }
    }

    void migrateUsers(UserIdMapper mapper) throws IOException {
        LOGGER.fine("Beginning migration of users to userId mapping.");
        Map<String, File> existingUsers = scanExistingUsers();
        for (Map.Entry<String, File> existingUser : existingUsers.entrySet()) {
            File newDirectory = mapper.putIfAbsent(existingUser.getKey(), false);
            LOGGER.log(Level.INFO, "Migrating user '" + existingUser.getKey() + "' from 'users/" + existingUser.getValue().getName() + "/' to 'users/" + newDirectory.getName() + "/'");
            Files.move(existingUser.getValue().toPath(), newDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        mapper.save();
        LOGGER.fine("Completed migration of users to userId mapping.");
    }

}
