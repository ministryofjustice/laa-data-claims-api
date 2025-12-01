package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/** Interface for bulk submission file converters. */
public interface BulkSubmissionConverter {

  String MATTER_START_NODE_MISSING_ERROR = "MatterStart node not found in XML.";
  String MATTER_START_MISSING_CODE_ATTRIBUTE_ERROR = "Matter start node is missing code attribute.";
  String IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_ERROR =
      "immClrData node is missing code attribute.";
  String UNSUPPORTED_CATEGORY_CODE_MEDIATION_TYPE_ERROR =
      "Unsupported matter start category code/mediation type: '%s'";
  String MATTER_START_ERROR_MESSAGE_TEMPLATE =
      "Error processing matter start item with code '%s' and value '%s': %s";

  FileSubmission convert(MultipartFile file);

  boolean handles(FileExtension fileExtension);
}
