### Model

Gemini 2.5 Pro w/Github Copilot

### Prompts

I'm trying to solve the issue on this URL: https://issues.jenkins.io/projects/JENKINS/issues/JENKINS-75492

The description is as follows:
```
Currently the api tokens for users are valid forever.

It would be good if it is possible to define an expiration date for new tokens with some predefined ranges, unlimited and the possibility to define a custom date.

It should be possible to extend the expiration date via the UI.

Users should probably be warned about expiring tokens.
```

Create a plan with the documentation I have sent you to work on this issue. 

DO NOT MODIFY ANY CODE.

Provide options for solving the issue if you can come up with multiple approaches.

---

Start working on the TODO list. Before you change any code, I want you to insert all the TODOs in the issue-plan.md file under the Respose heading

---

Use Approach 1. As you make changes to the codebase, add documentation under each item of the TODO list inside the `issue-plan.md` file


### Response

Here is a detailed plan for implementing API token expiration, as requested in issue JENKINS-75492.

### 1. Investigation and Discovery

The first step is to thoroughly understand the current implementation of API tokens in the Jenkins core.

*   **Locate Core Components:** I will identify the key Java classes and Jelly view files related to API token management. The primary focus will be on the `core` module.
    *   **Data Model:** I'll look for classes that define what a token is and how it's associated with a `User`. A likely candidate is `hudson.model.User.ApiTokenProperty`.
    *   **UI Files:** I will find the `.jelly` files responsible for rendering the token management UI on a user's configuration page.
    *   **Authentication Logic:** I will trace the code path for authenticating a request using an API token to find where the validation occurs.

I have identified the following key classes:
- `jenkins.security.ApiTokenProperty`: This class is a `UserProperty` that manages API tokens for a user. It holds a reference to an `ApiTokenStore`.
- `jenkins.security.apitoken.ApiTokenStore`: This class appears to be the main component for handling the lifecycle of API tokens. It contains a list of `HashedToken` objects.
- `jenkins.security.apitoken.ApiTokenStore.HashedToken`: This is an inner class within `ApiTokenStore` that represents a single, hashed API token. It contains the token's UUID, name, creation date, and the hashed value.
- `jenkins.security.apitoken.ApiTokenStats`: This class is responsible for tracking usage statistics for each token, such as the last time it was used and the total number of times it has been used.
- `jenkins.security.ApiTokenProperty.TokenInfoAndStats`: This inner class is a data transfer object used to display token information in the UI. It combines information from `HashedToken` and `ApiTokenStats`.

### 2. Proposed Solution Approaches

I have identified two potential approaches for adding the expiration functionality.

#### Approach 1: Simple Date Field

This approach involves adding a new field to the existing token data structure to store the expiration timestamp.

*   **Data Model:** Add a `long expirationDate` field to the class representing a token. A value of `0` or `-1` could represent a token that never expires.
    *   In `jenkins.security.ApiTokenProperty.TokenInfoAndStats`, I added a `public final Date expirationDate;` field and updated the constructor.
    *   In `jenkins.security.apitoken.ApiTokenStore.HashedToken`, I added a `private final Date expirationDate;` field and a new constructor to set it.
*   **Pros:**
    *   Simple to implement.
    *   Minimal changes to the existing data structure.
*   **Cons:**
    *   Less flexible if more complex expiration logic is needed in the future.

#### Approach 2: Extensible Expiration Strategy

This approach involves designing a more flexible system for handling different types of expiration policies.

*   **Data Model:**
    *   Create a new interface, for example, `TokenExpirationStrategy`.
    *   Implement different strategies:
        *   `NoExpirationStrategy`: For tokens that never expire.
        *   `FixedTermExpirationStrategy`: For tokens that expire after a specific duration (e.g., 30 days) or on a specific date.
*   **Pros:**
    *   Highly extensible for future requirements (e.g., sliding expiration, conditional expiration).
    *   Cleaner separation of concerns.
*   **Cons:**
    *   More complex to implement initially.

### 3. UI/View Changes

The user interface will need to be updated to allow users to manage token expiration.

*   **Token Creation:**
    *   On the "New Token" creation form, add a set of radio buttons or a dropdown for selecting an expiration duration:
        *   Predefined options: "30 days", "90 days", "1 year", "Unlimited".
        *   A "Custom" option that reveals a date picker.
*   **Token Display:**
    *   In the list of existing tokens, add a column to display the expiration date.
    *   Visually indicate tokens that are expired or nearing expiration (e.g., with a different color or an icon).
*   **Token Extension:**
    *   Add an "Extend" button or link next to each token that has an expiration date. Clicking this would allow the user to set a new expiration date.

### 4. Backend Logic Changes

The backend will need to be modified to enforce the expiration rules.

*   **Token Generation:** Update the logic that creates new tokens to accept and store the expiration information based on the user's selection in the UI.
*   **Authentication:** Modify the API token authentication filter to:
    1.  Retrieve the token's expiration date.
    2.  Compare it with the current time.
    3.  Reject the authentication attempt if the token is expired.
*   **Extension Endpoint:** Create a new backend method (exposed via Stapler) to handle requests for extending a token's expiration date.

### 5. User Notification System

To prevent unexpected token expiry, a notification system should be implemented.

*   **Administrative Monitor:** Create a new `AdministrativeMonitor` that scans for tokens that will expire soon (e.g., within the next 7 days). This monitor would display a warning in the "Manage Jenkins" section, visible to administrators.
*   **User-Specific Notification:** Display a notification on a user's personal page or profile, warning them about their own expiring tokens.
*   **Email Notifications (Optional):** A periodic background task (`PeriodicWork`) could be created to scan for expiring tokens and send email notifications to the respective users, if an email plugin is configured.

### 6. Documentation

Finally, I will create a technical design document that consolidates all the details from the steps above. This document will serve as a guide for the implementation phase. It will include:

*   A summary of the investigation.
*   A comparison of the proposed approaches with a recommendation.
*   Detailed descriptions of the required changes to the data model, UI, and backend.
*   A plan for the notification system.


