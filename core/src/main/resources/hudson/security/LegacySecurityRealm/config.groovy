package hudson.security.LegacySecurityRealm

import hudson.PluginWrapper
import hudson.model.UnprotectedRootAction
import jenkins.model.Jenkins

def f = namespace(lib.FormTagLib)

f.entry(title: _('Unprotected URLs')) {
    f.description(_('blurb'))
    ul {
        for (def action : Jenkins.get().getActions().sort { x, y -> x.getUrlName() <=> y.getUrlName() }) {
            if (action instanceof UnprotectedRootAction) {
                li {
                    a(href: '../' + action.getUrlName(), rel: 'noopener noreferrer', target: '_blank') {
                        code {
                            text(action.getUrlName())
                        }
                    }
                    br()
                    PluginWrapper whichPlugin = Jenkins.get().getPluginManager().whichPlugin(action.getClass())
                    if (whichPlugin == null) {
                        text(_("byCore"))
                    } else {
                        raw(_("byPlugin", whichPlugin.getDisplayName(), whichPlugin.getUrl()))
                    }
                }
            }
        }
    }
}
