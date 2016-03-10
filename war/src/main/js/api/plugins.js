//
// TODO: Get all of this information from the Update Center via a REST API.
//

//
// TODO: Decide on what the real "recommended" plugin set is. This is just a 1st stab.
// Also remember, the user ultimately has full control as they can easily customize
// away from these.
//
exports.recommendedPlugins = [
    "ant",
    "antisamy-markup-formatter",
    "build-monitor-plugin",
    "build-timeout",
    "cloudbees-folder",
    "credentials-binding",
    "email-ext",
    "git",
    "github-branch-source",
    "gradle",
    "ldap",
    "mailer",
    // "matrix-auth",
    "pam-auth",
    "pipeline-stage-view",
    "ssh-slaves",
    "subversion",
    "timestamper",
    "workflow-aggregator",
    "workflow-multibranch",
    "ws-cleanup"
];

//
// A Categorized list of the plugins offered for install in the wizard.
// This is a community curated list.
//
exports.availablePlugins = [
    {
        "category":"Organization and Administration",
        "plugins": [
            // { "name": "dashboard-view" },
            { "name": "build-monitor-plugin" },
            { "name": "cloudbees-folder" },
            { "name": "antisamy-markup-formatter" }
        ]
    },
    {
        "category":"Build Features",
        "description":"Add general purpose features to your jobs",
        "plugins": [
            { "name": "ansicolor" },
            // { "name": "build-name-setter" },
            { "name": "build-timeout" },
            { "name": "config-file-provider" },
            { "name": "credentials-binding" },
            { "name": "rebuild" },
            { "name": "ssh-agent" },
            // { "name": "throttle-concurrents" },
            { "name": "timestamper" }
            // { "name": "ws-cleanup" }
        ]
    },
    {
        "category":"Build Tools",
        "plugins": [
            { "name": "ant" },
            { "name": "gradle" },
            { "name": "msbuild" },
            { "name": "nodejs" }
        ]
    },
    {
        "category":"Build Analysis and Reporting",
        "plugins": [
            // { "name": "checkstyle" },
            // { "name": "cobertura" },
            { "name": "htmlpublisher" },
            { "name": "junit" },
            // { "name": "sonar" },
            // { "name": "warnings" },
            { "name": "xunit" }
        ]
    },
    {
        "category":"Pipelines and Continuous Delivery",
        "plugins": [
            { "name": "workflow-aggregator" },
            { "name": "workflow-multibranch" },
            { "name": "github-branch-source" },
            { "name": "pipeline-stage-view" },
            { "name": "build-pipeline-plugin" },
            // { "name": "conditional-buildstep" },
            // { "name": "jenkins-multijob-plugin" },
            { "name": "parameterized-trigger" },
            { "name": "copyartifact" }
        ]
    },
    {
        "category":"Source Code Management",
        "plugins": [
            { "name": "bitbucket" },
            { "name": "clearcase" },
            { "name": "cvs" },
            { "name": "git" },
            { "name": "git-parameter" },
            { "name": "github" },
            { "name": "gitlab-plugin" },
            { "name": "p4" },
            { "name": "repo" },
            { "name": "subversion" },
            { "name": "teamconcert" },
            { "name": "tfs" }
        ]
    },
    {
        "category":"Distributed Builds",
        "plugins": [
            { "name": "matrix-project" },
            { "name": "ssh-slaves" },
            { "name": "windows-slaves" }
        ]
    },
    {
        "category":"User Management and Security",
        "plugins": [            
            // { "name": "matrix-auth" },
            { "name": "pam-auth" },
            { "name": "ldap" },
            // { "name": "role-strategy" },
            { "name": "active-directory" }
        ]
    },
    {
        "category":"Notifications and Publishing",
        "plugins": [
            { "name": "email-ext" },
            { "name": "emailext-template" },
            { "name": "mailer" },
            { "name": "publish-over-ssh" },
            { "name": "slack" },
            { "name": "ssh" }
        ]
    }
];