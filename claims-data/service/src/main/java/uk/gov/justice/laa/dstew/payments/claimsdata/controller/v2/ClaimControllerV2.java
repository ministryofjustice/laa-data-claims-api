package uk.gov.justice.laa.dstew.payments.claimsdata.controller.v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.payments.claimsdata.api.ClaimsApi;

/** Controller for handling claims requests. */
@RestController
@RequestMapping(version = "1")
@RequiredArgsConstructor
@Slf4j
public class ClaimControllerV2 implements ClaimsApi {
}
