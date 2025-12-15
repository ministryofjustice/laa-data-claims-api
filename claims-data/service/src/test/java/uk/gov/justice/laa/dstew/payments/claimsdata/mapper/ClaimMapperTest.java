package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class ClaimMapperTest {

  @InjectMocks private final ClaimMapperImpl mapper = new ClaimMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

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
    assertEquals(entity.getTotalValue(), fields.getTotalValue());
    assertEquals(entity.getSubmission().getId().toString(), fields.getSubmissionId());
    assertEquals(entity.getSubmission().getSubmissionPeriod(), fields.getSubmissionPeriod());
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

  @Test
  void updateSubmissionClaimFromPatch_nullPatch_noChanges() {
    final Claim entity = Claim.builder().dutySolicitor(true).build();
    mapper.updateSubmissionClaimFromPatch(null, entity);
    assertTrue(entity.getDutySolicitor());
  }

  @Test
  void updateSubmissionClaimFromPatch_updatesOnlyNonNullFields() {
    final Claim entity =
        Claim.builder()
            .dutySolicitor(false)
            .youthCourt(false)
            .status(ClaimStatus.INVALID)
            .scheduleReference("OLD_SCH")
            .build();

    final ClaimPatch patch =
        new ClaimPatch()
            .isDutySolicitor(true)
            .isYouthCourt(true)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference("NEW_SCH");

    mapper.updateSubmissionClaimFromPatch(patch, entity);

    assertTrue(entity.getDutySolicitor());
    assertTrue(entity.getYouthCourt());
    assertEquals(ClaimStatus.READY_TO_PROCESS, entity.getStatus());
    assertEquals("NEW_SCH", entity.getScheduleReference());
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
