package jenkins.widgets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.tasks.BuildWrapper;
import hudson.util.Secret;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;


public class HistoryPageFilterTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void doNotFindSensitiveBuildParams() throws Exception {
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        final PasswordParameterDefinition passwordParameterDefinition = new PasswordParameterDefinition("password", Secret.fromString("t0ps3cr3t"), "description");
        final StringParameterDefinition stringParameterDefinition = new StringParameterDefinition("key", "value", "desc");
        freeStyleProject.addProperty(new ParametersDefinitionProperty(passwordParameterDefinition, stringParameterDefinition));
        final FreeStyleBuild build1 = j.buildAndAssertSuccess(freeStyleProject);
        final FreeStyleBuild build2 = j.waitForCompletion(Objects.requireNonNull(freeStyleProject.scheduleBuild2(
                0,
                new ParametersAction(
                        passwordParameterDefinition.createValue("p4ssw0rd"),
                        stringParameterDefinition.createValue("value123"))))
                .waitForStart());
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("value");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs.stream().map(HistoryPageEntry::getEntry).collect(Collectors.toList()), contains(build2, build1));
            assertThat(historyPageFilter.queueItems, empty());
        }
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("t0p");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs, empty());
            assertThat(historyPageFilter.queueItems, empty());
        }
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("value123");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs.stream().map(HistoryPageEntry::getEntry).collect(Collectors.toList()), contains(build2));
            assertThat(historyPageFilter.queueItems, empty());
        }
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("p4ss");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs, empty());
            assertThat(historyPageFilter.queueItems, empty());
        }
    }

    @Test
    public void doNotFindSensitiveBuildWrapperVars() throws Exception {
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        freeStyleProject.getBuildWrappersList().add(new BuildWrapperWithSomeSensitiveVars(Map.of("key1", "value123", "key2", "value234", "key3", "s3cr3t"), Set.of("key3")));
        final FreeStyleBuild build = j.buildAndAssertSuccess(freeStyleProject);
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("value");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs.stream().map(HistoryPageEntry::getEntry).collect(Collectors.toList()), contains(build));
            assertThat(historyPageFilter.queueItems, empty());
        }
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("value123");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs.stream().map(HistoryPageEntry::getEntry).collect(Collectors.toList()), contains(build));
            assertThat(historyPageFilter.queueItems, empty());
        }
        {
            final HistoryPageFilter<FreeStyleBuild> historyPageFilter = new HistoryPageFilter<>(30);
            historyPageFilter.setSearchString("s3cr3t");
            historyPageFilter.add(freeStyleProject.getBuilds());
            assertThat(historyPageFilter.runs, empty());
            assertThat(historyPageFilter.queueItems, empty());
        }
    }

    private static class BuildWrapperWithSomeSensitiveVars extends BuildWrapper {
        private final Map<String, String> variables;
        private final Set<String> sensitiveVariables;

        private BuildWrapperWithSomeSensitiveVars(Map<String, String> variables, Set<String> sensitiveVariables) {

            this.variables = new HashMap<>(variables);
            this.sensitiveVariables = new HashSet<>(sensitiveVariables);
        }

        @Override
        public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
            variables.putAll(this.variables);
        }

        @Override
        public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
            sensitiveVariables.addAll(this.sensitiveVariables);
        }
    }
}
