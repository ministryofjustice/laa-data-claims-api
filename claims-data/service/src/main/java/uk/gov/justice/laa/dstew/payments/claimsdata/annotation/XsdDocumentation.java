package uk.gov.justice.laa.dstew.payments.claimsdata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for documenting XML fields and types in the generated XSD schema. Used by the
 * generateXsd Gradle task to include descriptions and metadata.
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface XsdDocumentation {
  /**
   * Human-readable description for this element in the XSD documentation.
   *
   * @return the description text
   */
  String description() default "";

  /**
   * Whether this field is required. Maps to minOccurs/maxOccurs in XSD.
   *
   * @return true if field is required, false if optional
   */
  boolean required() default false;
}
