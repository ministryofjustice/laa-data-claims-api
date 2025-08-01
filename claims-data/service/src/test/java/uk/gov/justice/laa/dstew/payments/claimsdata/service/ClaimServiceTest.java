package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.javers.core.Javers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRequestBody;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

  @Mock
  private ClaimRepository mockClaimRepository;

  @Mock
  private ClaimMapper mockClaimMapper;

  @InjectMocks
  private ClaimService claimService;

  @Mock
  private Javers javers;

  @Test
  void shouldGetAllClaims() {
    ClaimEntity firstClaimEntity = new ClaimEntity(1L, "Claim One", "This is Claim One.");
    ClaimEntity secondClaimEntity = new ClaimEntity(2L, "Claim Two", "This is Claim Two.");
    Claim firstClaim = Claim.builder().id(1L).name("Claim One").description("This is Claim One.").build();
    Claim secondClaim = Claim.builder().id(2L).name("Claim Two").description("This is Claim Two.").build();
    when(mockClaimRepository.findAll()).thenReturn(List.of(firstClaimEntity, secondClaimEntity));
    when(mockClaimMapper.toClaim(firstClaimEntity)).thenReturn(firstClaim);
    when(mockClaimMapper.toClaim(secondClaimEntity)).thenReturn(secondClaim);

    List<Claim> result = claimService.getAllClaims();

    assertThat(result).hasSize(2).contains(firstClaim, secondClaim);
  }

  @Test
  void shouldGetClaimById() {
    Long id = 1L;
    String name = "Claim One";
    String description = "This is Claim One.";
    ClaimEntity claimEntity = new ClaimEntity(id, name, description);
    Claim claim = Claim.builder().id(id).name(name).description(description).build();
    when(mockClaimRepository.findById(id)).thenReturn(Optional.of(claimEntity));
    when(mockClaimMapper.toClaim(claimEntity)).thenReturn(claim);

    Claim result = claimService.getClaim(id);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getName()).isEqualTo(name);
  }

  @Test
  void shouldNotGetClaimById_whenClaimNotFoundThenThrowsException() {
    Long id = 5L;
    when(mockClaimRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ClaimNotFoundException.class, () -> claimService.getClaim(id));

    verify(mockClaimMapper, never()).toClaim(any(ClaimEntity.class));
  }

  @Test
  void shouldCreateClaim() {
    ClaimRequestBody claimRequestBody = ClaimRequestBody.builder().name("Claim Three").description("This is Claim Three.").build();
    ClaimEntity claimEntity = new ClaimEntity(3L, "Claim Three", "This is Claim Three.");
    when(mockClaimRepository.save(new ClaimEntity(null, "Claim Three", "This is Claim Three.")))
        .thenReturn(claimEntity);

    Long result = claimService.createClaim(claimRequestBody);

    assertThat(result).isNotNull().isEqualTo(3L);
  }

  @Test
  void shouldUpdateClaim() {
    Long id = 1L;
    String name = "Claim One";
    String description = "This is Claim One.";
    ClaimRequestBody claimRequestBody = ClaimRequestBody.builder().name(name).description(description).build();
    ClaimEntity claimEntity = new ClaimEntity(id, name, description);
    when(mockClaimRepository.findById(id)).thenReturn(Optional.of(claimEntity));

    claimService.updateClaim(id, claimRequestBody);

    verify(mockClaimRepository).save(claimEntity);
  }

  @Test
  void shouldNotUpdateClaim_whenClaimNotFoundThenThrowsException() {
    Long id = 5L;
    ClaimRequestBody claimRequestBody = ClaimRequestBody.builder().name("Claim Five").description("This is Claim Five.").build();
    when(mockClaimRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ClaimNotFoundException.class, () -> claimService.updateClaim(id, claimRequestBody));

    verify(mockClaimRepository, never()).save(any(ClaimEntity.class));
  }

  @Test
  void shouldDeleteClaim() {
    Long id = 1L;
    ClaimEntity claimEntity = new ClaimEntity(id, "Claim One", "This is Claim One.");
    when(mockClaimRepository.findById(id)).thenReturn(Optional.of(claimEntity));

    claimService.deleteClaim(id);

    verify(mockClaimRepository).deleteById(id);
  }

  @Test
  void shouldNotDeleteClaim_whenClaimNotFoundThenThrowsException() {
    Long id = 5L;
    when(mockClaimRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ClaimNotFoundException.class, () -> claimService.deleteClaim(id));

    verify(mockClaimRepository, never()).deleteById(id);
  }
}
