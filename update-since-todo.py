#!/usr/bin/env python3

import argparse
import fileinput
import io
import os
import shutil
import subprocess

GIT = shutil.which("git")


def update_file(file, lineno, old, new):
    """
    Replace all occurrences of the old substring by the new substring.

    :param file: The file to update.
    :param lineno: The line number to update.
    :param old: The old substring.
    :param new: The new substring.
    """
    print("* Updating file in place")
    with fileinput.FileInput(file, inplace=True) as f:
        for line in f:
            if f.lineno() == lineno and old in line:
                print(line.replace(old, new), end="")
            else:
                print(line, end="")


def analyze_file(file, lineno, commits_and_tags, dry_run=False):
    """
    Analyze the given file.

    :param file: The file to analyze.
    :param lineno: The line number to analyze.
    :param commits_and_tags: The output dictionary mapping commits to release tags.
    :param dry_run: Whether or not this is a dry run.
    """
    print(f"Analyzing {file}:{lineno}")
    line_sha = (
        subprocess.check_output(
            [GIT, "blame", "--porcelain", "-L", f"{lineno},{lineno}", file], text=True
        )
        .split("\n", 1)[0]
        .split(" ", 1)[0]
    )
    print(f"* first sha: {line_sha}")
    first_tag = subprocess.check_output(
        [GIT, "tag", "--sort=creatordate", "--contains", line_sha, "jenkins-*"],
        text=True,
    ).split("\n", 1)[0]
    if first_tag:
        print(f"* first tag was {first_tag}")
        commits_and_tags[line_sha] = first_tag
        if not dry_run:
            since_version = first_tag.replace("jenkins-", "")
            update_file(
                file,
                int(lineno),
                "@since TODO",
                f"@since {since_version}",
            )
            update_file(
                file,
                int(lineno),
                '@Deprecated(since = "TODO")',
                f'@Deprecated(since = "{since_version}")',
            )
            update_file(
                file,
                int(lineno),
                '@RestrictedSince("TODO")',
                f'@RestrictedSince("{since_version}")',
            )

    else:
        print(
            "* Not updating file, no tag found. "
            "Normal if the associated PR/commit is not merged and released yet; "
            "otherwise make sure to fetch tags from jenkinsci/jenkins"
        )
    print() # Add a newline for markdown rendering


def analyze_files(commits_and_tags, dry_run=False):
    """
    Analyze all files in the repository.

    :param commits_and_tags: The output dictionary mapping commits to release tags.
    :param dry_run: Whether or not this is a dry run.
    """
    cmd = [
        GIT,
        "grep",
        "--line-number",
        "-E",
        '@since TODO|@Deprecated\\(since = "TODO"\\)|@RestrictedSince\\("TODO"\\)',
        "--",
        "*.java",
        "*.jelly",
        "*.js",
    ]

    is_ci = "CI" in os.environ
    if is_ci:
        print("<details><summary>Detailed output</summary>\n\n")
    processed = 0
    with subprocess.Popen(cmd, stdout=subprocess.PIPE) as proc:
        for line in io.TextIOWrapper(proc.stdout):
            parts = line.rstrip().split(":", 2)
            analyze_file(parts[0], parts[1], commits_and_tags, dry_run=dry_run)
            processed += 1
        retcode = proc.wait()
        if retcode not in (0, 1):
            raise subprocess.CalledProcessError(retcode, cmd)
        if processed == 0:
            print("No '@since TODO', '@Deprecated(since = \"TODO\")', or '@RestrictedSince(\"TODO\")' tags found.")
    if is_ci:
        print("</details>\n")


def display_results(commits_and_tags):
    """
    Display the results of the analysis.

    :param commits_and_tags: The output dictionary mapping commits to release tags.
    """
    print("List of commits introducing new API and the first release they went in:")
    releases = {release for release in commits_and_tags.values()}
    for release in sorted(releases):
        print(f"* https://github.com/jenkinsci/jenkins/releases/tag/{release}")
        for commit, first_release in commits_and_tags.items():
            if release == first_release:
                print(f"  - https://github.com/jenkinsci/jenkins/commit/{commit}")


def main():
    """
    Update '@since TODO', '@Deprecated(since = "TODO")', and '@RestrictedSince("TODO")' entries
    with actual Jenkins release versions.

    This script is a developer tool, to be used by maintainers.
    """
    parser = argparse.ArgumentParser(
        description="Update '@since TODO', '@Deprecated(since = \"TODO\")', and '@RestrictedSince(\"TODO\")' entries "
                    "with actual Jenkins release versions. "
    )
    parser.add_argument("-n", "--dry-run", help="Dry run", action="store_true")
    args = parser.parse_args()

    commits_and_tags = {}
    analyze_files(commits_and_tags, dry_run=args.dry_run)
    if commits_and_tags:
        display_results(commits_and_tags)


if __name__ == "__main__":
    main()
