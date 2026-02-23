package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

public class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

  public TrimmingStringDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser p, DeserializationContext context)
      throws IOException {
    String value = p.getValueAsString();
    return value == null ? null : value.trim();
  }
}
