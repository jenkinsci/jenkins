#!/bin/zsh -ex

function checkout() {
    groupId=$1
    artifactId=$2
    version=$3
    
    cp ~/.maven/repository/$groupId/jars/$artifactId-$version*.*         $groupId/jars
    cp ~/.maven/repository/$groupId/poms/$artifactId-$version*.*         $groupId/poms
    cp ~/.maven/repository/$groupId/java-sources/$artifactId-$version*.* $groupId/jars

    m2dest=~/.m2/repository/$(echo $groupId | tr '.' '/')/$artifactId/$version
    mkdir $m2dest || true
    cp ~/.maven/repository/$groupId/jars/$artifactId-$version*.*         $m2dest
    cp ~/.maven/repository/$groupId/poms/$artifactId-$version*.*         $m2dest
    cp ~/.maven/repository/$groupId/java-sources/$artifactId-$version*.* $m2dest
}

case $1 in
stapler)
        tiger
        pushd /kohsuke/projects/stapler/stapler
        staplerDir=$PWD
        maven jar source jar:install source:install
        popd
        checkout org.kohsuke.stapler stapler $(show-pom-version $staplerDir/project.xml)
        ;;

jelly)
        # build with 1.4
        mantis
        
        pushd ../../../jelly/jelly-tags/define/
        jellyDir=$PWD
        maven -Dmaven.test.skip=true clean jar source jar:install source:install
        popd
        checkout commons-jelly commons-jelly-tags-define $(show-pom-version $jellyDir/project.xml)
        
        pushd ../../../jelly
        jellyDir=$PWD
        maven -Dmaven.test.skip=true clean jar source jar:install source:install
        popd
        checkout commons-jelly commons-jelly $(show-pom-version $jellyDir/project.xml)
        ;;

jexl)
        mantis

        pushd ../../../jexl
        jexlDir=$PWD
        maven -Dmaven.test.skip=true jar source jar:install source:install
        popd
        checkout commons-jexl commons-jexl $(show-pom-version $jexlDir/project.xml)
        ;;
esac
