package jenkins.security.s2m.MasterKillSwitchConfiguration

def f=namespace(lib.FormTagLib)

if (instance.isRelevant()) {
    f.optionalBlock(field:"masterToSlaveAccessControl", title:_("Enable Slave \u2192 Master Access Control")) {
        f.nested() {
            raw _("Rules can be tweaked <a href='${rootURL}/administrativeMonitor/slaveToMasterAccessControl/'>here</a>")
        }
    }
}
