package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;

/**
 * A custom Jackson {@link com.fasterxml.jackson.databind.JsonDeserializer} that trims leading and
 * trailing whitespace from all deserialized {@link String} values.
 *
 * <p>This deserializer ensures that any {@code String} content coming from JSON or XML input is
 * cleaned before being bound to application objects. It is particularly useful when processing XML
 * attributes or elements where user‑provided data may contain surrounding whitespace.
 */
public class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

  public TrimmingStringDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser p, DeserializationContext context) throws IOException {
    String value = p.getValueAsString();
    return value == null ? null : value.trim();
  }
}
