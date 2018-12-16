See [JENKINS-XXXXX](https://issues.jenkins-ci.org/browse/JENKINS-XXXXX).

<!-- Comment:

IMPORTANT: ALL checkboxes below are a MUST.
They are required for a PR to be analyzed, you must check each checkbox, and if not applicable simply ~~cross~~ the full line to show you've taken it in account, ideally add a short explanation as to why it's irrelevant if not obvious.

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

### Submitter checklist

- [ ] JIRA issue is well described
- [ ] Changelog entry appropriate for the audience affected by the change (users or developer, depending on the change). [Examples](https://github.com/jenkins-infra/jenkins.io/blob/master/content/_data/changelogs/weekly.yml)
      * Use the `Internal: ` prefix if the change has no user-visible impact (API, test frameworks, etc.)
- [ ] Appropriate autotests or explanation to why this change has no tests
- [ ] For dependency updates: links to external changelogs and, if possible, full diffs
- [ ] After PR automated build has run, i.e. a few hours, check the result.
      If failing, the **submitter** is responsible for checking why and provide either additional fixes, or clear explanations as to why the failure looks unrelated.

<!-- For new API and extension points: Link to the reference implementation in open-source (or example in Javadoc) -->

### Desired reviewers

@mention

<!-- Comment:
If you need an accelerated review process by the community (e.g., for critical bugs), mention @jenkinsci/code-reviewers
-->
