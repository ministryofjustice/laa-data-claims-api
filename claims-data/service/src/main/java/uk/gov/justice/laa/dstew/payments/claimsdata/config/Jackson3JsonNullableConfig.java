package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 support for the OpenAPI {@link JsonNullable} wrapper.
 *
 * <p>Spring Boot 4 uses Jackson 3 ({@code tools.jackson}) for the web layer, whereas {@code
 * org.openapitools:jackson-databind-nullable} only ships a Jackson 2 module. Without this, request
 * bodies that model tri-state PATCH fields as {@code JsonNullable<T>} (notably {@code
 * ClaimAmendmentPatch} on {@code PATCH /submissions/{id}/claims/{id}}) fail to deserialize with
 * "Cannot construct instance of JsonNullable".
 *
 * <p>This registers a Jackson 3 module that maps JSON to {@link JsonNullable} while preserving the
 * PATCH tri-state:
 *
 * <ul>
 *   <li>field omitted &rarr; the model keeps its {@code JsonNullable.undefined()} default (the
 *       deserializer is never invoked);
 *   <li>field present as {@code null} &rarr; {@code JsonNullable.of(null)} (an explicit clear);
 *   <li>field present with a value &rarr; {@code JsonNullable.of(value)}.
 * </ul>
 */
// @Configuration
public class Jackson3JsonNullableConfig {

  /**
   * Registers the {@link JsonNullable} deserializer with the auto-configured Jackson 3 mapper used
   * by Spring MVC.
   *
   * @return the Jackson 3 module
   */
  @Bean
  public SimpleModule jsonNullableJackson3Module() {
    SimpleModule module = new SimpleModule("JsonNullableJackson3Module");
    module.addDeserializer(JsonNullable.class, new JsonNullableDeserializer(null));
    return module;
  }

  /** Contextual deserializer that unwraps to the declared {@code JsonNullable<T>} content type. */
  static final class JsonNullableDeserializer extends ValueDeserializer<JsonNullable<?>> {

    private final JavaType contentType;

    JsonNullableDeserializer(JavaType contentType) {
      this.contentType = contentType;
    }

    @Override
    public ValueDeserializer<?> createContextual(
        DeserializationContext ctxt, BeanProperty property) {
      if (property != null) {
        JavaType resolved = property.getType().containedTypeOrUnknown(0);
        return new JsonNullableDeserializer(resolved);
      }
      return this;
    }

    @Override
    public JsonNullable<?> deserialize(JsonParser parser, DeserializationContext ctxt) {
      JavaType type = contentType != null ? contentType : ctxt.constructType(Object.class);
      Object value = ctxt.readValue(parser, type);
      return JsonNullable.of(value);
    }

    /** An explicit JSON {@code null} is a requested clear, preserved as a present null. */
    @Override
    public JsonNullable<?> getNullValue(DeserializationContext ctxt) {
      return JsonNullable.of(null);
    }
  }
}
