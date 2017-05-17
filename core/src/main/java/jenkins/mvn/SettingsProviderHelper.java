package jenkins.mvn;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SettingsProviderHelper {
    private final static Logger LOGGER = Logger.getLogger(SettingsProviderHelper.class.getName());

    public static boolean ABSOlUTE_PATH_BACKWARD_COMPATIBILITY = Boolean.valueOf(System.getProperty("jenkins.mvn.SettingsProvider.absolutePathBackwardCompatibility"));

    /**
     * Provide the {@link FilePath} of the given plain text {@code path} for the desired Maven settings.xml file.
     *
     * @param build       the build to provide the settings for
     * @param workspace the workspace in which the build takes place
     * @param listener the listener of this given build
     * @return the filepath to the provided file. {@code null} if no settings will be provided.
     * @throws IOException exception accessing to the file system
     * @throws InterruptedException occurs when the build is interrupted
     */
    @CheckForNull
    public static FilePath supplySettings(String path, Run<?, ?> build, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(path)) {
            return null;
        }

        try {
            EnvVars env = build.getEnvironment(listener);

            FilePath result;
            if (build instanceof AbstractBuild) {
                AbstractBuild abstractBuild = (AbstractBuild) build;
                String targetPath = Util.replaceMacro(path, abstractBuild.getBuildVariableResolver());
                targetPath = env.expand(targetPath);
                if (IOUtils.isAbsolute(targetPath) && ABSOlUTE_PATH_BACKWARD_COMPATIBILITY) {
                    File settingsFile = new File(targetPath);
                    checkFileIsaMavenSettingsFile(settingsFile);
                    return new FilePath(settingsFile);
                } else {
                    FilePath mrSettings = abstractBuild.getModuleRoot().child(targetPath);
                    FilePath wsSettings = abstractBuild.getWorkspace().child(targetPath);
                    if (wsSettings.exists()) {
                        result = wsSettings;
                    } else if (mrSettings.exists()) {
                        result = mrSettings;
                    } else {
                        throw new FileNotFoundException(targetPath);
                    }
                }
            } else {
                String targetPath = env.expand(path);
                result = workspace.child(targetPath);
            }
            return result;
        } catch (IOException e) {
            throw new IOException(
                    "Failed to prepare Maven (global) settings.xml with path '" + path + "' for " + build +
                            " in workspace " + workspace, e);
        } catch (InterruptedException e) {
            throw new IOException(
                    "Failed to prepare Maven (global) settings.xml with path '" + path + "' for " + build +
                            " in workspace " + workspace, e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to prepare Maven (global) settings.xml with path '" + path + "' for " + build +
                            " in workspace " + workspace, e);
        }
    }

    /**
     * Check that the given file is a valid Maven settings.xml file
     *
     * @param file the file to check
     * @throws IllegalStateException if the given file is not a valid settings.xml file
     */
    public static void checkFileIsaMavenSettingsFile(File file) throws IllegalStateException {
        if (!file.exists()) {
            throw new IllegalStateException(new FileNotFoundException("Maven settings file '" + file + "' not found"));
        }
        Document mavenSettingsDocument;
        try {
            mavenSettingsDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new IllegalStateException("Invalid Maven settings file '" + file.getPath() + "': " + e.toString(), e);
        }
        Element documentElement = mavenSettingsDocument.getDocumentElement();
        if (!"settings".equals(documentElement.getNodeName())) {
            throw new IllegalStateException(
                    "Invalid Maven settings file '" + file.getPath() + "', " +
                            "actual document element: '" + documentElement.getNodeName() + "', " +
                            "expected document element: 'settings'");
        }
        // we don't check the namespace as the XML parsers are often "non validating"

    }
}
