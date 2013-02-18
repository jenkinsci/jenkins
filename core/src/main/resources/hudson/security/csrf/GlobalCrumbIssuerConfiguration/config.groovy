package hudson.security.csrf.GlobalCrumbIssuerConfiguration

import hudson.security.csrf.CrumbIssuer

def f=namespace(lib.FormTagLib)
def all = CrumbIssuer.all()

if (!all.isEmpty()) {
    f.optionalBlock(field:"csrf", title:_("Prevent Cross Site Request Forgery exploits"), checked: app.useCrumbs ) {
        f.entry(title:_("Crumbs")) {
            table(style:"width:100%") {
                f.descriptorRadioList(title:_("Crumb Algorithm"), varName:"issuer", instance:app.crumbIssuer, descriptors:all)
            }
        }
    }
}
