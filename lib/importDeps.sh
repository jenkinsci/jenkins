#!/bin/zsh -ex

function checkout() {
    groupId=$1
    artifactId=$2
    version=$3
    
    for i in jars poms java-sources
    do
      cp ~/.maven/repository/$groupId/$i/$artifactId-$version*.* $groupId/$i
    done
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
