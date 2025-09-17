package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

@ExtendWith(MockitoExtension.class)
class ClaimResultSetMapperImplTest {

  @Spy private ClaimMapper claimMapper;

  @InjectMocks private ClaimResultSetMapperImpl mapper = new ClaimResultSetMapperImpl();

  @Test
  void toClaimResultSet_shouldMapPageAttributes() {
    Page<Claim> input = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 20), 30);

    ClaimResultSet resultSet = mapper.toClaimResultSet(input);

    assertEquals(1, resultSet.getNumber());
    assertEquals(20, resultSet.getSize());
    assertEquals(2, resultSet.getTotalPages());
    assertEquals(30, resultSet.getTotalElements());
  }

  @Test
  void toClaimResultSet_shouldReturnClaimData() {
    var expectedClaim = ClaimsDataTestUtil.getClaimBuilder().build();
    var expectedClient = ClaimsDataTestUtil.getClientBuilder().claim(expectedClaim).build();

    Page<Claim> page = new PageImpl<>(Collections.singletonList(expectedClaim));

    var claimResponse = ClaimsDataTestUtil.getClaimResponse();
    when(claimMapper.toClaimResponse(expectedClaim)).thenReturn(claimResponse);

    var claimResultSet = mapper.toClaimResultSet(page);
    var actualClaimResponse = claimResultSet.getContent().getFirst();

    // Verify Claim attributes in the actual claim response
    assertThat(actualClaimResponse.getScheduleReference())
        .isEqualTo(expectedClaim.getScheduleReference());
    assertThat(actualClaimResponse.getCaseReferenceNumber())
        .isEqualTo(expectedClaim.getCaseReferenceNumber());
    assertThat(actualClaimResponse.getUniqueFileNumber())
        .isEqualTo(expectedClaim.getUniqueFileNumber());
    assertThat(actualClaimResponse.getCaseStartDate())
        .isEqualTo(expectedClaim.getCaseStartDate().toString());
    assertThat(actualClaimResponse.getCaseConcludedDate())
        .isEqualTo(expectedClaim.getCaseConcludedDate().toString());
    assertThat(actualClaimResponse.getFeeCode()).isEqualTo(expectedClaim.getFeeCode());
    assertThat(actualClaimResponse.getProcurementAreaCode())
        .isEqualTo(expectedClaim.getProcurementAreaCode());
    assertThat(actualClaimResponse.getAccessPointCode())
        .isEqualTo(expectedClaim.getAccessPointCode());
    assertThat(actualClaimResponse.getDeliveryLocation())
        .isEqualTo(expectedClaim.getDeliveryLocation());

    // Verify Client attributes in the actual claim response
    assertThat(actualClaimResponse.getClientForename())
        .isEqualTo(expectedClient.getClientForename());
    assertThat(actualClaimResponse.getClientSurname()).isEqualTo(expectedClient.getClientSurname());
    assertThat(actualClaimResponse.getClientDateOfBirth())
        .isEqualTo(expectedClient.getClientDateOfBirth().toString());
    assertThat(actualClaimResponse.getUniqueClientNumber())
        .isEqualTo(expectedClient.getUniqueClientNumber());
    assertThat(actualClaimResponse.getClientPostcode())
        .isEqualTo(expectedClient.getClientPostcode());
    assertThat(actualClaimResponse.getIsLegallyAided())
        .isEqualTo(expectedClient.getIsLegallyAided());
    assertThat(actualClaimResponse.getClientTypeCode())
        .isEqualTo(expectedClient.getClientTypeCode());
    assertThat(actualClaimResponse.getHomeOfficeClientNumber())
        .isEqualTo(expectedClient.getHomeOfficeClientNumber());
  }
}
