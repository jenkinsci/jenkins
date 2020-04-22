package jenkins.model.Jenkins

import jenkins.model.Jenkins
import jenkins.util.SystemProperties

static def filePathForClassName(String className) {
    if (className.contains('$')) {
        return className.substring(0, className.indexOf('$')).replace('.', '/') + ".java"
    }
    return className.replace('.', '/') + ".java"
}

static def refForVersion() {
    String version = Jenkins.version.toString()
    if (version.matches('[0-9][.][0-9]+(|[.][0-9])')) {
        return 'jenkins-' + version;
    }
    return 'master'
}

static def pluginForClassName(String className) {
    // TODO this probably doesn't work but right now SystemProperties is @Restricted anyway
    def clazz = Class.forName(className)
    def plugin = Jenkins.get().getPluginManager().whichPlugin(clazz)
}

static def urlForCoreClass(String className, int line) {
    def classFile = filePathForClassName(className)
    def version = refForVersion()
    return 'https://github.com/jenkinsci/jenkins/blob/' + version + '/core/src/main/java/' + classFile + "#L" + line
}

h1 {
    text(_("System Property Access"))
}
p {
    raw(_('blurb'))
}
table(class:'pane sortable bigtable') {
    tr {
        th(class:'pane-header', initialSortDir:'down') {
            text(_('Name'))
        }
        th(class:'pane-header') {
            text(_('Access Count'))
        }
        th(class:'pane-header') {
            text(_('Last Accessed Time'))
        }
        th(class:'pane-header') {
            text(_('Last Accessed Value'))
        }
        th(class:'pane-header') {
            text(_('Accessed By'))
        }
    }
    def accesses = SystemProperties.getAccesses()
    for (def entry : accesses.entrySet()) {
        tr {
            td(class:'pane') {
                text(entry.key)
            }
            td(class:'pane') {
                text(entry.value.accessCount)
            }
            td(class:'pane') {
                text(entry.value.lastAccessTime)
            }
            td(class:'pane') {
                text(entry.value.lastAccessValue)
            }
            td(class:'pane') {
                for (def ste : entry.value.accessingCode) {
                    def plugin = pluginForClassName(ste.className)
                    if (plugin == null) {
                        a(href:urlForCoreClass(ste.className, ste.lineNumber)) {
                            text(ste.className)
                        }
                    } else {
                        // TODO add source code linking for plugins
                        text(ste.className)
                    }
                }
            }
        }
    }
}
