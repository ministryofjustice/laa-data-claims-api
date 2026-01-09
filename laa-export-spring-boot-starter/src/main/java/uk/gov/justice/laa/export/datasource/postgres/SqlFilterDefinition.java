package uk.gov.justice.laa.export.datasource.postgres;

public record SqlFilterDefinition(
    String column,
    Class<?> type
) {}
