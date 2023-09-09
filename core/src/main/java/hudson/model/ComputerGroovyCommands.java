
package hudson.model;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.util.RemotingDiagnostics;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class ComputerGroovyCommands implements Saveable, Serializable {

    private String name = "";

    public ComputerGroovyCommands(String name) {
        this.name = name;
    }

    private static final Logger LOGGER = Logger.getLogger(ComputerGroovyCommands.class.getName());

    private String groovyScript;

    public void setGroovyScript(String groovyString) {
        this.groovyScript = groovyString;
    }

    public String getGroovyScript() {
        return groovyScript;
    }

    public String getName() {
        return name;
    }

    // name of executor node.
    private String executorNodeName = "(built-in)"; // master node per default

    public void setExecutorNodeName(String executorNodeName) {
        this.executorNodeName = executorNodeName;
    }

    public String getExecutorNodeName() {
        return executorNodeName;
    }

    public String execute() {
        return execute("", "");
    }

    public String execute(String preCommand, String postCommand) {

        String finalScript = preCommand + "\n" + groovyScript + "\n" + postCommand;

        LOGGER.fine(this.name + " groovy script: " + finalScript);

        String groovyOut;

        Computer computer = Jenkins.get().getComputer(executorNodeName);
        if (computer == null) {
            LOGGER.warning("Computer does not exists: " + executorNodeName);
            return null;
        }

        try {

            /// @todo configure somehow, where shall be this script executed. the 'built-in' node is not the best idea
            groovyOut = RemotingDiagnostics.executeGroovy(finalScript, computer.getChannel());
            LOGGER.fine(this.name + " groovy output: " + groovyOut);
        } catch (Exception error) {
            groovyOut = null;
            LOGGER.warning("Failed to execute groovy script " + this.name + " on node " + computer.getName() + ": " + error.getMessage());
        }

        return groovyOut;
    }

    private XmlFile getConfigFile() {
        return getConfigFile(this.name);
    }

    private static XmlFile getConfigFile(String name) {
        return new XmlFile(new File(Jenkins.get().getRootDir(), ComputerGroovyCommands.class.getName() + "." + name + ".xml"));
    }

    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this))   return;
        XmlFile config = getConfigFile();
        config.write(this);
        SaveableListener.fireOnChange(this, config);
    }

    public static ComputerGroovyCommands load(String name) throws IOException {
        XmlFile f = getConfigFile(name);
        if (f.exists())
            return (ComputerGroovyCommands) f.read();
        else
            return new ComputerGroovyCommands(name);
    }
}