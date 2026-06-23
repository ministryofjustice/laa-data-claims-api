package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.ASSESSED_TOTAL_INCL_VAT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_CONCLUDED_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_REFERENCE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CASE_START_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CATEGORY_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLAIM_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_DATE_OF_BIRTH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_FORENAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLIENT_SURNAME;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CMRH_ORAL_COUNT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.EXEMPTION_CRITERIA_SATISFIED;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.LINE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.NET_PROFIT_COSTS_AMOUNT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.PROVIDER_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.REPRESENTATION_ORDER_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.SUBMISSION_PERIOD;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.UNIQUE_CLIENT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.VERSION;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@DisplayName("ClaimStateSnapshotMapper Tests")
class ClaimStateSnapshotMapperTest {

  private final ClaimStateSnapshotMapper mapper = new ClaimStateSnapshotMapperImpl();

  private static Claim claim(Submission submission) {
    return Claim.builder()
        .id(CLAIM_ID)
        .submission(submission)
        .status(ClaimStatus.READY_TO_PROCESS)
        .version(VERSION)
        .hasAssessment(true)
        .isAmended(false)
        .lineNumber(LINE_NUMBER)
        .matterTypeCode(MATTER_TYPE_CODE)
        .scheduleReference(SCHEDULE_REFERENCE)
        .caseReferenceNumber(CASE_REFERENCE_NUMBER)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .caseStartDate(CASE_START_DATE)
        .caseConcludedDate(CASE_CONCLUDED_DATE)
        .representationOrderDate(REPRESENTATION_ORDER_DATE)
        .feeCode(FEE_CODE)
        .createdByUserId(CREATED_BY_USER_ID)
        .build();
  }

  private static Submission submission() {
    return Submission.builder()
        .id(SUBMISSION_ID)
        .bulkSubmissionId(UUID.randomUUID())
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        .submissionPeriod(SUBMISSION_PERIOD)
        .areaOfLaw(AreaOfLaw.CRIME_LOWER)
        .createdByUserId(CREATED_BY_USER_ID)
        .providerUserId(PROVIDER_USER_ID)
        .build();
  }

  @Test
  @DisplayName("maps all values from a fully-populated aggregate")
  void mapsFullyPopulatedAggregate() {
    Client client =
        Client.builder()
            .clientForename(CLIENT_FORENAME)
            .clientSurname(CLIENT_SURNAME)
            .clientDateOfBirth(CLIENT_DATE_OF_BIRTH)
            .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();
    ClaimCase claimCase =
        ClaimCase.builder()
            .caseId(CASE_ID)
            .exemptionCriteriaSatisfied(EXEMPTION_CRITERIA_SATISFIED)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();
    CalculatedFeeDetail feeDetail =
        CalculatedFeeDetail.builder()
            .categoryOfLaw(CATEGORY_OF_LAW)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();
    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .netProfitCostsAmount(NET_PROFIT_COSTS_AMOUNT)
            .isVatApplicable(true)
            .cmrhOralCount(CMRH_ORAL_COUNT)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();
    Assessment assessment =
        Assessment.builder().assessedTotalInclVat(ASSESSED_TOTAL_INCL_VAT).build();

    ClaimStateSnapshot snapshot =
        mapper.toSnapshot(
            claim(submission()), client, claimCase, summaryFee, feeDetail, assessment);

    assertThat(snapshot.getClaimId()).isEqualTo(CLAIM_ID);
    assertThat(snapshot.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(snapshot.getStatus()).isEqualTo(ClaimStatus.READY_TO_PROCESS);
    assertThat(snapshot.getVersion()).isEqualTo(VERSION);
    assertThat(snapshot.hasAssessment()).isTrue();
    assertThat(snapshot.isAmended()).isFalse();
    assertThat(snapshot.getAreaOfLaw()).isEqualTo(AreaOfLaw.CRIME_LOWER);
    assertThat(snapshot.getOfficeAccountNumber()).isEqualTo(OFFICE_ACCOUNT_NUMBER);
    assertThat(snapshot.getSubmissionPeriod()).isEqualTo(SUBMISSION_PERIOD);
    assertThat(snapshot.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    assertThat(snapshot.getFeeCode()).isEqualTo(FEE_CODE);
    assertThat(snapshot.getCategoryOfLaw()).isEqualTo(CATEGORY_OF_LAW);
    assertThat(snapshot.getClientForename()).isEqualTo(CLIENT_FORENAME);
    assertThat(snapshot.getClientSurname()).isEqualTo(CLIENT_SURNAME);
    assertThat(snapshot.getClientDateOfBirth()).isEqualTo(CLIENT_DATE_OF_BIRTH);
    assertThat(snapshot.getUniqueClientNumber()).isEqualTo(UNIQUE_CLIENT_NUMBER);
    assertThat(snapshot.getCaseId()).isEqualTo(CASE_ID);
    assertThat(snapshot.getExemptionCriteriaSatisfied()).isEqualTo(EXEMPTION_CRITERIA_SATISFIED);
    // claim-summary-fee fields
    assertThat(snapshot.getNetProfitCostsAmount()).isEqualByComparingTo(NET_PROFIT_COSTS_AMOUNT);
    assertThat(snapshot.getIsVatApplicable()).isTrue();
    assertThat(snapshot.getCmrhOralCount()).isEqualTo(CMRH_ORAL_COUNT);
    // read-only nested context
    assertThat(snapshot.getCalculatedFeeDetail()).isNotNull();
    assertThat(snapshot.getLatestAssessment()).isNotNull();
    assertThat(snapshot.getLatestAssessment().getAssessedTotalInclVat())
        .isEqualByComparingTo(ASSESSED_TOTAL_INCL_VAT);
  }

  @Test
  @DisplayName("missing associations leave the corresponding fields null")
  void missingAssociationsLeaveFieldsNull() {
    ClaimStateSnapshot snapshot =
        mapper.toSnapshot(
            claim(submission()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(snapshot.getClaimId()).isEqualTo(CLAIM_ID);
    assertThat(snapshot.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    // associations absent
    assertThat(snapshot.getClientForename()).isNull();
    assertThat(snapshot.getUniqueClientNumber()).isNull();
    assertThat(snapshot.getCaseId()).isNull();
    assertThat(snapshot.getExemptionCriteriaSatisfied()).isNull();
    assertThat(snapshot.getCategoryOfLaw()).isNull();
    assertThat(snapshot.getNetProfitCostsAmount()).isNull();
    assertThat(snapshot.getCalculatedFeeDetail()).isNull();
    assertThat(snapshot.getLatestAssessment()).isNull();
  }

  @Test
  @DisplayName("null submission leaves submission-context fields null without failing")
  void nullSubmissionIsNullSafe() {
    ClaimStateSnapshot snapshot =
        mapper.toSnapshot(
            claim(null),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(snapshot.getSubmissionId()).isNull();
    assertThat(snapshot.getAreaOfLaw()).isNull();
    assertThat(snapshot.getOfficeAccountNumber()).isNull();
    assertThat(snapshot.getSubmissionPeriod()).isNull();
  }

  @Test
  @DisplayName("a fully-populated aggregate leaves no snapshot field null (guards silent nulls)")
  void fullyPopulatedAggregateLeavesNoSnapshotFieldNull() throws IllegalAccessException {
    // Values without a named constant below are arbitrary, single-use non-null fillers: this test
    // only asserts that every snapshot field is populated, never their specific values.
    Submission submission = submission();

    Claim claim =
        Claim.builder()
            .id(CLAIM_ID)
            .submission(submission)
            .status(ClaimStatus.READY_TO_PROCESS)
            .version(VERSION)
            .hasAssessment(true)
            .isAmended(true)
            .scheduleReference(SCHEDULE_REFERENCE)
            .lineNumber(LINE_NUMBER)
            .caseReferenceNumber(CASE_REFERENCE_NUMBER)
            .uniqueFileNumber(UNIQUE_FILE_NUMBER)
            .caseStartDate(CASE_START_DATE)
            .caseConcludedDate(CASE_CONCLUDED_DATE)
            .matterTypeCode(MATTER_TYPE_CODE)
            .crimeMatterTypeCode("CMT-1")
            .feeSchemeCode("FS-1")
            .feeCode(FEE_CODE)
            .procurementAreaCode("PA-1")
            .accessPointCode("AP-1")
            .deliveryLocation("DL-1")
            .representationOrderDate(REPRESENTATION_ORDER_DATE)
            .suspectsDefendantsCount(2)
            .policeStationCourtAttendancesCount(3)
            .policeStationCourtPrisonId("PSC-1")
            .dsccNumber("DSCC-1")
            .maatId("MAAT-1")
            .prisonLawPriorApprovalNumber("PLPA-1")
            .dutySolicitor(true)
            .youthCourt(false)
            .schemeId("SCHEME-1")
            .mediationSessionsCount(4)
            .mediationTimeMinutes(120)
            .outreachLocation("OUT-1")
            .referralSource("REF-1")
            .createdByUserId(CREATED_BY_USER_ID)
            .build();

    Client client =
        Client.builder()
            .clientForename(CLIENT_FORENAME)
            .clientSurname(CLIENT_SURNAME)
            .clientDateOfBirth(CLIENT_DATE_OF_BIRTH)
            .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
            .clientPostcode("AB1 2CD")
            .genderCode("F")
            .ethnicityCode("ETH-1")
            .disabilityCode("DIS-1")
            .isLegallyAided(true)
            .clientTypeCode("CT-1")
            .homeOfficeClientNumber("HO-1")
            .claReferenceNumber("CLA-1")
            .claExemptionCode("CLAEX-1")
            .client2Forename("Grace")
            .client2Surname("Hopper")
            .client2DateOfBirth(LocalDate.of(1985, 3, 10))
            .client2Ucn("UCN-2")
            .client2Postcode("EF3 4GH")
            .client2GenderCode("F")
            .client2EthnicityCode("ETH-2")
            .client2DisabilityCode("DIS-2")
            .client2IsLegallyAided(false)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();

    ClaimCase claimCase =
        ClaimCase.builder()
            .caseId(CASE_ID)
            .uniqueCaseId("UCID-1")
            .caseStageCode("CS-1")
            .stageReachedCode("SR-1")
            .standardFeeCategoryCode("SFC-1")
            .outcomeCode("OUT-1")
            .designatedAccreditedRepresentativeCode("DAR-1")
            .isPostalApplicationAccepted(true)
            .isClient2PostalApplicationAccepted(false)
            .mentalHealthTribunalReference("MHTR-1")
            .isNrmAdvice(true)
            .followOnWork("FOW-1")
            .transferDate(LocalDate.of(2026, 3, 1))
            .exemptionCriteriaSatisfied(EXEMPTION_CRITERIA_SATISFIED)
            .exceptionalCaseFundingReference("ECF-1")
            .isLegacyCase(false)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();

    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .adviceTime(10)
            .travelTime(20)
            .waitingTime(30)
            .netProfitCostsAmount(NET_PROFIT_COSTS_AMOUNT)
            .netDisbursementAmount(new BigDecimal("11.00"))
            .netCounselCostsAmount(new BigDecimal("12.00"))
            .disbursementsVatAmount(new BigDecimal("2.40"))
            .travelWaitingCostsAmount(new BigDecimal("13.00"))
            .netWaitingCostsAmount(new BigDecimal("14.00"))
            .isVatApplicable(true)
            .isToleranceApplicable(false)
            .priorAuthorityReference("PAR-1")
            .isLondonRate(true)
            .adjournedHearingFeeAmount(5)
            .isAdditionalTravelPayment(false)
            .costsDamagesRecoveredAmount(new BigDecimal("15.00"))
            .meetingsAttendedCode("MA-1")
            .detentionTravelWaitingCostsAmount(new BigDecimal("16.00"))
            .jrFormFillingAmount(new BigDecimal("17.00"))
            .isEligibleClient(true)
            .courtLocationCode("CL-1")
            .adviceTypeCode("AT-1")
            .medicalReportsCount(1)
            .isIrcSurgery(false)
            .surgeryDate(LocalDate.of(2026, 4, 1))
            .surgeryClientsCount(2)
            .surgeryMattersCount(3)
            .cmrhOralCount(CMRH_ORAL_COUNT)
            .cmrhTelephoneCount(1)
            .aitHearingCentreCode("AIT-1")
            .isSubstantiveHearing(true)
            .hoInterview(1)
            .localAuthorityNumber("LA-1")
            .createdByUserId(CREATED_BY_USER_ID)
            .build();

    CalculatedFeeDetail feeDetail =
        CalculatedFeeDetail.builder()
            .categoryOfLaw(CATEGORY_OF_LAW)
            .createdByUserId(CREATED_BY_USER_ID)
            .build();
    Assessment assessment =
        Assessment.builder().assessedTotalInclVat(ASSESSED_TOTAL_INCL_VAT).build();

    ClaimStateSnapshot snapshot =
        mapper.toSnapshot(claim, client, claimCase, summaryFee, feeDetail, assessment);

    // Reflectively assert every (non-static, non-synthetic) field is populated. This converts the
    // latent silent-null risk from unmappedTargetPolicy = IGNORE into an explicit, future-proof
    // guard: a newly added snapshot field with no matching source will fail this test.
    for (Field field : ClaimStateSnapshot.class.getDeclaredFields()) {
      if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      field.setAccessible(true);
      assertThat(field.get(snapshot))
          .as("snapshot field '%s' should be mapped from a source", field.getName())
          .isNotNull();
    }
  }
}
