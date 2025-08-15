package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import io.micrometer.core.instrument.util.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.springframework.util.ResourceUtils.getFile;

public class ConverterTestUtils {

  public static MultipartFile getMultipartFile(String inputFileName) throws IOException {
    File inputFile = getFile(inputFileName);
    FileInputStream inputStream = new FileInputStream(inputFile);
    return new MockMultipartFile(
        inputFileName, inputFile.getName(), "application/octet-stream", inputStream);
  }

  public static String getContent(File file) {
    String content;
    try (FileInputStream inputStream = new FileInputStream(file)) {
      content = IOUtils.toString(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return content;
  }
}
