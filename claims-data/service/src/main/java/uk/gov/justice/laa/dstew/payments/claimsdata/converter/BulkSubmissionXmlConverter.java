package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

import java.io.IOException;

/** Converter responsible for converting bulk submissions in XML format. */
@Slf4j
@Component
public class BulkSubmissionXmlConverter implements BulkSubmissionConverter {

  private final XmlMapper xmlMapper;

  @Autowired
  public BulkSubmissionXmlConverter(XmlMapper xmlMapper) {
    this.xmlMapper = xmlMapper;
  }

  /**
   * Converts the given file to a {@link XmlSubmission} object.
   *
   * @param file the input file
   * @return the {@link XmlSubmission} object.
   */
  @Override
  public XmlSubmission convert(MultipartFile file) {
    XmlSubmission submission;

    try {
      submission = xmlMapper.readValue(file.getInputStream(), XmlSubmission.class);
    } catch (IOException e) {
      throw new BulkSubmissionFileReadException("Failed to read xml bulk claim file", e);
    }

    return submission;
  }

  /**
   * Determines whether this converter handles the given {@link FileExtension}.
   *
   * @param fileExtension the file extension to check
   * @return true if the converter can handle files with the given extension, false otherwise
   */
  @Override
  public boolean handles(FileExtension fileExtension) {
    return FileExtension.XML.equals(fileExtension);
  }
}
