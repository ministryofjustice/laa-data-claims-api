package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;

/** Service responsible for handling the processing of bulk submission objects. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkSubmissionService implements AbstractEntityLookup<BulkSubmission, BulkSubmissionRepository, BulkSubmissionNotFoundException> {

  private final BulkSubmissionFileService bulkSubmissionFileService;
  private final BulkSubmissionRepository bulkSubmissionRepository;
  private final BulkSubmissionMapper submissionMapper;

  @Override
  public BulkSubmissionRepository lookup() {
    return bulkSubmissionRepository;
  }

  @Override
  public Supplier<BulkSubmissionNotFoundException> entityNotFoundSupplier(String message) {
    return () -> new BulkSubmissionNotFoundException(message);
  }

  /**
   * Processes a bulk submission from the provided multipart file and returns a response with
   * the bulk submission ID and the list of submission IDs.
   *
   * @param file the multipart file containing bulk submission data; must not be null.
   * @return a {@link CreateBulkSubmission201Response} object containing the ID of the bulk
   *         submission and the list of submitted ids (NOTE: we should only get one submission within the bulk).
   */
  public CreateBulkSubmission201Response submitBulkSubmissionFile(
      @NotNull String userId,
      @NotNull MultipartFile file
  ) {
    GetBulkSubmission200ResponseDetails bulkSubmissionDetails = getBulkSubmissionDetails(file);

    BulkSubmission.BulkSubmissionBuilder bulkSubmissionBuilder = BulkSubmission.builder();

    bulkSubmissionBuilder.data(bulkSubmissionDetails);
    bulkSubmissionBuilder.status(BulkSubmissionStatus.READY_FOR_PARSING);
    bulkSubmissionBuilder.createdByUserId(userId);
    BulkSubmission bulkSubmission = bulkSubmissionBuilder.build();

    bulkSubmissionRepository.save(bulkSubmission);

    return new CreateBulkSubmission201Response()
        .bulkSubmissionId(bulkSubmission.getId())
        .submissionIds(Collections.singletonList(UUID.randomUUID()));
  }

  /**
   * Converts the provided file to a Java object based on the filename extension, then maps it to
   * and returns a {@link GetBulkSubmission200ResponseDetails}.
   *
   * @param file the file to convert.
   * @return a {@link GetBulkSubmission200ResponseDetails} representing the provided input file.
   */
  public GetBulkSubmission200ResponseDetails getBulkSubmissionDetails(MultipartFile file) {
    FileSubmission fileSubmission = bulkSubmissionFileService.convert(file);

    return submissionMapper.toBulkSubmissionDetails(fileSubmission);
  }

  /**
   * Retrieve a bulk submission by its identifier.
   *
   * @param id the bulk submission id
   * @return bulk submission response model
   */
  @Transactional(readOnly = true)
  public GetBulkSubmission200Response getBulkSubmission(UUID id) {
    BulkSubmission bulkSubmission = requireEntity(id);

    return new GetBulkSubmission200Response()
            .bulkSubmissionId(id)
            .status(bulkSubmission.getStatus())
            .details(bulkSubmission.getData());
  }
}
