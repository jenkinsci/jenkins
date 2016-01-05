//
// TODO: Get all of this information from the Update Center via a REST API.
//

//
// TODO: Decide on what the real "recommended" plugin set is. This is just a 1st stab.
// Also remember, the user ultimately has full control as they can easily customize 
// away from these.
//
exports.recommendedPlugins = [
    "build-timeout",
    "timestamper",
    "antisamy-markup-formatter",
    "cloudbees-credentials",
    "cloudbees-folder",
    "ant",
    "junit",
    "copyartifact",
    "parameterized-trigger",
    "workflow-aggregator",
    "git",
    "subversion",
    "ssh-slaves",
    "windows-slaves",
    "ldap",
    "pam-auth",
    "matrix-auth",
    "email-ext",
    "mailer"
];

//
// A Categorized list of the plugins offered for install in the wizard.
// This is a community curated list. 
//
exports.availablePlugins = [
    {
        "category": "General",
        "description": "(a collection of things I cannot think of a better name for)",
        "plugins": [
            { "name": "ansicolor" },
            { "name": "build-timeout" },
            { "name": "embeddable-build-status" },
            { "name": "envinject" },
            { "name": "next-build-number" },
            { "name": "rebuild" },
            { "name": "sidebar-link" },
            { "name": "simple-theme-plugin" },
            { "name": "timestamper" },
            { "name": "translation" },
            { "name": "ws-cleanup" }
        ]
    },
    {
        "category":"Organization and Administration",
        "plugins": [
            { "name": "antisamy-markup-formatter" },
            { "name": "cloudbees-folder" },
            { "name": "compact-columns" },
            { "name": "dashboard-view" },
            { "name": "disk-usage" },
            { "name": "cloudbees-disk-usage-simple" },
            { "name": "extra-columns" },
            { "name": "jobConfigHistory" },
            { "name": "monitoring" },
            { "name": "view-job-filters" }
        ]
    },
    {
        "category":"Build Tools",
        "plugins": [
            { "name": "ant" },
            { "name": "gradle" },
            { "name": "groovy" },
            { "name": "maven-plugin" },
            { "name": "msbuild" },
            { "name": "nodejs" },
            { "name": "powershell" },
            { "name": "release" },
            { "name": "m2release" }
        ]
    },
    {
        "category":"Build Analysis and Reporting",
        "plugins": [
            { "name": "analysis-collector" },
            { "name": "build-failure-analyzer" },
            { "name": "checkstyle" },
            { "name": "cobertura" },
            { "name": "javadoc" },
            { "name": "junit" },
            { "name": "pmd" },
            { "name": "sonar" },
            { "name": "xunit" }
        ]
    },
    {
        "category":"Build Pipelines and Continuous Delivery",
        "plugins": [
            { "name": "build-pipeline-plugin" },
            { "name": "conditional-buildstep" },
            { "name": "copyartifact" },
            { "name": "parameterized-trigger" },
            { "name": "workflow-aggregator", "title": "Workflow", "excerpt": "The easiest way to orchestrate contiuous builds, delivery, and deployment." }
        ]
    },
    {
        "category":"SCM",
        "plugins": [
            { "name": "bitbucket" },
            { "name": "clearcase" },
            { "name": "cvs" },
            { "name": "gerrit-trigger" },
            { "name": "git" },
            { "name": "github" },
            { "name": "ghprb" },
            { "name": "gitlab-hook" },
            { "name": "gitlab-plugin" },
            { "name": "mercurial" },
            { "name": "perforce" },
            { "name": "subversion" }
        ]
    },
    {
        "category":"Distributed Builds and Containers",
        "plugins": [
            { "name": "docker-build-publish" },
            { "name": "docker-build-step" },
            { "name": "docker-plugin" },
            { "name": "matrix-project" },
            { "name": "ssh-slaves" },
            { "name": "windows-slaves" }
        ]
    },
    {
        "category":"User Management and Security",
        "plugins": [
            { "name": "active-directory" },
            { "name": "github-oauth" },
            { "name": "ldap" },
            { "name": "pam-auth" },
            { "name": "matrix-auth" }
        ]
    },
    {
        "category":"Notifications and Publishing",
        "plugins": [
            { "name": "deploy" },
            { "name": "email-ext" },
            { "name": "flexible-publish" },
            { "name": "htmlpublisher" },
            { "name": "instant-messaging" },
            { "name": "jabber" },
            { "name": "jira" },
            { "name": "mailer" },
            { "name": "publish-over-ssh" },
            { "name": "redmine" },
            { "name": "slack" }
        ]
    }
];