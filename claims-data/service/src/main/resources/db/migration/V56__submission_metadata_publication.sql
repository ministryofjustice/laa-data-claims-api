ALTER PUBLICATION claims_reporting_service_pub
    ADD TABLE claims.submission_metadata;

ALTER PUBLICATION claims_data_factory_service_pub
    ADD TABLE claims.submission_metadata;
