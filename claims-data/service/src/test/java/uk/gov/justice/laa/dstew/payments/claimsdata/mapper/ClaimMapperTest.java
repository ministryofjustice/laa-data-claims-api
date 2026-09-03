package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponseV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.DerivedClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimMapper tests")
class ClaimMapperTest {

  @InjectMocks private final ClaimMapperImpl mapper = new ClaimMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Spy private GlobalDateTimeMapper globalDateTimeMapper = new GlobalDateTimeMapperImpl();

  @Test
  void toClaim_nullInput_returnsNull() {
    assertNull(mapper.toClaim(null));
  }

  @Test
  void toSubmissionClaim_mapsAllFields() {
    final ClaimPost post = ClaimsDataTestUtil.getClaimPost(CASE_REFERENCE);

    final Claim entity = mapper.toClaim(post);

    assertNotNull(entity);
    assertEquals(post.getIsDutySolicitor(), entity.getDutySolicitor());
    assertEquals(post.getIsYouthCourt(), entity.getYouthCourt());
    assertEquals(post.getStatus(), entity.getStatus());
    assertEquals(post.getScheduleReference(), entity.getScheduleReference());
    assertEquals(post.getLineNumber(), entity.getLineNumber());
    assertEquals(post.getCaseReferenceNumber(), entity.getCaseReferenceNumber());
    assertEquals(post.getUniqueFileNumber(), entity.getUniqueFileNumber());
    assertEquals(
        post.getCaseStartDate(),
        entity.getCaseStartDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    assertEquals(
        post.getCaseConcludedDate(),
        entity.getCaseConcludedDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    assertEquals(post.getMatterTypeCode(), entity.getMatterTypeCode());
    assertEquals(post.getCrimeMatterTypeCode(), entity.getCrimeMatterTypeCode());
    assertEquals(post.getFeeSchemeCode(), entity.getFeeSchemeCode());
    assertEquals(post.getFeeCode(), entity.getFeeCode());
    assertEquals(post.getProcurementAreaCode(), entity.getProcurementAreaCode());
    assertEquals(post.getAccessPointCode(), entity.getAccessPointCode());
    assertEquals(post.getDeliveryLocation(), entity.getDeliveryLocation());
    assertEquals(
        post.getRepresentationOrderDate(),
        entity.getRepresentationOrderDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    assertEquals(post.getSuspectsDefendantsCount(), entity.getSuspectsDefendantsCount());
    assertEquals(
        post.getPoliceStationCourtAttendancesCount(),
        entity.getPoliceStationCourtAttendancesCount());
    assertEquals(post.getPoliceStationCourtPrisonId(), entity.getPoliceStationCourtPrisonId());
    assertEquals(post.getDsccNumber(), entity.getDsccNumber());
    assertEquals(post.getMaatId(), entity.getMaatId());
    assertEquals(post.getPrisonLawPriorApprovalNumber(), entity.getPrisonLawPriorApprovalNumber());
    assertEquals(post.getSchemeId(), entity.getSchemeId());
    assertEquals(post.getMediationSessionsCount(), entity.getMediationSessionsCount());
    assertEquals(post.getMediationTimeMinutes(), entity.getMediationTimeMinutes());
    assertEquals(post.getOutreachLocation(), entity.getOutreachLocation());
    assertEquals(post.getReferralSource(), entity.getReferralSource());
  }

  @Test
  void toClaimResponse_nullInput_returnsNull() {
    assertNull(mapper.toClaimResponse(null));
  }

  @Test
  void toClaimResponseV2_nullInput_returnsNull() {
    assertNull(mapper.toClaimResponseV2(null));
  }

  @Test
  void toClaimFields_mapsAllResponse() {
    UUID submissionId = Uuid7.timeBasedUuid();
    final Claim entity =
        Claim.builder()
            .dutySolicitor(true)
            .youthCourt(false)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference("SCH123")
            .lineNumber(5)
            .caseReferenceNumber(CASE_REFERENCE)
            .uniqueFileNumber("UFN123")
            .caseStartDate(LocalDate.now())
            .caseConcludedDate(LocalDate.now().plusDays(1))
            .matterTypeCode("MTC")
            .crimeMatterTypeCode("CMTC")
            .feeSchemeCode("FSC")
            .feeCode("FC")
            .procurementAreaCode("PAC")
            .accessPointCode("APC")
            .deliveryLocation("DEL")
            .representationOrderDate(LocalDate.now().minusDays(2))
            .suspectsDefendantsCount(3)
            .policeStationCourtAttendancesCount(4)
            .policeStationCourtPrisonId("PSCPI")
            .dsccNumber("DSCC123")
            .maatId("987654321L")
            .prisonLawPriorApprovalNumber("PLPAN")
            .schemeId("12")
            .mediationSessionsCount(2)
            .mediationTimeMinutes(90)
            .outreachLocation("OUTLOC")
            .referralSource("REFSRC")
            .hasAssessment(true)
            .isAmended(false)
            .submission(Submission.builder().id(submissionId).submissionPeriod("APR-2025").build())
            .build();

    final ClaimResponse fields = mapper.toClaimResponse(entity);

    assertNotNull(fields);
    assertEquals(entity.getDutySolicitor(), fields.getIsDutySolicitor());
    assertEquals(entity.getYouthCourt(), fields.getIsYouthCourt());
    assertEquals(ClaimStatus.READY_TO_PROCESS, fields.getStatus());
    assertEquals(entity.getScheduleReference(), fields.getScheduleReference());
    assertEquals(entity.getLineNumber(), fields.getLineNumber());
    assertEquals(entity.getCaseReferenceNumber(), fields.getCaseReferenceNumber());
    assertEquals(entity.getUniqueFileNumber(), fields.getUniqueFileNumber());
    assertEquals(entity.getCaseStartDate().toString(), fields.getCaseStartDate());
    assertEquals(entity.getCaseConcludedDate().toString(), fields.getCaseConcludedDate());
    assertEquals(entity.getMatterTypeCode(), fields.getMatterTypeCode());
    assertEquals(entity.getCrimeMatterTypeCode(), fields.getCrimeMatterTypeCode());
    assertEquals(entity.getFeeSchemeCode(), fields.getFeeSchemeCode());
    assertEquals(entity.getFeeCode(), fields.getFeeCode());
    assertEquals(entity.getProcurementAreaCode(), fields.getProcurementAreaCode());
    assertEquals(entity.getAccessPointCode(), fields.getAccessPointCode());
    assertEquals(entity.getDeliveryLocation(), fields.getDeliveryLocation());
    assertEquals(
        entity.getRepresentationOrderDate().toString(), fields.getRepresentationOrderDate());
    assertEquals(entity.getSuspectsDefendantsCount(), fields.getSuspectsDefendantsCount());
    assertEquals(
        entity.getPoliceStationCourtAttendancesCount(),
        fields.getPoliceStationCourtAttendancesCount());
    assertEquals(entity.getPoliceStationCourtPrisonId(), fields.getPoliceStationCourtPrisonId());
    assertEquals(entity.getDsccNumber(), fields.getDsccNumber());
    assertEquals(entity.getMaatId(), fields.getMaatId());
    assertEquals(
        entity.getPrisonLawPriorApprovalNumber(), fields.getPrisonLawPriorApprovalNumber());
    assertEquals(entity.getSchemeId(), fields.getSchemeId());
    assertEquals(entity.getMediationSessionsCount(), fields.getMediationSessionsCount());
    assertEquals(entity.getMediationTimeMinutes(), fields.getMediationTimeMinutes());
    assertEquals(entity.getOutreachLocation(), fields.getOutreachLocation());
    assertEquals(entity.getReferralSource(), fields.getReferralSource());
    assertEquals(entity.isHasAssessment(), fields.getHasAssessment());
    assertEquals(entity.isAmended(), fields.getIsAmended());
    assertEquals(entity.getSubmission().getId().toString(), fields.getSubmissionId());
    assertEquals(entity.getSubmission().getSubmissionPeriod(), fields.getSubmissionPeriod());
  }

  @Test
  void toClaimFieldsV2_mapsAllResponse() {
    UUID submissionId = Uuid7.timeBasedUuid();
    final Claim entity =
        Claim.builder()
            .dutySolicitor(true)
            .youthCourt(false)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference("SCH123")
            .lineNumber(5)
            .caseReferenceNumber(CASE_REFERENCE)
            .uniqueFileNumber("UFN123")
            .caseStartDate(LocalDate.now())
            .caseConcludedDate(LocalDate.now().plusDays(1))
            .matterTypeCode("MTC")
            .crimeMatterTypeCode("CMTC")
            .feeSchemeCode("FSC")
            .feeCode("FC")
            .procurementAreaCode("PAC")
            .accessPointCode("APC")
            .deliveryLocation("DEL")
            .representationOrderDate(LocalDate.now().minusDays(2))
            .suspectsDefendantsCount(3)
            .policeStationCourtAttendancesCount(4)
            .policeStationCourtPrisonId("PSCPI")
            .dsccNumber("DSCC123")
            .maatId("987654321L")
            .prisonLawPriorApprovalNumber("PLPAN")
            .schemeId("12")
            .mediationSessionsCount(2)
            .mediationTimeMinutes(90)
            .outreachLocation("OUTLOC")
            .referralSource("REFSRC")
            .hasAssessment(false)
            .isAmended(true)
            .claimSummaryFee(new ArrayList<>())
            .submission(
                Submission.builder()
                    .id(submissionId)
                    .submissionPeriod("APR-2025")
                    .createdOn(Instant.now())
                    .build())
            .calculatedFeeDetails(
                List.of(
                    CalculatedFeeDetail.builder()
                        .claimSummaryFee(ClaimSummaryFee.builder().isVatApplicable(true).build())
                        .build()))
            .build();

    final ClaimResponseV2 fields = mapper.toClaimResponseV2(entity);

    assertNotNull(fields);
    assertEquals(entity.getDutySolicitor(), fields.getIsDutySolicitor());
    assertEquals(entity.getYouthCourt(), fields.getIsYouthCourt());
    assertEquals(ClaimStatus.READY_TO_PROCESS, fields.getStatus());
    assertEquals(entity.getScheduleReference(), fields.getScheduleReference());
    assertEquals(entity.getLineNumber(), fields.getLineNumber());
    assertEquals(entity.getCaseReferenceNumber(), fields.getCaseReferenceNumber());
    assertEquals(entity.getUniqueFileNumber(), fields.getUniqueFileNumber());
    assertEquals(entity.getCaseStartDate().toString(), fields.getCaseStartDate());
    assertEquals(entity.getCaseConcludedDate().toString(), fields.getCaseConcludedDate());
    assertEquals(entity.getMatterTypeCode(), fields.getMatterTypeCode());
    assertEquals(entity.getCrimeMatterTypeCode(), fields.getCrimeMatterTypeCode());
    assertEquals(entity.getFeeSchemeCode(), fields.getFeeSchemeCode());
    assertEquals(entity.getFeeCode(), fields.getFeeCode());
    assertEquals(entity.getProcurementAreaCode(), fields.getProcurementAreaCode());
    assertEquals(entity.getAccessPointCode(), fields.getAccessPointCode());
    assertEquals(entity.getDeliveryLocation(), fields.getDeliveryLocation());
    assertEquals(
        entity.getRepresentationOrderDate().toString(), fields.getRepresentationOrderDate());
    assertEquals(entity.getSuspectsDefendantsCount(), fields.getSuspectsDefendantsCount());
    assertEquals(
        entity.getPoliceStationCourtAttendancesCount(),
        fields.getPoliceStationCourtAttendancesCount());
    assertEquals(entity.getPoliceStationCourtPrisonId(), fields.getPoliceStationCourtPrisonId());
    assertEquals(entity.getDsccNumber(), fields.getDsccNumber());
    assertEquals(entity.getMaatId(), fields.getMaatId());
    assertEquals(
        entity.getPrisonLawPriorApprovalNumber(), fields.getPrisonLawPriorApprovalNumber());
    assertEquals(entity.getSchemeId(), fields.getSchemeId());
    assertEquals(entity.getMediationSessionsCount(), fields.getMediationSessionsCount());
    assertEquals(entity.getMediationTimeMinutes(), fields.getMediationTimeMinutes());
    assertEquals(entity.getOutreachLocation(), fields.getOutreachLocation());
    assertEquals(entity.getReferralSource(), fields.getReferralSource());
    assertEquals(entity.isHasAssessment(), fields.getHasAssessment());
    assertEquals(entity.isAmended(), fields.getIsAmended());
    assertEquals(entity.getSubmission().getId().toString(), fields.getSubmissionId());
    assertEquals(entity.getSubmission().getSubmissionPeriod(), fields.getSubmissionPeriod());
    assertEquals(entity.getSubmission().getCreatedOn(), fields.getDateSubmitted().toInstant());
    assertEquals(
        entity.getLatestCalculatedFee().getClaimSummaryFee().getIsVatApplicable(),
        fields.getIsVatApplicable());
  }

  @Test
  void toSubmissionClaim_nullInput_returnsNull() {
    assertNull(mapper.toSubmissionClaim(null));
  }

  @Test
  void toSubmissionClaim_mapsFields() {
    final UUID id = Uuid7.timeBasedUuid();
    final Claim entity = Claim.builder().id(id).status(ClaimStatus.READY_TO_PROCESS).build();

    final SubmissionClaim response = mapper.toSubmissionClaim(entity);

    assertNotNull(response);
    assertEquals(id, response.getClaimId());
    assertEquals(ClaimStatus.READY_TO_PROCESS, response.getStatus());
  }

  @ParameterizedTest(name = "[{index}] status={0}, hasAssessment={1}, isAmended={2} -> {3}")
  @CsvSource({
    "VOID,             false, false, VOIDED",
    "INVALID,          false, false, INVALID",
    "READY_TO_PROCESS, false, false, READY_TO_PROCESS",
    "VALID,            false, false, ACCEPTED",
    "VALID,            false, true,  AMENDED",
    "VALID,            true,  false, ASSESSED",
    "VALID,            true,  true,  ASSESSED",
  })
  @DisplayName("toClaimResponseV2 derives derived_claim_status and leaves raw status unchanged")
  void toClaimResponseV2_populatesDerivedClaimStatus(
      ClaimStatus status,
      boolean hasAssessment,
      boolean isAmended,
      DerivedClaimStatus expectedDerived) {
    final Claim entity =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .status(status)
            .hasAssessment(hasAssessment)
            .isAmended(isAmended)
            .claimSummaryFee(new ArrayList<>())
            .calculatedFeeDetails(new ArrayList<>())
            .submission(Submission.builder().id(Uuid7.timeBasedUuid()).build())
            .build();

    final ClaimResponseV2 response = mapper.toClaimResponseV2(entity);

    assertNotNull(response);
    // Derived business status is populated from the resolver...
    assertEquals(expectedDerived, response.getDerivedClaimStatus());
    // ...and the raw processing status is left untouched.
    assertEquals(status, response.getStatus());
    assertEquals(hasAssessment, response.getHasAssessment());
    assertEquals(isAmended, response.getIsAmended());
  }

  @Test
  void toAmendmentPayload_nullInput_returnsNull() {
    assertNull(mapper.toAmendmentPayload(null));
  }

  @Test
  void toAmendmentPayload_mapsDatesUuidAndTriState() {
    final java.util.UUID userUuid = UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    // present value strings -> should parse into LocalDate in payload
    patch.setCaseStartDate(org.openapitools.jackson.nullable.JsonNullable.of("5/12/2026"));
    // present UUID -> should map to String
    patch.setAmendmentUserId(org.openapitools.jackson.nullable.JsonNullable.of(userUuid));
    // omitted field stays undefined
    // explicit null preserved
    patch.setScheduleReference(org.openapitools.jackson.nullable.JsonNullable.of((String) null));

    final uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload payload =
        mapper.toAmendmentPayload(patch);

    // parsed date
    assertTrue(payload.getCaseStartDate().isPresent());
    assertEquals(LocalDate.of(2026, 12, 5), payload.getCaseStartDate().get());

    // uuid -> string
    assertTrue(payload.getAmendmentUserId().isPresent());
    assertEquals(userUuid.toString(), payload.getAmendmentUserId().get());

    // explicit-null preserved
    assertTrue(payload.getScheduleReference().isPresent());
    assertNull(payload.getScheduleReference().get());

    // omitted fields remain undefined (e.g. feeCode)
    assertTrue(!payload.getFeeCode().isPresent());
  }

  @Test
  void toAmendmentPayload_mapsAllPresentFields() {
    final UUID userUuid = UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

    final ClaimAmendmentPatch patch =
        new ClaimAmendmentPatch()
            .scheduleReference("SCH-ALL")
            .lineNumber(7)
            .caseReferenceNumber("CR-ALL")
            .uniqueFileNumber("UFN-ALL")
            .caseStartDate("5/12/2026")
            .caseConcludedDate("6/12/2026")
            .matterTypeCode("MTC-ALL")
            .crimeMatterTypeCode("CMTC-ALL")
            .feeSchemeCode("FSC-ALL")
            .feeCode("FC-ALL")
            .procurementAreaCode("PAC-ALL")
            .accessPointCode("APC-ALL")
            .deliveryLocation("DL-ALL")
            .version(11L)
            .representationOrderDate("1/1/2025")
            .suspectsDefendantsCount(2)
            .policeStationCourtAttendancesCount(3)
            .policeStationCourtPrisonId("PS-PR-1")
            .dsccNumber("DSCC-ALL")
            .maatId("MAAT-ALL")
            .prisonLawPriorApprovalNumber("PR-APP")
            .isDutySolicitor(Boolean.TRUE)
            .isYouthCourt(Boolean.FALSE)
            .schemeId("SCHMID")
            .mediationSessionsCount(1)
            .mediationTimeMinutes(30)
            .outreachLocation("OUT-ALL")
            .referralSource("REF-ALL")
            .clientForename("John")
            .clientSurname("Smith")
            .clientDateOfBirth("5/12/1990")
            .uniqueClientNumber("UCN-1")
            .clientPostcode("PC1")
            .genderCode("M")
            .ethnicityCode("ETH")
            .disabilityCode("DIS")
            .isLegallyAided(Boolean.TRUE)
            .clientTypeCode("CTC")
            .homeOfficeClientNumber("HOCN")
            .claReferenceNumber("CLA-1")
            .claExemptionCode("EX-1")
            .client2Forename("Jane")
            .client2Surname("Doe")
            .client2DateOfBirth("6/6/1992")
            .client2Ucn("UCN-2")
            .client2Postcode("PC2")
            .client2GenderCode("F")
            .client2EthnicityCode("ETH2")
            .client2DisabilityCode("DIS2")
            .client2IsLegallyAided(Boolean.FALSE)
            .caseId("CASE-1")
            .uniqueCaseId("UCASE-1")
            .caseStageCode("STAGE1")
            .stageReachedCode("REACHED1")
            .standardFeeCategoryCode("SFC")
            .outcomeCode("OUTC")
            .designatedAccreditedRepresentativeCode("DAR")
            .isPostalApplicationAccepted(Boolean.TRUE)
            .isClient2PostalApplicationAccepted(Boolean.FALSE)
            .mentalHealthTribunalReference("MHTR")
            .isNrmAdvice(Boolean.TRUE)
            .followOnWork("FOLLOW")
            .transferDate("2/2/2024")
            .exemptionCriteriaSatisfied("EXC")
            .exceptionalCaseFundingReference("EXCF")
            .isLegacyCase(Boolean.FALSE)
            .adviceTime(5)
            .travelTime(6)
            .waitingTime(7)
            .netProfitCostsAmount(new BigDecimal("1.11"))
            .netDisbursementAmount(new BigDecimal("2.22"))
            .netCounselCostsAmount(new BigDecimal("3.33"))
            .disbursementsVatAmount(new BigDecimal("4.44"))
            .travelWaitingCostsAmount(new BigDecimal("5.55"))
            .netWaitingCostsAmount(new BigDecimal("6.66"))
            .isVatApplicable(Boolean.TRUE)
            .isToleranceApplicable(Boolean.FALSE)
            .priorAuthorityReference("PAR-1")
            .isLondonRate(Boolean.TRUE)
            .adjournedHearingFeeAmount(1)
            .isAdditionalTravelPayment(Boolean.FALSE)
            .costsDamagesRecoveredAmount(new BigDecimal("7.77"))
            .meetingsAttendedCode("MEET1")
            .detentionTravelWaitingCostsAmount(new BigDecimal("8.88"))
            .jrFormFillingAmount(new BigDecimal("9.99"))
            .isEligibleClient(Boolean.TRUE)
            .courtLocationCode("CL01")
            .adviceTypeCode("ATC")
            .medicalReportsCount(2)
            .isIrcSurgery(Boolean.FALSE)
            .surgeryDate("3/3/2023")
            .surgeryClientsCount(1)
            .surgeryMattersCount(2)
            .cmrhOralCount(0)
            .cmrhTelephoneCount(1)
            .aitHearingCentreCode("AITHC")
            .isSubstantiveHearing(Boolean.TRUE)
            .hoInterview(4)
            .localAuthorityNumber("LAN1")
            .amendmentRequestedBy("PROVIDER")
            .amendmentUserId(userUuid)
            .amendmentReasonCode("REASON1");

    final ClaimAmendmentPayload payload = mapper.toAmendmentPayload(patch);

    // Assert every mapped field is present and equals the expected value
    // --- Claim fields ---
    assertTrue(payload.getScheduleReference().isPresent());
    assertEquals("SCH-ALL", payload.getScheduleReference().get());

    assertTrue(payload.getLineNumber().isPresent());
    assertEquals(7, payload.getLineNumber().get());

    assertTrue(payload.getCaseReferenceNumber().isPresent());
    assertEquals("CR-ALL", payload.getCaseReferenceNumber().get());

    assertTrue(payload.getUniqueFileNumber().isPresent());
    assertEquals("UFN-ALL", payload.getUniqueFileNumber().get());

    assertTrue(payload.getCaseStartDate().isPresent());
    assertEquals(LocalDate.of(2026, 12, 5), payload.getCaseStartDate().get());

    assertTrue(payload.getCaseConcludedDate().isPresent());
    assertEquals(LocalDate.of(2026, 12, 6), payload.getCaseConcludedDate().get());

    assertTrue(payload.getMatterTypeCode().isPresent());
    assertEquals("MTC-ALL", payload.getMatterTypeCode().get());

    assertTrue(payload.getCrimeMatterTypeCode().isPresent());
    assertEquals("CMTC-ALL", payload.getCrimeMatterTypeCode().get());

    assertTrue(payload.getFeeSchemeCode().isPresent());
    assertEquals("FSC-ALL", payload.getFeeSchemeCode().get());

    assertTrue(payload.getFeeCode().isPresent());
    assertEquals("FC-ALL", payload.getFeeCode().get());

    assertTrue(payload.getProcurementAreaCode().isPresent());
    assertEquals("PAC-ALL", payload.getProcurementAreaCode().get());

    assertTrue(payload.getAccessPointCode().isPresent());
    assertEquals("APC-ALL", payload.getAccessPointCode().get());

    assertTrue(payload.getDeliveryLocation().isPresent());
    assertEquals("DL-ALL", payload.getDeliveryLocation().get());

    assertTrue(payload.getVersion().isPresent());
    assertEquals(11L, payload.getVersion().get());

    assertTrue(payload.getRepresentationOrderDate().isPresent());
    assertEquals(LocalDate.of(2025, 1, 1), payload.getRepresentationOrderDate().get());

    assertTrue(payload.getSuspectsDefendantsCount().isPresent());
    assertEquals(2, payload.getSuspectsDefendantsCount().get());

    assertTrue(payload.getPoliceStationCourtAttendancesCount().isPresent());
    assertEquals(3, payload.getPoliceStationCourtAttendancesCount().get());

    assertTrue(payload.getPoliceStationCourtPrisonId().isPresent());
    assertEquals("PS-PR-1", payload.getPoliceStationCourtPrisonId().get());

    assertTrue(payload.getDsccNumber().isPresent());
    assertEquals("DSCC-ALL", payload.getDsccNumber().get());

    assertTrue(payload.getMaatId().isPresent());
    assertEquals("MAAT-ALL", payload.getMaatId().get());

    assertTrue(payload.getPrisonLawPriorApprovalNumber().isPresent());
    assertEquals("PR-APP", payload.getPrisonLawPriorApprovalNumber().get());

    assertTrue(payload.getIsDutySolicitor().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsDutySolicitor().get());

    assertTrue(payload.getIsYouthCourt().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsYouthCourt().get());

    assertTrue(payload.getSchemeId().isPresent());
    assertEquals("SCHMID", payload.getSchemeId().get());

    assertTrue(payload.getMediationSessionsCount().isPresent());
    assertEquals(1, payload.getMediationSessionsCount().get());

    assertTrue(payload.getMediationTimeMinutes().isPresent());
    assertEquals(30, payload.getMediationTimeMinutes().get());

    assertTrue(payload.getOutreachLocation().isPresent());
    assertEquals("OUT-ALL", payload.getOutreachLocation().get());

    assertTrue(payload.getReferralSource().isPresent());
    assertEquals("REF-ALL", payload.getReferralSource().get());

    // --- Client fields ---
    assertTrue(payload.getClientForename().isPresent());
    assertEquals("John", payload.getClientForename().get());

    assertTrue(payload.getClientSurname().isPresent());
    assertEquals("Smith", payload.getClientSurname().get());

    assertTrue(payload.getClientDateOfBirth().isPresent());
    assertEquals(LocalDate.of(1990, 12, 5), payload.getClientDateOfBirth().get());

    assertTrue(payload.getUniqueClientNumber().isPresent());
    assertEquals("UCN-1", payload.getUniqueClientNumber().get());

    assertTrue(payload.getClientPostcode().isPresent());
    assertEquals("PC1", payload.getClientPostcode().get());

    assertTrue(payload.getGenderCode().isPresent());
    assertEquals("M", payload.getGenderCode().get());

    assertTrue(payload.getEthnicityCode().isPresent());
    assertEquals("ETH", payload.getEthnicityCode().get());

    assertTrue(payload.getDisabilityCode().isPresent());
    assertEquals("DIS", payload.getDisabilityCode().get());

    assertTrue(payload.getIsLegallyAided().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsLegallyAided().get());

    assertTrue(payload.getClientTypeCode().isPresent());
    assertEquals("CTC", payload.getClientTypeCode().get());

    assertTrue(payload.getHomeOfficeClientNumber().isPresent());
    assertEquals("HOCN", payload.getHomeOfficeClientNumber().get());

    assertTrue(payload.getClaReferenceNumber().isPresent());
    assertEquals("CLA-1", payload.getClaReferenceNumber().get());

    assertTrue(payload.getClaExemptionCode().isPresent());
    assertEquals("EX-1", payload.getClaExemptionCode().get());

    assertTrue(payload.getClient2Forename().isPresent());
    assertEquals("Jane", payload.getClient2Forename().get());

    assertTrue(payload.getClient2Surname().isPresent());
    assertEquals("Doe", payload.getClient2Surname().get());

    assertTrue(payload.getClient2DateOfBirth().isPresent());
    assertEquals(LocalDate.of(1992, 6, 6), payload.getClient2DateOfBirth().get());

    assertTrue(payload.getClient2Ucn().isPresent());
    assertEquals("UCN-2", payload.getClient2Ucn().get());

    assertTrue(payload.getClient2Postcode().isPresent());
    assertEquals("PC2", payload.getClient2Postcode().get());

    assertTrue(payload.getClient2GenderCode().isPresent());
    assertEquals("F", payload.getClient2GenderCode().get());

    assertTrue(payload.getClient2EthnicityCode().isPresent());
    assertEquals("ETH2", payload.getClient2EthnicityCode().get());

    assertTrue(payload.getClient2DisabilityCode().isPresent());
    assertEquals("DIS2", payload.getClient2DisabilityCode().get());

    assertTrue(payload.getClient2IsLegallyAided().isPresent());
    assertEquals(Boolean.FALSE, payload.getClient2IsLegallyAided().get());

    // --- Claim-case fields ---
    assertTrue(payload.getCaseId().isPresent());
    assertEquals("CASE-1", payload.getCaseId().get());

    assertTrue(payload.getUniqueCaseId().isPresent());
    assertEquals("UCASE-1", payload.getUniqueCaseId().get());

    assertTrue(payload.getCaseStageCode().isPresent());
    assertEquals("STAGE1", payload.getCaseStageCode().get());

    assertTrue(payload.getStageReachedCode().isPresent());
    assertEquals("REACHED1", payload.getStageReachedCode().get());

    assertTrue(payload.getStandardFeeCategoryCode().isPresent());
    assertEquals("SFC", payload.getStandardFeeCategoryCode().get());

    assertTrue(payload.getOutcomeCode().isPresent());
    assertEquals("OUTC", payload.getOutcomeCode().get());

    assertTrue(payload.getDesignatedAccreditedRepresentativeCode().isPresent());
    assertEquals("DAR", payload.getDesignatedAccreditedRepresentativeCode().get());

    assertTrue(payload.getIsPostalApplicationAccepted().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsPostalApplicationAccepted().get());

    assertTrue(payload.getIsClient2PostalApplicationAccepted().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsClient2PostalApplicationAccepted().get());

    assertTrue(payload.getMentalHealthTribunalReference().isPresent());
    assertEquals("MHTR", payload.getMentalHealthTribunalReference().get());

    assertTrue(payload.getIsNrmAdvice().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsNrmAdvice().get());

    assertTrue(payload.getFollowOnWork().isPresent());
    assertEquals("FOLLOW", payload.getFollowOnWork().get());

    assertTrue(payload.getTransferDate().isPresent());
    assertEquals(LocalDate.of(2024, 2, 2), payload.getTransferDate().get());

    assertTrue(payload.getExemptionCriteriaSatisfied().isPresent());
    assertEquals("EXC", payload.getExemptionCriteriaSatisfied().get());

    assertTrue(payload.getExceptionalCaseFundingReference().isPresent());
    assertEquals("EXCF", payload.getExceptionalCaseFundingReference().get());

    assertTrue(payload.getIsLegacyCase().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsLegacyCase().get());

    // --- Claim-summary-fee fields ---
    assertTrue(payload.getAdviceTime().isPresent());
    assertEquals(5, payload.getAdviceTime().get());

    assertTrue(payload.getTravelTime().isPresent());
    assertEquals(6, payload.getTravelTime().get());

    assertTrue(payload.getWaitingTime().isPresent());
    assertEquals(7, payload.getWaitingTime().get());

    assertTrue(payload.getNetProfitCostsAmount().isPresent());
    assertEquals(new BigDecimal("1.11"), payload.getNetProfitCostsAmount().get());

    assertTrue(payload.getNetDisbursementAmount().isPresent());
    assertEquals(new BigDecimal("2.22"), payload.getNetDisbursementAmount().get());

    assertTrue(payload.getNetCounselCostsAmount().isPresent());
    assertEquals(new BigDecimal("3.33"), payload.getNetCounselCostsAmount().get());

    assertTrue(payload.getDisbursementsVatAmount().isPresent());
    assertEquals(new BigDecimal("4.44"), payload.getDisbursementsVatAmount().get());

    assertTrue(payload.getTravelWaitingCostsAmount().isPresent());
    assertEquals(new BigDecimal("5.55"), payload.getTravelWaitingCostsAmount().get());

    assertTrue(payload.getNetWaitingCostsAmount().isPresent());
    assertEquals(new BigDecimal("6.66"), payload.getNetWaitingCostsAmount().get());

    assertTrue(payload.getIsVatApplicable().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsVatApplicable().get());

    assertTrue(payload.getIsToleranceApplicable().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsToleranceApplicable().get());

    assertTrue(payload.getPriorAuthorityReference().isPresent());
    assertEquals("PAR-1", payload.getPriorAuthorityReference().get());

    assertTrue(payload.getIsLondonRate().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsLondonRate().get());

    assertTrue(payload.getAdjournedHearingFeeAmount().isPresent());
    assertEquals(1, payload.getAdjournedHearingFeeAmount().get());

    assertTrue(payload.getIsAdditionalTravelPayment().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsAdditionalTravelPayment().get());

    assertTrue(payload.getCostsDamagesRecoveredAmount().isPresent());
    assertEquals(new BigDecimal("7.77"), payload.getCostsDamagesRecoveredAmount().get());

    assertTrue(payload.getMeetingsAttendedCode().isPresent());
    assertEquals("MEET1", payload.getMeetingsAttendedCode().get());

    assertTrue(payload.getDetentionTravelWaitingCostsAmount().isPresent());
    assertEquals(new BigDecimal("8.88"), payload.getDetentionTravelWaitingCostsAmount().get());

    assertTrue(payload.getJrFormFillingAmount().isPresent());
    assertEquals(new BigDecimal("9.99"), payload.getJrFormFillingAmount().get());

    assertTrue(payload.getIsEligibleClient().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsEligibleClient().get());

    assertTrue(payload.getCourtLocationCode().isPresent());
    assertEquals("CL01", payload.getCourtLocationCode().get());

    assertTrue(payload.getAdviceTypeCode().isPresent());
    assertEquals("ATC", payload.getAdviceTypeCode().get());

    assertTrue(payload.getMedicalReportsCount().isPresent());
    assertEquals(2, payload.getMedicalReportsCount().get());

    assertTrue(payload.getIsIrcSurgery().isPresent());
    assertEquals(Boolean.FALSE, payload.getIsIrcSurgery().get());

    assertTrue(payload.getSurgeryDate().isPresent());
    assertEquals(LocalDate.of(2023, 3, 3), payload.getSurgeryDate().get());

    assertTrue(payload.getSurgeryClientsCount().isPresent());
    assertEquals(1, payload.getSurgeryClientsCount().get());

    assertTrue(payload.getSurgeryMattersCount().isPresent());
    assertEquals(2, payload.getSurgeryMattersCount().get());

    assertTrue(payload.getCmrhOralCount().isPresent());
    assertEquals(0, payload.getCmrhOralCount().get());

    assertTrue(payload.getCmrhTelephoneCount().isPresent());
    assertEquals(1, payload.getCmrhTelephoneCount().get());

    assertTrue(payload.getAitHearingCentreCode().isPresent());
    assertEquals("AITHC", payload.getAitHearingCentreCode().get());

    assertTrue(payload.getIsSubstantiveHearing().isPresent());
    assertEquals(Boolean.TRUE, payload.getIsSubstantiveHearing().get());

    assertTrue(payload.getHoInterview().isPresent());
    assertEquals(4, payload.getHoInterview().get());

    assertTrue(payload.getLocalAuthorityNumber().isPresent());
    assertEquals("LAN1", payload.getLocalAuthorityNumber().get());

    // --- Amendment metadata ---
    assertTrue(payload.getAmendmentRequestedBy().isPresent());
    assertEquals("PROVIDER", payload.getAmendmentRequestedBy().get());

    assertTrue(payload.getAmendmentUserId().isPresent());
    assertEquals(userUuid.toString(), payload.getAmendmentUserId().get());

    assertTrue(payload.getAmendmentReasonCode().isPresent());
    assertEquals("REASON1", payload.getAmendmentReasonCode().get());
  }

  @Test
  void toAmendmentPayload_omittedFields_areNotPresent() {
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();

    final ClaimAmendmentPayload payload = mapper.toAmendmentPayload(patch);

    // When no fields are set on the patch, the payload should contain no present values
    assertNotNull(payload);
    // pick a few representative fields that had "isPresent" checks in the mapper
    assertTrue(!payload.getScheduleReference().isPresent());
    assertTrue(!payload.getCaseStartDate().isPresent());
    assertTrue(!payload.getClientForename().isPresent());
    assertTrue(!payload.getAmendmentUserId().isPresent());
  }

  @Test
  void toValidationMessageLog_mapsFields() {
    final Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    final Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).submission(submission).build();

    final ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("A display message")
            .technicalMessage("A technical message");

    final ValidationMessageLog log = mapper.toValidationMessageLog(patch, claim);

    assertNotNull(log.getId());
    assertEquals(submission.getId(), log.getSubmissionId());
    assertEquals(claim.getId(), log.getClaimId());
    assertEquals(ValidationMessageType.ERROR, log.getType());
    assertEquals("SYSTEM", log.getSource());
    assertEquals("A display message", log.getDisplayMessage());
    assertEquals("A technical message", log.getTechnicalMessage());
  }

  @Test
  void toValidationMessageLog_mapsMessageCodeForFspMessages() {
    final Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    final Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).submission(submission).build();

    final ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("FSP")
            .displayMessage("FSP error message")
            .technicalMessage("FSP technical details")
            .messageCode("ERRALL1");

    final ValidationMessageLog log = mapper.toValidationMessageLog(patch, claim);

    assertNotNull(log.getId());
    assertEquals(submission.getId(), log.getSubmissionId());
    assertEquals(claim.getId(), log.getClaimId());
    assertEquals(ValidationMessageType.ERROR, log.getType());
    assertEquals("FSP", log.getSource());
    assertEquals("FSP error message", log.getDisplayMessage());
    assertEquals("FSP technical details", log.getTechnicalMessage());
    assertEquals("ERRALL1", log.getMessageCode());
  }

  @Test
  void toClaimSummaryFee_mapsAllFields() {
    final ClaimPost post = ClaimsDataTestUtil.getClaimPost(CASE_REFERENCE);

    final ClaimSummaryFee claimSummaryFee = mapper.toClaimSummaryFee(post);

    assertThat(claimSummaryFee.getAdviceTime()).isEqualTo(post.getAdviceTime());
    assertThat(claimSummaryFee.getTravelTime()).isEqualTo(post.getTravelTime());
    assertThat(claimSummaryFee.getWaitingTime()).isEqualTo(post.getWaitingTime());
    assertThat(claimSummaryFee.getNetProfitCostsAmount()).isEqualTo(post.getNetProfitCostsAmount());
    assertThat(claimSummaryFee.getNetDisbursementAmount())
        .isEqualTo(post.getNetDisbursementAmount());
    assertThat(claimSummaryFee.getNetCounselCostsAmount())
        .isEqualTo(post.getNetCounselCostsAmount());
    assertThat(claimSummaryFee.getDisbursementsVatAmount())
        .isEqualTo(post.getDisbursementsVatAmount());
    assertThat(claimSummaryFee.getTravelWaitingCostsAmount())
        .isEqualTo(post.getTravelWaitingCostsAmount());
    assertThat(claimSummaryFee.getNetWaitingCostsAmount())
        .isEqualTo(post.getNetWaitingCostsAmount());
    assertThat(claimSummaryFee.getIsVatApplicable()).isEqualTo(post.getIsVatApplicable());
    assertThat(claimSummaryFee.getIsToleranceApplicable())
        .isEqualTo(post.getIsToleranceApplicable());
    assertThat(claimSummaryFee.getPriorAuthorityReference())
        .isEqualTo(post.getPriorAuthorityReference());
    assertThat(claimSummaryFee.getIsLondonRate()).isEqualTo(post.getIsLondonRate());
    assertThat(claimSummaryFee.getAdjournedHearingFeeAmount())
        .isEqualTo(post.getAdjournedHearingFeeAmount());
    assertThat(claimSummaryFee.getIsAdditionalTravelPayment())
        .isEqualTo(post.getIsAdditionalTravelPayment());
    assertThat(claimSummaryFee.getCostsDamagesRecoveredAmount())
        .isEqualTo(post.getCostsDamagesRecoveredAmount());
    assertThat(claimSummaryFee.getMeetingsAttendedCode()).isEqualTo(post.getMeetingsAttendedCode());
    assertThat(claimSummaryFee.getDetentionTravelWaitingCostsAmount())
        .isEqualTo(post.getDetentionTravelWaitingCostsAmount());
    assertThat(claimSummaryFee.getJrFormFillingAmount()).isEqualTo(post.getJrFormFillingAmount());
    assertThat(claimSummaryFee.getIsEligibleClient()).isEqualTo(post.getIsEligibleClient());
    assertThat(claimSummaryFee.getCourtLocationCode()).isEqualTo(post.getCourtLocationCode());
    assertThat(claimSummaryFee.getAdviceTypeCode()).isEqualTo(post.getAdviceTypeCode());
    assertThat(claimSummaryFee.getMedicalReportsCount()).isEqualTo(post.getMedicalReportsCount());
    assertThat(claimSummaryFee.getIsIrcSurgery()).isEqualTo(post.getIsIrcSurgery());
    assertThat(claimSummaryFee.getSurgeryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        .isEqualTo(post.getSurgeryDate());
    assertThat(claimSummaryFee.getSurgeryClientsCount()).isEqualTo(post.getSurgeryClientsCount());
    assertThat(claimSummaryFee.getSurgeryMattersCount()).isEqualTo(post.getSurgeryMattersCount());
    assertThat(claimSummaryFee.getCmrhOralCount()).isEqualTo(post.getCmrhOralCount());
    assertThat(claimSummaryFee.getCmrhTelephoneCount()).isEqualTo(post.getCmrhTelephoneCount());
    assertThat(claimSummaryFee.getAitHearingCentreCode()).isEqualTo(post.getAitHearingCentreCode());
    assertThat(claimSummaryFee.getIsSubstantiveHearing()).isEqualTo(post.getIsSubstantiveHearing());
    assertThat(claimSummaryFee.getHoInterview()).isEqualTo(post.getHoInterview());
    assertThat(claimSummaryFee.getLocalAuthorityNumber()).isEqualTo(post.getLocalAuthorityNumber());
  }

  @Test
  void toClaimSummaryFee_nullPost_noChanges() {
    assertNull(mapper.toClaimSummaryFee(null));
  }

  @Test
  void toCalculatedFeeDetail_mapsAllFields() {
    final BoltOnPatch boltOnPatch = getBoltOnPatch();

    final FeeCalculationPatch feeCalculationPatch = getFeeCalculationPatch();
    feeCalculationPatch.boltOnDetails(boltOnPatch);

    final CalculatedFeeDetail calculatedFeeDetail =
        mapper.toCalculatedFeeDetail(feeCalculationPatch);

    // test CalculatedFeeDetail fields
    assertNotNull(calculatedFeeDetail.getId());
    assertThat(calculatedFeeDetail.getFeeCode()).isEqualTo(feeCalculationPatch.getFeeCode());
    assertThat(calculatedFeeDetail.getFeeCodeDescription())
        .isEqualTo(feeCalculationPatch.getFeeCodeDescription());
    assertThat(calculatedFeeDetail.getFeeType()).isEqualTo(feeCalculationPatch.getFeeType());
    assertThat(calculatedFeeDetail.getCategoryOfLaw())
        .isEqualTo(feeCalculationPatch.getCategoryOfLaw());
    assertThat(calculatedFeeDetail.getTotalAmount())
        .isEqualTo(feeCalculationPatch.getTotalAmount());
    assertThat(calculatedFeeDetail.getVatIndicator())
        .isEqualTo(feeCalculationPatch.getVatIndicator());
    assertThat(calculatedFeeDetail.getVatRateApplied())
        .isEqualTo(feeCalculationPatch.getVatRateApplied());
    assertThat(calculatedFeeDetail.getCalculatedVatAmount())
        .isEqualTo(feeCalculationPatch.getCalculatedVatAmount());
    assertThat(calculatedFeeDetail.getDisbursementAmount())
        .isEqualTo(feeCalculationPatch.getDisbursementAmount());
    assertThat(calculatedFeeDetail.getRequestedNetDisbursementAmount())
        .isEqualTo(feeCalculationPatch.getRequestedNetDisbursementAmount());
    assertThat(calculatedFeeDetail.getDisbursementVatAmount())
        .isEqualTo(feeCalculationPatch.getDisbursementVatAmount());
    assertThat(calculatedFeeDetail.getHourlyTotalAmount())
        .isEqualTo(feeCalculationPatch.getHourlyTotalAmount());
    assertThat(calculatedFeeDetail.getFixedFeeAmount())
        .isEqualTo(feeCalculationPatch.getFixedFeeAmount());
    assertThat(calculatedFeeDetail.getNetProfitCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetProfitCostsAmount());
    assertThat(calculatedFeeDetail.getRequestedNetProfitCostsAmount())
        .isEqualTo(feeCalculationPatch.getRequestedNetProfitCostsAmount());
    assertThat(calculatedFeeDetail.getNetCostOfCounselAmount())
        .isEqualTo(feeCalculationPatch.getNetCostOfCounselAmount());
    assertThat(calculatedFeeDetail.getNetTravelCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetTravelCostsAmount());
    assertThat(calculatedFeeDetail.getNetWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetWaitingCostsAmount());
    assertThat(calculatedFeeDetail.getDetentionTravelAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getDetentionTravelAndWaitingCostsAmount());
    assertThat(calculatedFeeDetail.getJrFormFillingAmount())
        .isEqualTo(feeCalculationPatch.getJrFormFillingAmount());
    assertThat(calculatedFeeDetail.getTravelAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getTravelAndWaitingCostsAmount());

    // Test fields from BoltOnPatch
    assertThat(calculatedFeeDetail.getBoltOnTotalFeeAmount())
        .isEqualTo(boltOnPatch.getBoltOnTotalFeeAmount());
    assertThat(calculatedFeeDetail.getBoltOnAdjournedHearingCount())
        .isEqualTo(boltOnPatch.getBoltOnAdjournedHearingCount());
    assertThat(calculatedFeeDetail.getBoltOnAdjournedHearingFee())
        .isEqualTo(boltOnPatch.getBoltOnAdjournedHearingFee());
    assertThat(calculatedFeeDetail.getBoltOnCmrhTelephoneCount())
        .isEqualTo(boltOnPatch.getBoltOnCmrhTelephoneCount());
    assertThat(calculatedFeeDetail.getBoltOnCmrhTelephoneFee())
        .isEqualTo(boltOnPatch.getBoltOnCmrhTelephoneFee());
    assertThat(calculatedFeeDetail.getBoltOnCmrhOralCount())
        .isEqualTo(boltOnPatch.getBoltOnCmrhOralCount());
    assertThat(calculatedFeeDetail.getBoltOnCmrhOralFee())
        .isEqualTo(boltOnPatch.getBoltOnCmrhOralFee());
    assertThat(calculatedFeeDetail.getBoltOnHomeOfficeInterviewCount())
        .isEqualTo(boltOnPatch.getBoltOnHomeOfficeInterviewCount());
    assertThat(calculatedFeeDetail.getBoltOnHomeOfficeInterviewFee())
        .isEqualTo(boltOnPatch.getBoltOnHomeOfficeInterviewFee());
    assertThat(calculatedFeeDetail.getEscapeCaseFlag()).isEqualTo(boltOnPatch.getEscapeCaseFlag());
    assertThat(calculatedFeeDetail.getSchemeId()).isEqualTo(boltOnPatch.getSchemeId());
    assertThat(calculatedFeeDetail.getBoltOnSubstantiveHearingFee())
        .isEqualTo(boltOnPatch.getBoltOnSubstantiveHearingFee());
  }

  @Test
  void updateClaimResponseFromClaimSummaryFee_mapsFields() {
    final ClaimSummaryFee summaryFee = new ClaimSummaryFee();
    summaryFee.setAdviceTime(10);
    summaryFee.setTravelTime(20);
    summaryFee.setWaitingTime(30);
    summaryFee.setNetProfitCostsAmount(new BigDecimal("123.45"));
    summaryFee.setNetDisbursementAmount(new BigDecimal("67.89"));
    summaryFee.setNetCounselCostsAmount(new BigDecimal("10.11"));
    summaryFee.setDisbursementsVatAmount(new BigDecimal("12.34"));
    summaryFee.setTravelWaitingCostsAmount(new BigDecimal("15.67"));
    summaryFee.setNetWaitingCostsAmount(new BigDecimal("18.90"));
    summaryFee.setIsVatApplicable(Boolean.TRUE);
    summaryFee.setIsToleranceApplicable(Boolean.FALSE);
    summaryFee.setPriorAuthorityReference("PA-123");
    summaryFee.setIsLondonRate(Boolean.TRUE);
    summaryFee.setAdjournedHearingFeeAmount(5);
    summaryFee.setIsAdditionalTravelPayment(Boolean.FALSE);
    summaryFee.setCostsDamagesRecoveredAmount(new BigDecimal("21.00"));
    summaryFee.setMeetingsAttendedCode("MEET1");
    summaryFee.setDetentionTravelWaitingCostsAmount(new BigDecimal("22.00"));
    summaryFee.setJrFormFillingAmount(new BigDecimal("23.00"));
    summaryFee.setIsEligibleClient(Boolean.TRUE);
    summaryFee.setCourtLocationCode("COURT01");
    summaryFee.setAdviceTypeCode("ADVICE01");
    summaryFee.setMedicalReportsCount(3);
    summaryFee.setIsIrcSurgery(Boolean.TRUE);
    summaryFee.setSurgeryDate(LocalDate.of(2025, 1, 2));
    summaryFee.setSurgeryClientsCount(4);
    summaryFee.setSurgeryMattersCount(5);
    summaryFee.setCmrhOralCount(6);
    summaryFee.setCmrhTelephoneCount(7);
    summaryFee.setAitHearingCentreCode("AITHC01");
    summaryFee.setIsSubstantiveHearing(Boolean.FALSE);
    summaryFee.setHoInterview(8);
    summaryFee.setLocalAuthorityNumber("LA-001");

    final ClaimResponse claimResponse = new ClaimResponse();

    mapper.updateClaimResponseFromClaimSummaryFee(summaryFee, claimResponse);

    assertThat(claimResponse.getAdviceTime()).isEqualTo(10);
    assertThat(claimResponse.getTravelTime()).isEqualTo(20);
    assertThat(claimResponse.getWaitingTime()).isEqualTo(30);
    assertThat(claimResponse.getNetProfitCostsAmount())
        .isEqualByComparingTo(new BigDecimal("123.45"));
    assertThat(claimResponse.getNetDisbursementAmount())
        .isEqualByComparingTo(new BigDecimal("67.89"));
    assertThat(claimResponse.getNetCounselCostsAmount())
        .isEqualByComparingTo(new BigDecimal("10.11"));
    assertThat(claimResponse.getDisbursementsVatAmount())
        .isEqualByComparingTo(new BigDecimal("12.34"));
    assertThat(claimResponse.getTravelWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("15.67"));
    assertThat(claimResponse.getNetWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("18.90"));
    assertThat(claimResponse.getIsVatApplicable()).isTrue();
    assertThat(claimResponse.getIsToleranceApplicable()).isFalse();
    assertThat(claimResponse.getPriorAuthorityReference()).isEqualTo("PA-123");
    assertThat(claimResponse.getIsLondonRate()).isTrue();
    assertThat(claimResponse.getAdjournedHearingFeeAmount()).isEqualTo(5);
    assertThat(claimResponse.getIsAdditionalTravelPayment()).isFalse();
    assertThat(claimResponse.getDetentionTravelWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("22.00"));
    assertThat(claimResponse.getSurgeryDate()).isEqualTo("2025-01-02");
    assertThat(claimResponse.getIsSubstantiveHearing()).isFalse();
    assertThat(claimResponse.getLocalAuthorityNumber()).isEqualTo("LA-001");
    assertThat(claimResponse.getMeetingsAttendedCode()).isEqualTo("MEET1");
  }

  @Test
  void updateClaimResponseFromCalculatedFeeDetail_createsNestedResponseWhenMissing() {
    final ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();
    UUID claimSummaryFeeId = Uuid7.timeBasedUuid();
    claimSummaryFee.setId(claimSummaryFeeId);

    UUID calculatedFeeDetailId = Uuid7.timeBasedUuid();

    final CalculatedFeeDetail feeDetail = new CalculatedFeeDetail();
    final Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).build();
    feeDetail.setId(calculatedFeeDetailId);
    feeDetail.setClaim(claim);
    feeDetail.setFeeCode("FEE001");
    feeDetail.setFeeCodeDescription("Fee description");
    feeDetail.setFeeType(FeeCalculationType.DISB_ONLY);
    feeDetail.setCategoryOfLaw("LAW");
    feeDetail.setTotalAmount(new BigDecimal("100.00"));
    feeDetail.setVatIndicator(Boolean.TRUE);
    feeDetail.setVatRateApplied(new BigDecimal("20.00"));
    feeDetail.setCalculatedVatAmount(new BigDecimal("20.00"));
    feeDetail.setDisbursementAmount(new BigDecimal("10.00"));
    feeDetail.setRequestedNetDisbursementAmount(new BigDecimal("9.00"));
    feeDetail.setDisbursementVatAmount(new BigDecimal("1.00"));
    feeDetail.setHourlyTotalAmount(new BigDecimal("50.00"));
    feeDetail.setFixedFeeAmount(new BigDecimal("30.00"));
    feeDetail.setNetProfitCostsAmount(new BigDecimal("40.00"));
    feeDetail.setRequestedNetProfitCostsAmount(new BigDecimal("35.00"));
    feeDetail.setNetCostOfCounselAmount(new BigDecimal("25.00"));
    feeDetail.setNetTravelCostsAmount(new BigDecimal("15.00"));
    feeDetail.setNetWaitingCostsAmount(new BigDecimal("5.00"));
    feeDetail.setDetentionTravelAndWaitingCostsAmount(new BigDecimal("3.00"));
    feeDetail.setJrFormFillingAmount(new BigDecimal("2.00"));
    feeDetail.setTravelAndWaitingCostsAmount(new BigDecimal("4.00"));
    feeDetail.setBoltOnTotalFeeAmount(new BigDecimal("6.00"));
    feeDetail.setBoltOnAdjournedHearingCount(1);
    feeDetail.setBoltOnAdjournedHearingFee(new BigDecimal("1.50"));
    feeDetail.setBoltOnCmrhTelephoneCount(2);
    feeDetail.setBoltOnCmrhTelephoneFee(new BigDecimal("2.50"));
    feeDetail.setBoltOnCmrhOralCount(3);
    feeDetail.setBoltOnCmrhOralFee(new BigDecimal("3.50"));
    feeDetail.setBoltOnHomeOfficeInterviewCount(4);
    feeDetail.setBoltOnHomeOfficeInterviewFee(new BigDecimal("4.50"));
    feeDetail.setBoltOnSubstantiveHearingFee(new BigDecimal("7.30"));
    feeDetail.setEscapeCaseFlag(Boolean.TRUE);
    feeDetail.setSchemeId("SCHEME-01");
    feeDetail.setClaimSummaryFee(claimSummaryFee);

    final ClaimResponse claimResponse = new ClaimResponse();

    mapper.updateClaimResponseFromCalculatedFeeDetail(feeDetail, claimResponse);

    final FeeCalculationPatch feeCalculationResponse = claimResponse.getFeeCalculationResponse();
    assertNotNull(feeCalculationResponse);
    assertThat(feeCalculationResponse.getClaimId()).isEqualTo(claim.getId());
    assertThat(feeCalculationResponse.getClaimSummaryFeeId()).isEqualTo(claimSummaryFeeId);
    assertThat(feeCalculationResponse.getCalculatedFeeDetailId())
        .isEqualTo(calculatedFeeDetailId.toString());
    assertThat(feeCalculationResponse.getFeeCode()).isEqualTo("FEE001");
    assertThat(feeCalculationResponse.getFeeCodeDescription()).isEqualTo("Fee description");
    assertThat(feeCalculationResponse.getFeeType()).isEqualTo(FeeCalculationType.DISB_ONLY);
    assertThat(feeCalculationResponse.getCategoryOfLaw()).isEqualTo("LAW");
    assertThat(feeCalculationResponse.getTotalAmount())
        .isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(feeCalculationResponse.getVatIndicator()).isTrue();
    assertThat(feeCalculationResponse.getVatRateApplied())
        .isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(feeCalculationResponse.getCalculatedVatAmount())
        .isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(feeCalculationResponse.getDisbursementAmount())
        .isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(feeCalculationResponse.getRequestedNetDisbursementAmount())
        .isEqualByComparingTo(new BigDecimal("9.00"));
    assertThat(feeCalculationResponse.getDisbursementVatAmount())
        .isEqualByComparingTo(new BigDecimal("1.00"));
    assertThat(feeCalculationResponse.getHourlyTotalAmount())
        .isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(feeCalculationResponse.getFixedFeeAmount())
        .isEqualByComparingTo(new BigDecimal("30.00"));
    assertThat(feeCalculationResponse.getNetProfitCostsAmount())
        .isEqualByComparingTo(new BigDecimal("40.00"));
    assertThat(feeCalculationResponse.getRequestedNetProfitCostsAmount())
        .isEqualByComparingTo(new BigDecimal("35.00"));
    assertThat(feeCalculationResponse.getNetCostOfCounselAmount())
        .isEqualByComparingTo(new BigDecimal("25.00"));
    assertThat(feeCalculationResponse.getNetTravelCostsAmount())
        .isEqualByComparingTo(new BigDecimal("15.00"));
    assertThat(feeCalculationResponse.getNetWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("5.00"));
    assertThat(feeCalculationResponse.getDetentionTravelAndWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("3.00"));
    assertThat(feeCalculationResponse.getJrFormFillingAmount())
        .isEqualByComparingTo(new BigDecimal("2.00"));
    assertThat(feeCalculationResponse.getTravelAndWaitingCostsAmount())
        .isEqualByComparingTo(new BigDecimal("4.00"));

    final BoltOnPatch boltOnDetails = feeCalculationResponse.getBoltOnDetails();
    assertNotNull(boltOnDetails);
    assertThat(boltOnDetails.getBoltOnTotalFeeAmount())
        .isEqualByComparingTo(new BigDecimal("6.00"));
    assertThat(boltOnDetails.getBoltOnAdjournedHearingCount()).isEqualTo(1);
    assertThat(boltOnDetails.getBoltOnAdjournedHearingFee())
        .isEqualByComparingTo(new BigDecimal("1.50"));
    assertThat(boltOnDetails.getBoltOnCmrhTelephoneCount()).isEqualTo(2);
    assertThat(boltOnDetails.getBoltOnCmrhTelephoneFee())
        .isEqualByComparingTo(new BigDecimal("2.50"));
    assertThat(boltOnDetails.getBoltOnCmrhOralCount()).isEqualTo(3);
    assertThat(boltOnDetails.getBoltOnCmrhOralFee()).isEqualByComparingTo(new BigDecimal("3.50"));
    assertThat(boltOnDetails.getBoltOnHomeOfficeInterviewCount()).isEqualTo(4);
    assertThat(boltOnDetails.getBoltOnHomeOfficeInterviewFee())
        .isEqualByComparingTo(new BigDecimal("4.50"));
    assertThat(boltOnDetails.getBoltOnSubstantiveHearingFee())
        .isEqualByComparingTo(new BigDecimal("7.30"));
    assertThat(boltOnDetails.getEscapeCaseFlag()).isTrue();
    assertThat(boltOnDetails.getSchemeId()).isEqualTo("SCHEME-01");
  }

  @Test
  void updateClaimResponseFromCalculatedFeeDetail_reusesExistingNestedObjects() {
    final CalculatedFeeDetail feeDetail = new CalculatedFeeDetail();
    final Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).build();
    feeDetail.setClaim(claim);
    feeDetail.setFeeCode("NEW-CODE");
    feeDetail.setBoltOnTotalFeeAmount(new BigDecimal("12.34"));
    feeDetail.setSchemeId("NEW-SCHEME");

    final FeeCalculationPatch existingResponse = new FeeCalculationPatch().feeCode("OLD-CODE");
    final BoltOnPatch existingBoltOn = new BoltOnPatch().schemeId("OLD-SCHEME");
    existingResponse.setBoltOnDetails(existingBoltOn);

    final ClaimResponse claimResponse =
        new ClaimResponse().feeCalculationResponse(existingResponse);

    mapper.updateClaimResponseFromCalculatedFeeDetail(feeDetail, claimResponse);

    assertThat(claimResponse.getFeeCalculationResponse()).isSameAs(existingResponse);
    assertThat(claimResponse.getFeeCalculationResponse().getBoltOnDetails())
        .isSameAs(existingBoltOn);
    assertThat(existingResponse.getFeeCode()).isEqualTo("NEW-CODE");
    assertThat(existingBoltOn.getSchemeId()).isEqualTo("NEW-SCHEME");
    assertThat(existingBoltOn.getBoltOnTotalFeeAmount())
        .isEqualByComparingTo(new BigDecimal("12.34"));
  }

  private static BoltOnPatch getBoltOnPatch() {
    final BoltOnPatch boltOnPatch = new BoltOnPatch();
    boltOnPatch.boltOnTotalFeeAmount(new BigDecimal("345.07"));
    boltOnPatch.boltOnAdjournedHearingCount(4);
    boltOnPatch.boltOnAdjournedHearingFee(new BigDecimal("145.90"));
    boltOnPatch.boltOnCmrhTelephoneCount(0);
    boltOnPatch.boltOnCmrhTelephoneFee(new BigDecimal("25.12"));
    boltOnPatch.boltOnCmrhOralCount(5);
    boltOnPatch.boltOnCmrhOralFee(new BigDecimal("44.59"));
    boltOnPatch.boltOnHomeOfficeInterviewCount(7);
    boltOnPatch.boltOnHomeOfficeInterviewFee(new BigDecimal("945.23"));
    boltOnPatch.boltOnSubstantiveHearingFee(new BigDecimal("1245.45"));
    boltOnPatch.escapeCaseFlag(true);
    boltOnPatch.schemeId("SCHEME_ID");
    return boltOnPatch;
  }

  @Test
  void toCalculatedFeeDetail_nullPatch_noChanges() {
    assertNull(mapper.toCalculatedFeeDetail(null));
  }

  @Test
  void toCalculatedFeeDetail_withNullBoltOnPatch_mapsAllFieldsButBoltOnPatch() {
    final FeeCalculationPatch feeCalculationPatch = getFeeCalculationPatch();

    final CalculatedFeeDetail calculatedFeeDetail =
        mapper.toCalculatedFeeDetail(feeCalculationPatch);

    // test CalculatedFeeDetail fields
    assertNotNull(calculatedFeeDetail.getId());
    assertThat(calculatedFeeDetail.getFeeCode()).isEqualTo(feeCalculationPatch.getFeeCode());
    assertThat(calculatedFeeDetail.getFeeCodeDescription())
        .isEqualTo(feeCalculationPatch.getFeeCodeDescription());
    assertThat(calculatedFeeDetail.getFeeType()).isEqualTo(feeCalculationPatch.getFeeType());
    assertThat(calculatedFeeDetail.getCategoryOfLaw())
        .isEqualTo(feeCalculationPatch.getCategoryOfLaw());
    assertThat(calculatedFeeDetail.getTotalAmount())
        .isEqualTo(feeCalculationPatch.getTotalAmount());
    assertThat(calculatedFeeDetail.getVatIndicator())
        .isEqualTo(feeCalculationPatch.getVatIndicator());
    assertThat(calculatedFeeDetail.getVatRateApplied())
        .isEqualTo(feeCalculationPatch.getVatRateApplied());
    assertThat(calculatedFeeDetail.getCalculatedVatAmount())
        .isEqualTo(feeCalculationPatch.getCalculatedVatAmount());
    assertThat(calculatedFeeDetail.getDisbursementAmount())
        .isEqualTo(feeCalculationPatch.getDisbursementAmount());
    assertThat(calculatedFeeDetail.getRequestedNetDisbursementAmount())
        .isEqualTo(feeCalculationPatch.getRequestedNetDisbursementAmount());
    assertThat(calculatedFeeDetail.getDisbursementVatAmount())
        .isEqualTo(feeCalculationPatch.getDisbursementVatAmount());
    assertThat(calculatedFeeDetail.getHourlyTotalAmount())
        .isEqualTo(feeCalculationPatch.getHourlyTotalAmount());
    assertThat(calculatedFeeDetail.getFixedFeeAmount())
        .isEqualTo(feeCalculationPatch.getFixedFeeAmount());
    assertThat(calculatedFeeDetail.getNetProfitCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetProfitCostsAmount());
    assertThat(calculatedFeeDetail.getRequestedNetProfitCostsAmount())
        .isEqualTo(feeCalculationPatch.getRequestedNetProfitCostsAmount());
    assertThat(calculatedFeeDetail.getNetCostOfCounselAmount())
        .isEqualTo(feeCalculationPatch.getNetCostOfCounselAmount());
    assertThat(calculatedFeeDetail.getNetTravelCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetTravelCostsAmount());
    assertThat(calculatedFeeDetail.getNetWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getNetWaitingCostsAmount());
    assertThat(calculatedFeeDetail.getDetentionTravelAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getDetentionTravelAndWaitingCostsAmount());
    assertThat(calculatedFeeDetail.getJrFormFillingAmount())
        .isEqualTo(feeCalculationPatch.getJrFormFillingAmount());
    assertThat(calculatedFeeDetail.getTravelAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getTravelAndWaitingCostsAmount());

    // Test fields from BoltOnPatch are null
    assertNull(calculatedFeeDetail.getBoltOnTotalFeeAmount());
    assertNull(calculatedFeeDetail.getBoltOnAdjournedHearingCount());
    assertNull(calculatedFeeDetail.getBoltOnAdjournedHearingFee());
    assertNull(calculatedFeeDetail.getBoltOnCmrhTelephoneCount());
    assertNull(calculatedFeeDetail.getBoltOnCmrhTelephoneFee());
    assertNull(calculatedFeeDetail.getBoltOnCmrhOralCount());
    assertNull(calculatedFeeDetail.getBoltOnCmrhOralFee());
    assertNull(calculatedFeeDetail.getBoltOnHomeOfficeInterviewCount());
    assertNull(calculatedFeeDetail.getBoltOnHomeOfficeInterviewFee());
    assertNull(calculatedFeeDetail.getEscapeCaseFlag());
    assertNull(calculatedFeeDetail.getSchemeId());
  }

  @Test
  void toClaimCase_mapsAllFields() {
    final ClaimPost post = ClaimsDataTestUtil.getClaimPost(CASE_REFERENCE);

    final ClaimCase claimCase = mapper.toClaimCase(post);

    assertThat(claimCase.getCaseId()).isEqualTo(post.getCaseId());
    assertThat(claimCase.getUniqueCaseId()).isEqualTo(post.getUniqueCaseId());
    assertThat(claimCase.getCaseStageCode()).isEqualTo(post.getCaseStageCode());
    assertThat(claimCase.getStageReachedCode()).isEqualTo(post.getStageReachedCode());
    assertThat(claimCase.getStandardFeeCategoryCode()).isEqualTo(post.getStandardFeeCategoryCode());
    assertThat(claimCase.getOutcomeCode()).isEqualTo(post.getOutcomeCode());
    assertThat(claimCase.getDesignatedAccreditedRepresentativeCode())
        .isEqualTo(post.getDesignatedAccreditedRepresentativeCode());
    assertThat(claimCase.getIsPostalApplicationAccepted())
        .isEqualTo(post.getIsPostalApplicationAccepted());
    assertThat(claimCase.getIsClient2PostalApplicationAccepted())
        .isEqualTo(post.getIsClient2PostalApplicationAccepted());
    assertThat(claimCase.getMentalHealthTribunalReference())
        .isEqualTo(post.getMentalHealthTribunalReference());
    assertThat(claimCase.getIsNrmAdvice()).isEqualTo(post.getIsNrmAdvice());
    assertThat(claimCase.getFollowOnWork()).isEqualTo(post.getFollowOnWork());
    assertThat(claimCase.getTransferDate().format(DateTimeFormatter.ofPattern("d/M/yyyy")))
        .isEqualTo(post.getTransferDate());
    assertThat(claimCase.getExemptionCriteriaSatisfied())
        .isEqualTo(post.getExemptionCriteriaSatisfied());
    assertThat(claimCase.getExceptionalCaseFundingReference())
        .isEqualTo(post.getExceptionalCaseFundingReference());
    assertThat(claimCase.getIsLegacyCase()).isEqualTo(post.getIsLegacyCase());
  }

  @Test
  void toClaimCase_nullPost_noChanges() {
    assertNull(mapper.toClaimCase(null));
  }

  private static FeeCalculationPatch getFeeCalculationPatch() {
    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    feeCalculationPatch.calculatedFeeDetailId("FEE_DETAIL_ID");
    feeCalculationPatch.claimSummaryFeeId(Uuid7.timeBasedUuid());
    feeCalculationPatch.claimId(Uuid7.timeBasedUuid());
    feeCalculationPatch.feeCode("FEE_CODE");
    feeCalculationPatch.feeCodeDescription("FEE_DESCRIPTION");
    feeCalculationPatch.feeType(FeeCalculationType.DISB_ONLY);
    feeCalculationPatch.categoryOfLaw("CRIME");
    feeCalculationPatch.totalAmount(new BigDecimal("768.45"));
    feeCalculationPatch.vatIndicator(true);
    feeCalculationPatch.vatRateApplied(new BigDecimal("20.00"));
    feeCalculationPatch.calculatedVatAmount(new BigDecimal("155.07"));
    feeCalculationPatch.disbursementAmount(new BigDecimal("345.26"));
    feeCalculationPatch.requestedNetDisbursementAmount(new BigDecimal("546.12"));
    feeCalculationPatch.disbursementVatAmount(new BigDecimal("25.00"));
    feeCalculationPatch.hourlyTotalAmount(new BigDecimal("65.00"));
    feeCalculationPatch.fixedFeeAmount(new BigDecimal("345.07"));
    feeCalculationPatch.netProfitCostsAmount(new BigDecimal("245.07"));
    feeCalculationPatch.requestedNetProfitCostsAmount(new BigDecimal("615.56"));
    feeCalculationPatch.netCostOfCounselAmount(new BigDecimal("156.78"));
    feeCalculationPatch.netTravelCostsAmount(new BigDecimal("365.87"));
    feeCalculationPatch.netWaitingCostsAmount(new BigDecimal("274.25"));
    feeCalculationPatch.detentionTravelAndWaitingCostsAmount(new BigDecimal("347.63"));
    feeCalculationPatch.jrFormFillingAmount(new BigDecimal("612.98"));
    feeCalculationPatch.travelAndWaitingCostsAmount(new BigDecimal("398.12"));
    return feeCalculationPatch;
  }

  @Test
  void shouldAddTotalWarningMessages() {
    // Given
    ClaimResponse claimResponse = ClaimResponse.builder().build();
    // When
    mapper.updateTotalWarningMessages(123L, claimResponse);
    // Then
    assertThat(claimResponse.getTotalWarnings()).isEqualTo(123L);
  }
}
