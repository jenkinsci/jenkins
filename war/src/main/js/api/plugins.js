//
// TODO: Get all of this information from the Update Center via a REST API.
//

//
// TODO: Decide on what the real "recommended" plugin set is. This is just a 1st stab.
// Also remember, the user ultimately has full control as they can easily customize
// away from these.
//
exports.recommendedPlugins = [
    "antisamy-markup-formatter",
    "credentials",
    "junit",
    "mailer",
    "matrix-auth",
    "script-security",
    "subversion",
    "translation"
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
            { "name": "external-monitor-job" },
            { "name": "translation" }
        ]
    },
    {
        "category":"Organization and Administration",
        "plugins": [
            { "name": "antisamy-markup-formatter" }
        ]
    },
    {
        "category":"Build Tools",
        "plugins": [
            { "name": "ant" },
            { "name": "maven-plugin" }
        ]
    },
    {
        "category":"Build Analysis and Reporting",
        "plugins": [
            { "name": "javadoc" },
            { "name": "junit" }
        ]
    },
    {
        "category":"SCM",
        "plugins": [
            { "name": "cvs" },
            { "name": "subversion" }
        ]
    },
    {
        "category":"Distributed Builds and Containers",
        "plugins": [
            { "name": "matrix-project" },
            { "name": "ssh-slaves" },
            { "name": "windows-slaves" }
        ]
    },
    {
        "category":"User Management and Security",
        "plugins": [            
            { "name": "credentials" },
            { "name": "ldap" },
            { "name": "matrix-auth" },
            { "name": "pam-auth" },
            { "name": "script-security" },
            { "name": "ssh-credentials" }
        ]
    },
    {
        "category":"Notifications and Publishing",
        "plugins": [
            { "name": "mailer" }
        ]
    }
];