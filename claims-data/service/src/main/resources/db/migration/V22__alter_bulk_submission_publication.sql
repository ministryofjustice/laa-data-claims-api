ALTER PUBLICATION claims_reporting_service_pub
    DROP TABLE claims.bulk_submission;

ALTER PUBLICATION claims_reporting_service_pub
    ADD TABLE claims.bulk_submission (
        id,
        status,
        error_code,
        error_description,
        created_by_user_id,
        created_on,
        updated_by_user_id,
        updated_on
    );