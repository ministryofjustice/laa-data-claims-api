package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.DepartmentReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.InquestDepartmentReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.DepartmentReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@Transactional
class InquestDepartmentReferenceControllerIntegrationTest extends AbstractIntegrationTest {
  @Autowired private DepartmentReferenceRepository departmentReferenceRepository;

  @Test
  void returnsAllSeededDepartmentsInDisplayOrderIncludingInactive() throws Exception {
    DepartmentReference inactive = new DepartmentReference();
    inactive.setId(Uuid7.timeBasedUuid());
    inactive.setCode("OLD");
    inactive.setDisplayLabel("Former department");
    inactive.setDisplayOrder(999);
    inactive.setIsActive(false);
    inactive.setCreatedByUserId("integration-test");
    inactive.setCreatedOn(Instant.now());
    departmentReferenceRepository.save(inactive);

    MvcResult result =
        mockMvc
            .perform(
                get(SystemReferencePaths.INQUEST_DEPARTMENTS)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    InquestDepartmentReference[] response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), InquestDepartmentReference[].class);
    assertThat(response).hasSize(25);
    assertThat(response).extracting("displayOrder").isSorted();
    assertThat(response).anyMatch(department -> !department.getIsActive());
  }
}
