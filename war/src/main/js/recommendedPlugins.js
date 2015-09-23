module.exports = {
	"defaultPlugins": [ "workflow-aggregator", "github" ],
	"availablePlugins": [
		{
			"category": "General",
			"description": "(a collection of things I cannot think of a better name for)",
			"plugins": [
			  { "name": "ansicolor", "usage": "10116" },
			  { "name": "build-timeout", "usage": "13610" },
			  { "name": "embeddable-build-status", "usage": "3712" },
			  { "name": "envinject", "usage": "26765" },
			  { "name": "next-build-number", "usage": "4846" },
			  { "name": "rebuild", "usage": "8780" },
			  { "name": "sidebar-link", "usage": "4179" },
			  { "name": "simple-theme-plugin", "usage": "6475" },
			  { "name": "timestamper", "usage": "10850" },
			  { "name": "translation", "usage": "116526" },
			  { "name": "ws-cleanup", "usage": "16127" }
			]
		},
		{
			"category":"Organization and Administration",
			"plugins": [
			  { "name": "antisamy-markup-formatter", "usage": "105502" },
			  { "name": "cloudbees-folder", "usage": "3203" },
			  { "name": "compact-columns", "usage": "3007" },
			  { "name": "dashboard-view", "usage": "15646" },
			  { "name": "disk-usage", "usage": "11017" },
			  { "name": "extra-columns", "usage": "5235" },
			  { "name": "jobConfigHistory", "usage": "13746" },
			  { "name": "monitoring", "usage": "6975" },
			  { "name": "view-job-filters", "usage": "6795" }
			]
		},
		{
			"category":"Build Tools",
			"plugins": [
			  { "name": "ant", "usage": "117347" },
			  { "name": "gradle", "usage": "12409" },
			  { "name": "groovy", "usage": "8609" },
			  { "name": "maven-plugin", "usage": "118716" },
			  { "name": "msbuild", "usage": "12031" },
			  { "name": "nodejs", "usage": "5207" },
			  { "name": "powershell", "usage": "4771" }
			]
		},
		{
			"category":"Build Analysis and Reporting",
			"plugins": [
			  { "name": "analysis-collector", "usage": "9129" },
			  { "name": "checkstyle", "usage": "13894" },
			  { "name": "cobertura", "usage": "14223" },
			  { "name": "javadoc", "usage": "118301" },
			  { "name": "junit", "usage": "94052" },
			  { "name": "pmd", "usage": "11760" },
			  { "name": "sonar", "usage": "15794" },
			  { "name": "xunit", "usage": "13470" }
			]
		},
		{
			"category":"Build Pipelines and Continuous Delivery",
			"plugins": [
			  { "name": "build-pipeline-plugin", "usage": "16414" },
			  { "name": "conditional-buildstep", "usage": "18888" },
			  { "name": "copyartifact", "usage": "20810" },
			  { "name": "parameterized-trigger", "usage": "38180" },
			  { "name": "workflow-aggregator", "usage": "1451", "title": "Workflow", "excerpt": "The easiest way to orchestrate contiuous builds, delivery, and deployment." }
			]
		},
		{
			"category":"SCM",
			"plugins": [
			  { "name": "bitbucket", "usage": "3311" },
			  { "name": "clearcase", "usage": "1628" },
			  { "name": "cvs", "usage": "113379" },
			  { "name": "gerrit-trigger", "usage": "3731" },
			  { "name": "git", "usage": "67648" },
			  { "name": "github", "usage": "18672" },
			  { "name": "ghprb", "usage": "5384" },
			  { "name": "gitlab-hook", "usage": "3768" },
			  { "name": "gitlab-plugin", "usage": "3557" },
			  { "name": "mercurial", "usage": "5017" },
			  { "name": "perforce", "usage": "4174" },
			  { "name": "subversion", "usage": "116038" }
			]
		},
		{
			"category":"Distributed Builds and Containers",
			"plugins": [
			  { "name": "docker-build-publish", "usage": "1226" },
			  { "name": "docker-build-step", "usage": "1802" },
			  { "name": "docker-plugin", "usage": "2148" },
			  { "name": "matrix-project", "usage": "102899" },
			  { "name": "ssh-slaves", "usage": "119273" },
			  { "name": "windows-slaves", "usage": "105619" }
			]
		},
		{
			"category":"User Management and Security",
			"plugins": [
			  { "name": "active-directory", "usage": "13754" },
			  { "name": "github-oauth", "usage": "5120" },
			  { "name": "ldap", "usage": "115714" },
			  { "name": "pam-auth", "usage": "116658" },
			  { "name": "matrix-auth", "usage": "110042" }
			]
		},
		{
			"category":"Notifications and Publishing",
			"plugins": [
			  { "name": "deploy", "usage": "10069" },
			  { "name": "email-ext", "usage": "35762" },
			  { "name": "flexible-publish", "usage": "4769" },
			  { "name": "htmlpublisher", "usage": "18785" },
			  { "name": "instant-messaging", "usage": "5552" },
			  { "name": "jabber", "usage": "2355" },
			  { "name": "jira", "usage": "8430" },
			  { "name": "mailer", "usage": "117236" },
			  { "name": "publish-over-ssh", "usage": "15610" },
			  { "name": "redmine", "usage": "2292" },
			  { "name": "slack", "usage": "7707" }
			]
		}
	]
};