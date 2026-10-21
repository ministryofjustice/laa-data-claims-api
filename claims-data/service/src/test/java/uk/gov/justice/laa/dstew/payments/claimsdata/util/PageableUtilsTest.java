package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableUtilsTest {

  private static final Sort FIXED_SORT =
      Sort.by(Sort.Order.desc("updatedOn"), Sort.Order.asc("id"));

  @Test
  void shouldApplyDefaultsAndFixedSortToUnpagedRequest() {
    Pageable result = PageableUtils.withDefaultPaginationAndSort(Pageable.unpaged(), FIXED_SORT);

    assertThat(result.getPageNumber()).isEqualTo(PageableUtils.DEFAULT_PAGE_NUMBER);
    assertThat(result.getPageSize()).isEqualTo(PageableUtils.DEFAULT_PAGE_SIZE);
    assertThat(result.getSort()).isEqualTo(FIXED_SORT);
  }

  @Test
  void shouldPreservePageAndSizeButReplaceCallerSort() {
    Pageable requested = PageRequest.of(3, 50, Sort.by("client.clientSurname"));

    Pageable result = PageableUtils.withDefaultPaginationAndSort(requested, FIXED_SORT);

    assertThat(result.getPageNumber()).isEqualTo(3);
    assertThat(result.getPageSize()).isEqualTo(50);
    assertThat(result.getSort()).isEqualTo(FIXED_SORT);
  }
}
