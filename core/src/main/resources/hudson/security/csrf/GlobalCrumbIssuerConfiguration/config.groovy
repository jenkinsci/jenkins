package hudson.security.csrf.GlobalCrumbIssuerConfiguration

import hudson.security.csrf.CrumbIssuer
import hudson.security.csrf.DefaultCrumbIssuer

def f = namespace(lib.FormTagLib)
def all = CrumbIssuer.all()
def disableCsrf =
    hudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION

def onlyDefaultIssuer =
    all.size() == 1 && all[0].clazz == DefaultCrumbIssuer

def showCsrfConfig = !onlyDefaultIssuer || disableCsrf

if (showCsrfConfig) {
    f.section(title: _("CSRF Protection")) {
        if (disableCsrf) {
            f.entry {
                p(raw(_('disabled')))
                p(_('unsupported'))
            }
        } else {
            f.dropdownDescriptorSelector(
                title: _("Crumb Issuer"),
                descriptors: all,
                field: 'crumbIssuer'
            )
        }
    }
}
