inherit java-pkg-2 rpm

DESCRIPTION="Extensible continuous integration server"
# We are using rpm package here, because we want file with version.
SRC_URI="http://hudson-labs.org/redhat/RPMS/noarch/hudson-${PV}-1.1.noarch.rpm"
HOMEPAGE="http://hudson-ci.org/"
LICENSE="MIT"
SLOT="0"
KEYWORDS="~x86"
IUSE=""

RDEPEND=">=virtual/jdk-1.5"

src_unpack() {
    rpm_src_unpack ${A}
}

pkg_setup() {
    enewgroup hudson
    enewuser hudson -1 /bin/bash /var/lib/hudson hudson
}

src_install() {
    keepdir /var/run/hudson /var/log/hudson 
    keepdir /var/lib/hudson/home /var/lib/hudson/backup

    insinto /usr/lib/hudson
    doins usr/lib/hudson/hudson.war

    newinitd "${FILESDIR}/init.sh" hudson
    newconfd "${FILESDIR}/conf" hudson

    fowners hudson:hudson /var/run/hudson /var/log/hudson /var/lib/hudson /var/lib/hudson/home /var/lib/hudson/backup
}
