package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/** Interface for bulk submission file converters. */
public interface BulkSubmissionConverter {
  FileSubmission convert(MultipartFile file);

  boolean handles(FileExtension fileExtension);
}
