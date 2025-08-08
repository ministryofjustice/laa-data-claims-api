package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverterFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/** Service responsible for handling the processing of bulk submission objects. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkSubmissionService {

    private final BulkSubmissionConverterFactory bulkSubmissionConverterFactory;
    private final BulkSubmissionMapper submissionMapper;
    private final BulkSubmissionRepository bulkSubmissionRepository;

    /**
     * Converts the provided file to a Java object based on the filename extension, then maps it to
     * and returns a {@link BulkSubmissionDetails}.
     *
     * @param file the file to convert.
     * @return a {@link BulkSubmissionDetails} representing the provided input file.
     */
    public BulkSubmissionDetails getBulkSubmissionDetails(MultipartFile file) {
        FileSubmission submission = convert(file);

        return submissionMapper.toBulkSubmissionDetails(submission);
    }

    /**
     * Processes a bulk submission from the provided multipart file and returns a response with
     * a submission ID.
     *
     * @param file the multipart file containing bulk claim data; must not be null.
     * @return a {@link CreateBulkSubmission201Response} object containing the ID of the bulk submission and the
     * list of submitted ids (NOTE: we should only get one submission within the bulk).
     */
    public CreateBulkSubmission201Response submitBulkSubmissionFile(@NotNull String userId, @NotNull MultipartFile file) {

        BulkSubmissionDetails bulkSubmissionDetails = getBulkSubmissionDetails(file);

        BulkSubmission bulkSubmission = new BulkSubmission();
        bulkSubmission.setStatus(BulkSubmissionStatus.READY_FOR_PARSING);
        bulkSubmission.setData(bulkSubmissionDetails);
        bulkSubmission.setCreatedOn(Instant.now());
        bulkSubmission.setCreatedByUserId(userId);

        bulkSubmissionRepository.save(bulkSubmission);

        return new CreateBulkSubmission201Response()
                .bulkSubmissionId(bulkSubmission.getId())
                .submissionIds(Collections.singletonList(UUID.randomUUID()));
    }

    private FileSubmission convert(MultipartFile file) {
        FileExtension fileExtension = getFileExtension(file);

        return bulkSubmissionConverterFactory.converterFor(fileExtension).convert(file);
    }

    private FileExtension getFileExtension(MultipartFile file) {
        String filename =
                !StringUtils.hasText(file.getOriginalFilename())
                        ? file.getName()
                        : file.getOriginalFilename();
        try {
            int index = filename.lastIndexOf('.');
            return FileExtension.valueOf(filename.substring(index + 1).toUpperCase());
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new BulkSubmissionFileReadException(
                    "Unable to retrieve file extension from filename: %s".formatted(filename), e);
        }
    }
}
