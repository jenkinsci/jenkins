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

import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @deprecated Formerly used to track which directory held each user configuration.
 *             Now that is deterministic based on a hash of {@link IdStrategy#keyFor}.
 */
@Deprecated
@Restricted(NoExternalUse.class)
public class UserIdMapper {

    private static final XStream2 XSTREAM = new XStream2();
    private static final Logger LOGGER = Logger.getLogger(UserIdMapper.class.getName());

    // contrary to the name, the keys were actually IdStrategy.keyFor, not necessarily ids
    private Map<String, String> idToDirectoryNameMap = new ConcurrentHashMap<>();

    private UserIdMapper() {
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED, before = InitMilestone.JOB_LOADED)
    public static void migrate() throws IOException {
        var usersDirectory = User.getRootDir();
        var data = new UserIdMapper();
        var mapperXml = new XmlFile(XSTREAM, new File(usersDirectory, "users.xml"));
        if (mapperXml.exists()) { // need to migrate
            // Load it, and trust ids it defines over <id>…</id> in users/…/config.xml which UserIdMigrator neglected to resave.
            LOGGER.info(() -> "migrating " + mapperXml);
            mapperXml.unmarshal(data);
            var idStrategy = User.idStrategy();
            for (var entry : data.idToDirectoryNameMap.entrySet()) {
                var idKey = entry.getKey();
                var directoryName = entry.getValue();
                try {
                    var oldDirectory = new File(usersDirectory, directoryName);
                    var userXml = new XmlFile(User.XSTREAM, new File(oldDirectory, User.CONFIG_XML));
                    var user = (User) userXml.read();
                    if (user.id == null || !idKey.equals(idStrategy.keyFor(user.id))) {
                        user.id = idKey; // not quite right but hoping for the best
                        userXml.write(user);
                    }
                    var newDirectory = User.getUserFolderFor(user.id);
                    Files.move(oldDirectory.toPath(), newDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info(() -> "migrated " + oldDirectory + " to " + newDirectory);
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "failed to migrate " + entry, x);
                }
            }
            mapperXml.delete();
        }
        // Also look for any remaining user dirs, such as those predating even UserIdMapper, or PresetData or incomplete @LocalData.
        var subdirectories = usersDirectory.listFiles();
        if (subdirectories != null) {
            for (var oldDirectory : subdirectories) {
                if (!User.HASHED_DIRNAMES.matcher(oldDirectory.getName()).matches()) {
                    var userXml = new XmlFile(User.XSTREAM, new File(oldDirectory, User.CONFIG_XML));
                    if (userXml.exists()) {
                        try {
                            var user = (User) userXml.read();
                            var id = user.id;
                            if (id == null) {
                                id = oldDirectory.getName();
                                user.id = id;
                                userXml.write(user);
                            }
                            var newDirectory = User.getUserFolderFor(id);
                            Files.move(oldDirectory.toPath(), newDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info(() -> "migrated " + oldDirectory + " to " + newDirectory);
                        } catch (Exception x) {
                            LOGGER.log(Level.WARNING, "failed to migrate " + oldDirectory, x);
                        }
                    }
                }
            }
        }
    }

}
