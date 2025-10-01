package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    final ClaimPost post = ClaimsDataTestUtil.getClaimPost();

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
    final Claim entity =
        Claim.builder()
            .dutySolicitor(true)
            .youthCourt(false)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference("SCH123")
            .lineNumber(5)
            .caseReferenceNumber("CASE001")
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
    final ClaimPost post = ClaimsDataTestUtil.getClaimPost();

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

    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    feeCalculationPatch.calculatedFeeDetailId("FEE_DETAIL_ID");
    feeCalculationPatch.claimSummaryFeeId(Uuid7.timeBasedUuid());
    feeCalculationPatch.claimId(Uuid7.timeBasedUuid());
    feeCalculationPatch.feeCode("FEE_CODE");
    feeCalculationPatch.feeCodeDescription("FEE_DESCRIPTION");
    feeCalculationPatch.feeType(FeeCalculationType.DISBURSEMENT_ONLY);
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
    feeCalculationPatch.detentionAndWaitingCostsAmount(new BigDecimal("347.63"));
    feeCalculationPatch.jrFormFillingAmount(new BigDecimal("612.98"));
    feeCalculationPatch.travelAndWaitingCostsAmount(new BigDecimal("398.12"));
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
    // TODO: total_amount needs to be a NUMERIC and not TEXT on the entity?
    // assertThat(calculatedFeeDetail.getTotalAmount()).isEqualTo(feeCalculationPatch.getTotalAmount());
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
    assertThat(calculatedFeeDetail.getDetentionAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getDetentionAndWaitingCostsAmount());
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
    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    feeCalculationPatch.calculatedFeeDetailId("FEE_DETAIL_ID");
    feeCalculationPatch.claimSummaryFeeId(Uuid7.timeBasedUuid());
    feeCalculationPatch.claimId(Uuid7.timeBasedUuid());
    feeCalculationPatch.feeCode("FEE_CODE");
    feeCalculationPatch.feeCodeDescription("FEE_DESCRIPTION");
    feeCalculationPatch.feeType(FeeCalculationType.DISBURSEMENT_ONLY);
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
    feeCalculationPatch.detentionAndWaitingCostsAmount(new BigDecimal("347.63"));
    feeCalculationPatch.jrFormFillingAmount(new BigDecimal("612.98"));
    feeCalculationPatch.travelAndWaitingCostsAmount(new BigDecimal("398.12"));

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
    // TODO: total_amount needs to be a NUMERIC and not TEXT on the entity?
    // assertThat(calculatedFeeDetail.getTotalAmount()).isEqualTo(feeCalculationPatch.getTotalAmount());
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
    assertThat(calculatedFeeDetail.getDetentionAndWaitingCostsAmount())
        .isEqualTo(feeCalculationPatch.getDetentionAndWaitingCostsAmount());
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
}
