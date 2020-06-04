<!-- Comment: 
A great PR typically begins with the line below.
Replace XXXXX with the numeric part of the issue's id you created on JIRA.
Please note that if you want your changes backported into LTS, you will need to create a JIRA ticket for it. Read https://jenkins.io/download/lts/#backporting-process for more.
-->
See [JENKINS-XXXXX](https://issues.jenkins-ci.org/browse/JENKINS-XXXXX).

<!-- Comment: 
If the issue is not fully described in the ticket, add more information here (justification, pull request links, etc.).

 * We do not require JIRA issues for minor improvements.
 * Bugfixes should have a JIRA issue (backporting process).
 * Major new features should have a JIRA issue reference.
-->

### Proposed changelog entries

* Entry 1: Issue, Human-readable Text
* ...

<!-- Comment: 
The changelogs will be integrated by the core maintainers after the merge.  See the changelog examples here: https://jenkins.io/changelog/ -->

### Proposed upgrade guidelines

N/A

### Submitter checklist

- [ ] (If applicable) Jira issue is well described
- [ ] Changelog entries and upgrade guidelines are appropriate for the audience affected by the change (users or developer, depending on the change). [Examples](https://github.com/jenkins-infra/jenkins.io/blob/master/content/_data/changelogs/weekly.yml)
  * Fill-in the `Proposed changelog entries` section only if there are breaking changes or other changes which may require extra steps from users during the upgrade
- [ ] Appropriate autotests or explanation to why this change has no tests
- [ ] For dependency updates: links to external changelogs and, if possible, full diffs

<!-- For new API and extension points: Link to the reference implementation in open-source (or example in Javadoc) -->

### Desired reviewers

@mention

<!-- Comment:
If you need an accelerated review process by the community (e.g., for critical bugs), mention @jenkinsci/code-reviewers
-->

### Maintainer checklist

Before the changes are marked as `ready-for-merge`: 

- [ ] There are at least 2 approvals for the pull request and no outstanding requests for change
- [ ] Conversations in the pull request are over OR it is explicit that a reviewer does not block the change
- [ ] Changelog entries in the PR title and/or `Proposed changelog entries` are correct
- [ ] Proper changelog labels are set so that the changelog can be generated automatically
- [ ] If the change needs additional upgrade steps from users, `upgrade-guide-needed` label is set and there is a `Proposed upgrade guidelines` section in the PR title. ([example](https://github.com/jenkinsci/jenkins/pull/4387))
- [ ] If it would make sense to backport the change to LTS, a Jira issue must exist, be a _Bug_ or _Improvement_, and be labeled as `lts-candidate` to be considered (see [query](https://issues.jenkins-ci.org/issues/?filter=12146)).
