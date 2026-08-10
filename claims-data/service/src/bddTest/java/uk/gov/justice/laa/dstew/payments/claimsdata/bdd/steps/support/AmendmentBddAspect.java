package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.pda.PdaRequestField;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/** BDD-only amendment hooks for observability and failure injection. */
@Aspect
@Slf4j
public class AmendmentBddAspect {

  private static final String PDA_TECHNICAL_ERROR_CODE = "TECHNICAL_ERROR_PROVIDER_DETAILS_API";
  private static final String INVALID_AREA_OF_LAW_CODE = "INVALID_AREA_OF_LAW_FOR_PROVIDER";
  private static final String INVALID_CATEGORY_OF_LAW_CODE =
      "INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER";

  @Autowired private BddScenarioContext context;
  @Autowired private AmendmentDiffAssembler diffAssembler;

  @Around(
      "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment."
          + "ClaimAmendmentValidationService.validateAmendmentRequest(..)) && args(state)")
  public Object injectSyntheticEarlierValidationError(
      ProceedingJoinPoint joinPoint, ClaimAmendmentState state) throws Throwable {
    captureClassifierObservation(state);
    if (context.getInjectedValidationCode() != null) {
      state.addErrors(
          List.of(
              ClaimAmendmentValidationError.custom(
                  context.getInjectedValidationCode(),
                  context.getInjectedValidationCode(),
                  ValidationSeverity.ERROR,
                  HttpStatus.BAD_REQUEST,
                  false)));
    }
    if (context.isForceAreaOfLawValidationError()) {
      state.addErrors(
          List.of(
              ClaimAmendmentValidationError.custom(
                  INVALID_AREA_OF_LAW_CODE,
                  INVALID_AREA_OF_LAW_CODE,
                  ValidationSeverity.ERROR,
                  HttpStatus.BAD_REQUEST,
                  false)));
    }
    return joinPoint.proceed();
  }

  @Around(
      "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment."
          + "ClaimAmendmentCommitService.commit(..))")
  public Object failCommitAfterSuccess(ProceedingJoinPoint joinPoint) throws Throwable {
    context.setCommitAttempted(true);
    Object result = joinPoint.proceed();
    if (context.isFailCommitAfterSuccess()) {
      context.setCommitRolledBack(true);
      throw new IllegalStateException("BDD injected persistence failure after PDA success");
    }
    return result;
  }

  @Around(
      "execution(* uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment."
          + "ClaimAmendmentService.submitAmendment(..)) && args(claim, ..)")
  public Object observeAmendmentSubmission(ProceedingJoinPoint joinPoint, Claim claim)
      throws Throwable {
    long startNanos = System.nanoTime();
    context.setObservedClaimId(claim.getId());
    try {
      ClaimAmendmentResult result = (ClaimAmendmentResult) joinPoint.proceed();
      recordObservation(
          claim.getId(), result, Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
      return result;
    } catch (Throwable ex) {
      if (context.isFailCommitAfterSuccess()) {
        recordObservation(
            claim.getId(), null, Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
      }
      throw ex;
    }
  }

  private void recordObservation(UUID claimId, ClaimAmendmentResult result, long durationMillis) {
    String outcome = classifyOutcome(result);
    context.setLastObservedPdaOutcome(outcome);
    context.setLastObservedPdaDurationMillis(durationMillis);
    String monitoringEntry =
        "event=AMENDMENT_PDA_MONITOR claimId=%s outcome=%s durationMs=%d"
            .formatted(claimId, outcome, durationMillis);
    context.getObservedMonitoringEntries().add(monitoringEntry);
    if ("technical_failure".equals(outcome) || "timeout".equals(outcome)) {
      String logEntry =
          "event=AMENDMENT_PDA_FAILURE claimId=%s outcome=%s durationMs=%d"
              .formatted(claimId, outcome, durationMillis);
      log.warn(logEntry);
      context.getObservedLogEntries().add(logEntry);
    }
  }

  private String classifyOutcome(ClaimAmendmentResult result) {
    if (context.isFailCommitAfterSuccess() && context.isCommitRolledBack()) {
      return "technical_failure";
    }
    if (result == null) {
      return "technical_failure";
    }
    if (result.isSuccess()) {
      return "success";
    }
    List<String> codes =
        result.errors().stream().map(ClaimAmendmentValidationError::getCode).toList();
    if (codes.contains(PDA_TECHNICAL_ERROR_CODE)) {
      return "timeout".equals(context.getExpectedObservedOutcome())
          ? "timeout"
          : "technical_failure";
    }
    if (codes.contains(INVALID_AREA_OF_LAW_CODE) || codes.contains(INVALID_CATEGORY_OF_LAW_CODE)) {
      return "validation_failure";
    }
    return "rejected";
  }

  private void captureClassifierObservation(ClaimAmendmentState state) {
    if (!context.isClassifierScenarioActive()) {
      return;
    }
    ClaimStateSnapshot before = state.getBeforeState();
    ClaimStateSnapshot after = state.getPostAmendmentState();
    if (before == null || after == null) {
      return;
    }

    String beforeResolved = resolveEffectiveDate(before);
    String afterResolved = resolveEffectiveDate(after);
    context.setClassifierObservedResolvedEffectiveDateBefore(beforeResolved);
    context.setClassifierObservedResolvedEffectiveDateAfter(afterResolved);

    AmendmentDiff diff = diffAssembler.assemble(state);
    List<DiffEntry> changes = diff == null || diff.changes() == null ? List.of() : diff.changes();

    boolean officeChanged =
        context.isClassifierOfficeChanged()
            || (context.getPreAmendmentOffice() != null
                && context.getAmendmentOffice() != null
                && !context.getPreAmendmentOffice().equalsIgnoreCase(context.getAmendmentOffice()));

    boolean diffIndicatesPdaRelevant =
        changes.stream()
            .anyMatch(
                entry ->
                    !Objects.equals(entry.before(), entry.after())
                        && PdaRequestField.impactsPda(entry.fieldIdentifier(), after));
    boolean explicitNullCaseConcludedDateChange =
        context.getClassifierPatchFields().containsKey("case_concluded_date")
            && context.getClassifierPatchFields().get("case_concluded_date") == null
            && before.getCaseConcludedDate() != null;
    boolean pdaRelevant =
        officeChanged || diffIndicatesPdaRelevant || explicitNullCaseConcludedDateChange;
    context.setClassifierObservedPdaRelevant(pdaRelevant);

    if (officeChanged) {
      context.setClassifierObservedPdaSourceRuleReference("OFFICE_CODE_CHANGED");
    } else if (!pdaRelevant) {
      context.setClassifierObservedPdaSourceRuleReference("NO_PDA_RELEVANT_CHANGE");
    } else if (changes.stream()
        .anyMatch(
            entry ->
                "claim.feeCode".equals(entry.fieldIdentifier())
                    && !Objects.equals(entry.before(), entry.after()))) {
      context.setClassifierObservedPdaSourceRuleReference("FEE_CODE_CHANGED");
    } else {
      context.setClassifierObservedPdaSourceRuleReference("EFFECTIVE_DATE_CHANGED");
    }

    List<String> pricingFields = new ArrayList<>();
    boolean impactsPricingFromDiff =
        changes.stream()
            .filter(entry -> !Objects.equals(entry.before(), entry.after()))
            .anyMatch(
                entry -> {
                  boolean impacts =
                      before.getAreaOfLaw() != null
                          && FeeSchemeRequestField.impactsPricing(
                              entry.fieldIdentifier(), before.getAreaOfLaw());
                  if (impacts) {
                    pricingFields.add(toFeatureFieldName(entry.fieldIdentifier()));
                  }
                  return impacts;
                });
    if (officeChanged) {
      pricingFields.add("office_code");
    }
    boolean impactsPricing = impactsPricingFromDiff || officeChanged;
    context.setClassifierObservedImpactsPricing(impactsPricing);
    context.getClassifierObservedPricingImpactFields().clear();
    context.getClassifierObservedPricingImpactFields().addAll(pricingFields);
    context.setClassifierObservedFspSourceRuleReference(
        impactsPricing ? "FSP_REQUEST_BODY_FIELD_CHANGED" : "NO_FSP_REQUEST_BODY_FIELD_CHANGED");
    context.setClassifierObservedSourceRuleReference(
        context.getClassifierObservedPdaSourceRuleReference());
  }

  private static String resolveEffectiveDate(ClaimStateSnapshot snapshot) {
    if ("PROD".equals(snapshot.getFeeCode()) && snapshot.getCaseConcludedDate() != null) {
      return snapshot.getCaseConcludedDate().toString();
    }
    if (snapshot.getCaseStartDate() != null) {
      return snapshot.getCaseStartDate().toString();
    }
    if (snapshot.getRepresentationOrderDate() != null) {
      return snapshot.getRepresentationOrderDate().toString();
    }
    if (snapshot.getUniqueFileNumber() != null && snapshot.getUniqueFileNumber().length() >= 6) {
      String datePart = snapshot.getUniqueFileNumber().substring(0, 6);
      LocalDate parsed = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("ddMMyy"));
      if (parsed.getYear() < 2000) {
        parsed = parsed.plusYears(100);
      }
      return parsed.toString();
    }
    return null;
  }

  private static String toFeatureFieldName(String fieldIdentifier) {
    return switch (fieldIdentifier) {
      case "claim.feeCode" -> "fee_code";
      case "claim.caseStartDate" -> "case_start_date";
      case "claim.caseConcludedDate" -> "case_concluded_date";
      case "claim.representationOrderDate" -> "representation_order_date";
      case "claim.uniqueFileNumber" -> "ufn";
      case "claim.caseReferenceNumber" -> "client_reference";
      case "client.clientSurname" -> "client_surname";
      case "client.clientForename" -> "client_forename";
      default -> fieldIdentifier;
    };
  }
}
