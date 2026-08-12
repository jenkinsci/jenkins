# SVN Revisions Collapse & Ordering Implementation Plan

## Goal Description
The goal is to improve the UX of the build/job details page by:
1. Collapsing the SVN revisions/locations list when there are more than 5 revisions, adding an expand/collapse toggle.
2. Reordering the build details page so the most useful debugging information is at the top:
   - Test Failures
   - Changelog
   - SVN Revisions
   - Other build info

## Investigation Findings
During my investigation of the `c:\jenkins\jenkins` workspace, I found that this workspace contains the **Jenkins Core** source code, not the Subversion plugin.
1. **Ordering**: The build details page is rendered by `core\src\main\resources\hudson\model\Run\index.jelly`. Currently, all Action summaries (including Test Failures and SVN Revisions) are rendered in a loop, followed by the changelog (included via `main.jelly`). Reordering these would require modifying this jelly file to explicitly filter and order actions, which might violate Jenkins' plugin-agnostic architecture if we hardcode SVN/Test plugins in core.
2. **SVN Revisions UI**: The UI component that renders "SVN revisions/locations" (typically `SubversionTagAction/summary.jelly`) is part of the `subversion-plugin`, which is **not present** in this workspace. Searches for SVN-related UI files yielded no results.

> [!WARNING]
> **Missing Subversion Plugin**
> The component responsible for displaying SVN revisions is not present in the provided `c:\jenkins\jenkins` workspace (which only contains Jenkins Core). Therefore, I cannot implement the collapse behavior without access to the Subversion plugin's source code.

## Open Questions

> [!IMPORTANT]
> **1. Workspace / Subversion Plugin Location**
> The `subversion-plugin` is not in this repository. Is it possible you intended for me to work on the `jenkinsci/subversion-plugin` repository instead? If so, please provide access to it or guide me to the correct directory if it's hidden.

> [!IMPORTANT]
> **2. Jenkins Core vs Plugin Architecture for Ordering**
> To reorder Test Failures, Changelog, and SVN Revisions, we would typically need to modify `Run/index.jelly` in Jenkins Core to hardcode the order of specific plugin actions. This is generally discouraged in Jenkins core. Do you still want me to implement hardcoded reordering in `Run/index.jelly`, or is there an existing ordering API / Extension Point you prefer me to use?

## Proposed Changes (Pending Answers)
If we proceed with modifying Jenkins core to hardcode the order, and assuming you can provide the subversion-plugin for the collapse feature:

### Jenkins Core (`hudson\model\Run\index.jelly`)
- Modify the `it.allActions` loop to separate actions into multiple blocks (Test Result, SVN, Other).
- Move the `<st:include page="main.jelly" />` (which contains Changelog) between Test Results and SVN Revisions.

### Subversion Plugin (Location TBA)
- Modify `summary.jelly` (e.g., in `hudson.scm.SubversionTagAction`) to count revisions.
- Add `<j:if>` statements to check if the count > 5.
- Add HTML/JS for an expand/collapse `<details>` or toggle button.

## Verification Plan
- Manual verification: Build a job with 0, 1-5, and >5 SVN revisions and verify the UI collapse behavior.
- Manual verification: Verify the ordering of Test Failures -> Changelog -> SVN Revisions.
- Automated Tests: Run existing UI/Jelly tests in both Jenkins core and the SVN plugin.
