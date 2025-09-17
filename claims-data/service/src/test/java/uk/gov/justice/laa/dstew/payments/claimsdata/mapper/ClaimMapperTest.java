package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

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
    final ClaimPost post =
        new ClaimPost()
            .isDutySolicitor(true)
            .isYouthCourt(false)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference("SCH123")
            .lineNumber(5)
            .caseReferenceNumber("CASE001")
            .uniqueFileNumber("UFN123")
            .caseStartDate("01/01/2020")
            .caseConcludedDate("02/01/2020")
            .matterTypeCode("MTC")
            .crimeMatterTypeCode("CMTC")
            .feeSchemeCode("FSC")
            .feeCode("FC")
            .procurementAreaCode("PAC")
            .accessPointCode("APC")
            .deliveryLocation("DEL")
            .representationOrderDate("01/01/2020")
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
            .referralSource("REFSRC");

    final Claim entity = mapper.toClaim(post);

    assertNotNull(entity);
    assertEquals(post.getIsDutySolicitor(), entity.getDutySolicitor());
    assertEquals(post.getIsYouthCourt(), entity.getYouthCourt());
    assertEquals(post.getStatus().name(), entity.getStatus());
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
            .status(ClaimStatus.READY_TO_PROCESS.getValue())
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
    final UUID id = UUID.randomUUID();
    final Claim entity = Claim.builder().id(id).status("READY_TO_PROCESS").build();

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
            .status("OLD")
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
    assertEquals("READY_TO_PROCESS", entity.getStatus());
    assertEquals("NEW_SCH", entity.getScheduleReference());
  }

  @Test
  void toValidationMessageLog_mapsFields() {
    final Submission submission = Submission.builder().id(UUID.randomUUID()).build();
    final Claim claim = Claim.builder().id(UUID.randomUUID()).submission(submission).build();

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
}
