package jenkins.security.s2m.MasterKillSwitchConfiguration

def f=namespace(lib.FormTagLib)

if (instance.isRelevant()) {
    f.section(title: _('Agent \u2192 Master Security')) {
        f.optionalBlock(field: "masterToSlaveAccessControl", title: _("Enable Agent \u2192 Master Access Control")) {
            f.nested() {
                raw _("Rules can be tweaked <a href='${rootURL}/administrativeMonitor/slaveToMasterAccessControl/'>here</a>")
            }
        }
    }
}
