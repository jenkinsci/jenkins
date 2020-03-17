package hudson.security.csrf.GlobalCrumbIssuerConfiguration

import hudson.security.csrf.CrumbIssuer

def f=namespace(lib.FormTagLib)
def all = CrumbIssuer.all()

if (!all.isEmpty()) {
    f.section(title: _("CSRF Protection")) {
        if (hudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION) {
            f.entry {
                p(raw(_('disabled')))
                p(_('unsupported'))
            }
        } else {
            f.dropdownDescriptorSelector(title: _("Crumb Issuer"), descriptors: all, field: 'crumbIssuer')
        }
    }
}
