#!/usr/bin/env bash

# This script is a developer tool, to be used by maintainers
# to update '@since TODO' entries with actual Jenkins release versions.

set -o errexit
set -o nounset
set -o pipefail

# Needs bash 4+
declare -A commitsAndTags

IFS=$'\n'
for todo in $( git grep --line-number '@since TODO' -- *.java *.jelly *.js)
do
    #echo "TODO: $todo"
    file=$( echo "$todo" | cut -d : -f 1 )
    line=$( echo "$todo" | cut -d : -f 2 )

    echo "Analyzing $file:$line"

    lineSha=$( git blame --porcelain -L "$line,$line" "$file" | head -1 | cut -d ' ' -f 1 )
    echo -e "\tfirst sha: $lineSha"

    firstTag=$( git tag --sort=creatordate --contains "$lineSha" 'jenkins-*' | head -1 )

    if [[ -n $firstTag ]]; then
        echo -e "\tfirst tag was $firstTag"
        commitsAndTags[$lineSha]="$firstTag"
        echo -e "\tUpdating file in place"
        sedExpr="${line}s/@since TODO/@since ${firstTag//jenkins-/}/"
        sed -i.bak "$sedExpr" "$file"
        rm -f "$file.bak"
    else
        echo -e "\tNot updating file, no tag found. Normal if the associated PR/commit is not merged and released yet; otherwise make sure to fetch tags from jenkinsci/jenkins"
    fi
done

if [[ "${#commitsAndTags[@]}" -gt 0 ]] ; then
  echo ''
  echo "List of commits introducing new API and the first release they went in:"
  declare -A releases
  for commit in "${!commitsAndTags[@]}" ; do
    release="${commitsAndTags[$commit]}"
    releases[$release]=1
  done

  mapfile -t sortedReleases < <( sort <<<"${!releases[*]}" )

  for release in "${sortedReleases[@]}" ; do
      echo "* https://github.com/jenkinsci/jenkins/releases/tag/${release}"
      for commit in "${!commitsAndTags[@]}" ; do
        firstRelease="${commitsAndTags[$commit]}"
        if [[ "$release" = "$firstRelease" ]] ; then
          echo "  - https://github.com/jenkinsci/jenkins/commit/$commit"
        fi
      done
  done
fi
