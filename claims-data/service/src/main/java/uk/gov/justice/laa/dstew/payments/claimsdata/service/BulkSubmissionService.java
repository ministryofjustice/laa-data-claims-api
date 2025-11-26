package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionAreaOfLawException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionOfficeAuthorisationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.laa.springboot.exception.ApplicationException;

/** Service responsible for handling the processing of bulk submission objects. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkSubmissionService
    implements AbstractEntityLookup<
        BulkSubmission, BulkSubmissionRepository, BulkSubmissionNotFoundException> {

  private final BulkSubmissionFileService bulkSubmissionFileService;
  private final BulkSubmissionRepository bulkSubmissionRepository;
  private final BulkSubmissionMapper bulkSubmissionMapper;
  private final SubmissionEventPublisherService submissionEventPublisherService;

  @Override
  public BulkSubmissionRepository lookup() {
    return bulkSubmissionRepository;
  }

  @Override
  public Supplier<BulkSubmissionNotFoundException> entityNotFoundSupplier(String message) {
    return () -> new BulkSubmissionNotFoundException(message);
  }

  /**
   * Processes a bulk submission from the provided multipart file and returns a response with the
   * bulk submission ID and the list of submission IDs.
   *
   * @param file the multipart file containing bulk submission data; must not be null.
   * @return a {@link CreateBulkSubmission201Response} object containing the ID of the bulk
   *     submission and the list of submitted ids (NOTE: we should only get one submission within
   *     the bulk).
   */
  public CreateBulkSubmission201Response submitBulkSubmissionFile(
      @NotNull String userId, @NotNull MultipartFile file, final List<String> offices)
      throws ApplicationException {

    GetBulkSubmission200ResponseDetails bulkSubmissionDetails = getBulkSubmissionDetails(file);
    String areaOfLaw =
        Optional.ofNullable(bulkSubmissionDetails)
            .map(GetBulkSubmission200ResponseDetails::getSchedule)
            .map(GetBulkSubmission200ResponseDetailsSchedule::getAreaOfLaw)
            .orElse(null);

    validateAreaOfLaw(areaOfLaw);

    UUID bulkSubmissionId = Uuid7.timeBasedUuid();

    BulkSubmission.BulkSubmissionBuilder bulkSubmissionBuilder =
        BulkSubmission.builder()
            .id(bulkSubmissionId)
            .data(bulkSubmissionDetails)
            .createdByUserId(userId)
            .authorisedOffices(String.join(",", offices));

    validateOfficeCodeAndAccessPermissions(offices, bulkSubmissionDetails, bulkSubmissionBuilder);

    validateSubmissionPeriod(bulkSubmissionDetails, bulkSubmissionBuilder);

    validateDateFormats(bulkSubmissionDetails, bulkSubmissionBuilder);

    BulkSubmission authorised =
        bulkSubmissionBuilder.status(BulkSubmissionStatus.READY_FOR_PARSING).build();

    bulkSubmissionRepository.save(authorised);

    UUID newSubmissionId = Uuid7.timeBasedUuid();
    submissionEventPublisherService.publishBulkSubmissionEvent(
        authorised.getId(), List.of(newSubmissionId));

    return new CreateBulkSubmission201Response()
        .bulkSubmissionId(authorised.getId())
        .submissionIds(Collections.singletonList(newSubmissionId));
  }

  private void validateSubmissionPeriod(
      GetBulkSubmission200ResponseDetails bulkSubmissionDetails,
      BulkSubmission.BulkSubmissionBuilder bulkSubmissionBuilder) {
    Optional<String> submissionPeriod =
        Optional.ofNullable(bulkSubmissionDetails)
            .map(GetBulkSubmission200ResponseDetails::getSchedule)
            .map(GetBulkSubmission200ResponseDetailsSchedule::getSubmissionPeriod);

    if (submissionPeriod.isEmpty() || submissionPeriod.get().isBlank()) {
      failSubmission(
          "Submission period is required, please check the file and try again.",
          bulkSubmissionBuilder);

    } else if (!isValidMonthYear(submissionPeriod.get())) {
      failSubmission(
          "Submission period wrong format, should be in the format MMM-YYYY",
          bulkSubmissionBuilder);
    }
  }

  private void failSubmission(String errorMessage, BulkSubmission.BulkSubmissionBuilder builder) {
    BulkSubmission invalid =
        builder
            .status(BulkSubmissionStatus.VALIDATION_FAILED)
            .errorCode(BulkSubmissionErrorCode.V100)
            .errorDescription(errorMessage)
            .build();

    bulkSubmissionRepository.save(invalid);
    throw new BulkSubmissionValidationException(errorMessage);
  }

  private static boolean isValidMonthYear(String input) {
    String regex = "^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)-\\d{4}$";
    return input != null && input.matches(regex);
  }

  private void validateOfficeCodeAndAccessPermissions(
      List<String> offices,
      GetBulkSubmission200ResponseDetails bulkSubmissionDetails,
      BulkSubmission.BulkSubmissionBuilder bulkSubmissionBuilder) {
    String officeCode =
        Optional.ofNullable(bulkSubmissionDetails)
            .map(GetBulkSubmission200ResponseDetails::getOffice)
            .map(GetBulkSubmission200ResponseDetailsOffice::getAccount)
            .orElse(null);

    // Validation: check if file's office is in authorised list
    if (officeCode == null || !offices.contains(officeCode)) {
      String errorMessage =
          "User does not have authorisation to submit for office "
              + officeCode
              + ". Please verify your office code and access permissions.";

      BulkSubmission unauthorised =
          bulkSubmissionBuilder
              .status(BulkSubmissionStatus.UNAUTHORISED)
              .errorCode(BulkSubmissionErrorCode.E100)
              .errorDescription(errorMessage)
              .build();

      bulkSubmissionRepository.save(unauthorised);

      throw new BulkSubmissionOfficeAuthorisationException(errorMessage);
    }
  }

  /**
   * Validates the date formats for various date fields within the bulk submission details. This
   * method checks multiple date fields from the bulk submission outcomes to ensure they conform to
   * the required DD/MM/YYYY format and represent valid dates.
   *
   * @param bulkSubmissionDetails the details object containing the submission data to validate
   * @param bulkSubmissionBuilder builder object used to construct the bulk submission response in
   *     case of validation failures
   * @throws BulkSubmissionValidationException if any date field fails validation
   */
  private void validateDateFormats(
      GetBulkSubmission200ResponseDetails bulkSubmissionDetails,
      BulkSubmission.BulkSubmissionBuilder bulkSubmissionBuilder) {

    List<@Valid BulkSubmissionOutcome> bulkSubmissionOutcomes =
        Optional.ofNullable(bulkSubmissionDetails)
            .map(GetBulkSubmission200ResponseDetails::getOutcomes)
            .orElse(Collections.emptyList());

    bulkSubmissionOutcomes.forEach(
        outcome -> {
          try {
            validateDate(outcome.getCaseStartDate(), "Case Start Date");
            validateDate(outcome.getClientDateOfBirth(), "Client Date of Birth");
            validateDate(outcome.getWorkConcludedDate(), "Work Concluded Date");
            validateDate(outcome.getTransferDate(), "Transfer Date");
            validateDate(outcome.getSurgeryDate(), "Surgery Date");
            validateDate(outcome.getRepOrderDate(), "Rep Order Date");
            validateDate(outcome.getClient2DateOfBirth(), "Client 2 Date of Birth");
            validateDate(outcome.getMedConcludedDate(), "Med Concluded Date");
          } catch (Exception e) {
            failSubmission(
                e.getMessage() + " must be a valid date in the format DD/MM/YYYY",
                bulkSubmissionBuilder);
          }
        });
  }

  /**
   * Validates that a given date string matches the required format 'dd/MM/yyyy' and represents a
   * valid date. The method performs both format validation and logical date validation (e.g.,
   * checking for invalid dates like 29/02/2025).
   *
   * @param dateStr the date string to validate, can be null or blank
   * @param fieldName the name of the field being validated, used in error messages
   * @throws IllegalArgumentException if the date string is not in the correct format or represents
   *     an invalid date
   */
  private void validateDate(String dateStr, String fieldName) {
    if (dateStr != null && !dateStr.isBlank()) {
      try {
        // Support both single and double digit formats
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[d/M/yyyy][dd/MM/yyyy]");
        String normalizedDate = dateStr.trim();

        // Parse will validate the date is actually valid (e.g. not 29/02/2025)
        LocalDate date = LocalDate.parse(normalizedDate, formatter);

        // Verify the parsed date matches the input to catch any automatic corrections
        String formattedBackSingleDigitFormat =
            date.format(DateTimeFormatter.ofPattern("d/M/yyyy"));
        String formattedBackDoubleDigitFormat =
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (!normalizedDate.equals(formattedBackSingleDigitFormat)
            && !normalizedDate.equals(formattedBackDoubleDigitFormat)) {
          throw new IllegalArgumentException(fieldName);
        }
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(fieldName);
      }
    }
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

    return bulkSubmissionMapper.toBulkSubmissionDetails(fileSubmission);
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
        .createdByUserId(bulkSubmission.getCreatedByUserId())
        .errorCode(bulkSubmission.getErrorCode())
        .errorDescription(bulkSubmission.getErrorDescription())
        .updatedByUserId(bulkSubmission.getUpdatedByUserId())
        .details(bulkSubmission.getData());
  }

  /**
   * Update a {@link BulkSubmission} entity identified by the id with the details provided in the
   * {@link BulkSubmissionPatch} object.
   *
   * @param id the identifier of the {@code BulkSubmission} entity being updated
   * @param bulkSubmissionPatch the object representing the payload of the details to update on the
   *     bulk submission
   */
  @Transactional
  public void updateBulkSubmission(UUID id, BulkSubmissionPatch bulkSubmissionPatch) {

    int updateCount =
        bulkSubmissionRepository.updateBulkSubmission(
            id,
            Optional.ofNullable(bulkSubmissionPatch.getStatus()).map(Enum::name).orElse(null),
            Optional.ofNullable(bulkSubmissionPatch.getErrorCode()).map(Enum::name).orElse(null),
            bulkSubmissionPatch.getErrorDescription(),
            bulkSubmissionPatch.getUpdatedByUserId());
    if (updateCount == 0) {
      throw new BulkSubmissionNotFoundException("Bulk submission not found with id: " + id);
    }
  }

  private void validateAreaOfLaw(String areaOfLawValue) {
    if (areaOfLawValue == null) {
      throw new BulkSubmissionAreaOfLawException();
    }

    String trimmedValue = areaOfLawValue.trim();
    boolean supported =
        Arrays.stream(AreaOfLaw.values())
            .map(AreaOfLaw::getValue)
            .anyMatch(value -> value.equalsIgnoreCase(trimmedValue));

    if (!supported) {
      throw new BulkSubmissionAreaOfLawException();
    }
  }
}
