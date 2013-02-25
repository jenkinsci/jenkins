package hudson.maven;

import hudson.maven.MojoInfo;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public class MojoInfoBuilder {

    private String groupId;
    private String artifactId;
    private String goalName;
    private String version = "1.0";
    private Map<String, String> configValues = new HashMap<String, String>();
    private long startTime = System.currentTimeMillis();
    
    public static MojoInfoBuilder mojoBuilder(String groupId, String artifactId, String goalName) {
        return new MojoInfoBuilder(groupId, artifactId, goalName);
    }
    
    private MojoInfoBuilder(String groupId, String artifactId, String goalName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.goalName = goalName;
    }
    
    public MojoInfoBuilder copy() {
        MojoInfoBuilder copy = new MojoInfoBuilder(this.groupId, this.artifactId, this.goalName)
            .version(this.version);
        copy.configValues.putAll(this.configValues);
        return copy;
    }
    
    public MojoInfoBuilder version(String version) {
        this.version = version;
        return this;
    }
    
    public MojoInfoBuilder startTime(long startTime) {
        this.startTime = startTime;
        return this;
    }
    
    public MojoInfoBuilder configValue(String key,String value) {
        configValues.put(key, value);
        return this;
    }
    
    public MojoInfo build() {
        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setGroupId(groupId);
        pluginDescriptor.setArtifactId(artifactId);
        pluginDescriptor.setVersion(version);
        
        MojoDescriptor mojoDescriptor = new MojoDescriptor();
        mojoDescriptor.setPluginDescriptor(pluginDescriptor);
        mojoDescriptor.setGoal(goalName);
        
        MojoExecution mojoExecution = new MojoExecution(mojoDescriptor);
        
        PlexusConfiguration configuration = new DefaultPlexusConfiguration("configuration");
        for (Map.Entry<String, String> e : this.configValues.entrySet()) {
            configuration.addChild(e.getKey(),e.getValue());
        }
        
        ExpressionEvaluator evaluator = new ExpressionEvaluator() {
            @Override
            public Object evaluate(String expression) {
                return expression;
            }
            
            @Override
            public File alignToBaseDirectory(File file) {
                return file;
            }
        };
        
       MojoInfo info = new MojoInfo(mojoExecution, null, configuration, evaluator, startTime);
        return info;
    }
    
    
}
