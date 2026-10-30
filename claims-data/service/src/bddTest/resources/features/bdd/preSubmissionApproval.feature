@Regression
@submission
@preSubmissionApproval
Feature: Provider approval of a validated submission

  Background:
    Given the pre-submission feature flag is enabled

  @smoke
  Scenario: Provider approval moves the submission to final validation and records who approved it
    Given a submission is awaiting provider approval
    When the provider approves the submission
    Then the submission approval fields are recorded
