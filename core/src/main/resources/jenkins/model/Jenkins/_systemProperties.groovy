package jenkins.model.Jenkins

import hudson.PluginWrapper
import hudson.Util
import jenkins.model.Jenkins
import jenkins.util.SystemProperties

static PluginWrapper pluginForClassName(String className) {
    def clazz = Jenkins.get().getPluginManager().uberClassLoader.loadClass(className)
    return Jenkins.get().getPluginManager().whichPlugin(clazz)
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
            text(_('Last Accessed'))
        }
        th(class:'pane-header') {
            text(_('Most Recent Value'))
        }
        th(class:'pane-header') {
            text(_('Accessed By'))
        }
    }
    def accesses = SystemProperties.getAccesses()
    def now = System.currentTimeMillis();
    for (def entry : accesses.entrySet()) {
        tr {
            td(class:'pane') {
                text(entry.key)
            }
            td(class:'pane') {
                text(entry.value.accessCount)
            }
            td(class:'pane', data: entry.value.lastAccessTime) {
                span(_("ago", Util.getTimeSpanString(now - entry.value.lastAccessTime.toEpochMilli())), tooltip: entry.value.lastAccessTime)
            }
            td(class:'pane') {
                text(entry.value.lastAccessValue)
            }
            td(class:'pane') {
                ul(style:'margin:0;padding:0;') {
                    for (def ste : entry.value.accessingCode) {
                        li(style:'list-style-type: none;') {
                            PluginWrapper plugin = pluginForClassName(ste.className)
                            if (plugin == null) {
                                text("Jenkins (core)", tooltip: ste)
                            } else {
                                a(href:plugin.url) {
                                    text(plugin.displayName, tooltip: ste)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
