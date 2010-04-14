inherit java-pkg-2 rpm

DESCRIPTION="Extensible continuous integration server"
SRC_URI="http://hudson-ci.org/redhat/RPMS/noarch/hudson-${PV}-1.1.noarch.rpm"
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
    dodir /var/lib/hudson
    dodir /var/log/hudson
    dodir /var/run/hudson

    insinto /usr/lib/hudson
    doins usr/lib/hudson/hudson.war

    newinitd "${FILESDIR}/init" hudson
    newconfd "${FILESDIR}/conf" hudson

    fowners hudson:hudson /var/lib/hudson
    fowners hudson:hudson /var/run/hudson
    fowners hudson:hudson /var/log/hudson
}
