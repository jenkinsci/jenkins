<!-- Comment:
A great PR typically begins with the line below.
Replace XXXXX with the numeric part of the issue ID you created in Jira.
Note that if you want your changes backported into LTS, you need to create a Jira issue. See https://www.jenkins.io/download/lts/#backporting-process for more information.
-->

See [JENKINS-XXXXX](https://issues.jenkins.io/browse/JENKINS-XXXXX).

<!-- Comment:
If the issue is not fully described in Jira, add more information here (justification, pull request links, etc.).

 * We do not require Jira issues for minor improvements.
 * Bug fixes should have a Jira issue to facilitate the backporting process.
 * Major new features should have a Jira issue.
-->

### Testing done

<!-- Comment:
Provide a clear description of how this change was tested.
At minimum this should include proof that a computer has executed the changed lines.
Ideally this should include an automated test or an explanation as to why this change has no tests.
Note that automated test coverage is less than complete, so a successful PR build does not necessarily imply that a computer has executed the changed lines.
If automated test coverage does not exist for the lines you are changing, you must describe the scenario(s) in which you manually tested the change.
For frontend changes, include screenshots of the relevant page(s) before and after the change.
For refactoring and code cleanup changes, exercise the code before and after the change and verify the behavior remains the same.
-->

### Proposed changelog entries

- human-readable text

<!-- Comment:
The changelog entry should be in the imperative mood; e.g., write "do this"/"return that" rather than "does this"/"returns that".
For examples, see: https://www.jenkins.io/changelog/

Do not include the Jira issue in the changelog entry.
Include the Jira issue in the description of the pull request so that the changelog generator can find it and include it in the generated changelog.

You may add multiple changelog entries if applicable by adding a new entry to the list, e.g.
- First changelog entry
- Second changelog entry
-->

### Proposed upgrade guidelines

N/A

<!-- Comment:
Leave the proposed upgrade guidelines in the pull request with the "N/A" value if no upgrade guidelines are needed.
The changelog generator relies on the presence of the upgrade guidelines section as part of its data extraction process.
-->

```[tasklist]
### Submitter checklist
- [ ] The Jira issue, if it exists, is well-described.
- [ ] The changelog entries and upgrade guidelines are appropriate for the audience affected by the change (users or developers, depending on the change) and are in the imperative mood (see [examples](https://github.com/jenkins-infra/jenkins.io/blob/master/content/_data/changelogs/weekly.yml)). Fill in the **Proposed upgrade guidelines** section only if there are breaking changes or changes that may require extra steps from users during upgrade.
- [ ] There is automated testing or an explanation as to why this change has no tests.
- [ ] New public classes, fields, and methods are annotated with `@Restricted` or have `@since TODO` Javadocs, as appropriate.
- [ ] New deprecations are annotated with `@Deprecated(since = "TODO")` or `@Deprecated(forRemoval = true, since = "TODO")`, if applicable.
- [ ] New or substantially changed JavaScript is not defined inline and does not call `eval` to ease future introduction of Content Security Policy (CSP) directives (see [documentation](https://www.jenkins.io/doc/developer/security/csp/)).
- [ ] For dependency updates, there are links to external changelogs and, if possible, full differentials.
- [ ] For new APIs and extension points, there is a link to at least one consumer.
```

### Desired reviewers

@mention

<!-- Comment:
If you need an accelerated review process by the community (e.g., for critical bugs), mention @jenkinsci/core-pr-reviewers.
-->

Before the changes are marked as `ready-for-merge`:

```[tasklist]
### Maintainer checklist
- [ ] There are at least two (2) approvals for the pull request and no outstanding requests for change.
- [ ] Conversations in the pull request are over, or it is explicit that a reviewer is not blocking the change.
- [ ] Changelog entries in the pull request title and/or **Proposed changelog entries** are accurate, human-readable, and in the imperative mood.
- [ ] Proper changelog labels are set so that the changelog can be generated automatically.
- [ ] If the change needs additional upgrade steps from users, the `upgrade-guide-needed` label is set and there is a **Proposed upgrade guidelines** section in the pull request title (see [example](https://github.com/jenkinsci/jenkins/pull/4387)).
- [ ] If it would make sense to backport the change to LTS, a Jira issue must exist, be a _Bug_ or _Improvement_, and be labeled as `lts-candidate` to be considered (see [query](https://issues.jenkins.io/issues/?filter=12146)).
```
