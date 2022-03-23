package hudson.security.csrf.GlobalCrumbIssuerConfiguration

import hudson.security.csrf.CrumbIssuer

def f=namespace(lib.FormTagLib)
def all = CrumbIssuer.all()

if (!all.isEmpty()) {
    f.section(title: gettext("CSRF Protection")) {
        if (hudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION) {
            f.entry {
                p(raw(gettext('disabled')))
                p(gettext('unsupported'))
            }
        } else {
            f.dropdownDescriptorSelector(title: gettext("Crumb Issuer"), descriptors: all, field: 'crumbIssuer')
        }
    }
}
