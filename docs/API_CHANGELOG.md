# API Changelog

All notable changes to the LAA Data Claims API will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.1] - 2024-11-25

### Added

#### Matter Starts API Group
Operations for managing matter start records within submissions.

- **GET /api/v0/submissions/{id}/matter-starts** - List all matter starts for submission
  - Returns all matter start records for specified submission

- **POST /api/v0/submissions/{id}/matter-starts** - Create a matter start
  - Adds matter start record to specified submission
  - Returns Location header with URI of created resource
  
- **GET /api/v0/submissions/{id}/matter-starts/{matter-start-id}** - Get specific matter start
  - Returns details of specific matter start record within a submission

#### Validation Messages API Group
Operations for retrieving validation errors and warnings.

- **GET /api/v0/validation-messages** - Get validation messages
  - Paginated results with filtering by:
    - `submission-id` (required) - Submission identifier
    - `claim-id` (optional) - Claim identifier
    - `type` (optional) - Validation message type
    - `source` (optional) - Origin of validation message
  - Returns total count of unique claims with validation errors

#### Bulk Submissions API Group
Operations for uploading and managing bulk submission files (CSV/XML format).

- **POST /api/v0/bulk-submissions** - Upload a submission file
  - Accepts multipart/form-data with CSV or XML files
  - Validates file structure and stores as JSON in bulk_submission table
  - Returns bulk submission ID and list of submission IDs
  - Query parameters: `userId` (required), `offices` (required)
  
- **GET /api/v0/bulk-submissions/{id}** - Retrieve bulk submission details
  - Returns raw JSON document with bulk submission metadata
  - Includes status, office details, schedule information, outcomes, matter starts, and immigration CLR data
  
- **PATCH /api/v0/bulk-submissions/{id}** - Update bulk submission status
  - Updates status field of existing bulk submission
  - Supports status transitions: READY_FOR_PARSING, PARSING_COMPLETED, PARSING_FAILED, VALIDATION_FAILED, REPLACED, UNAUTHORISED, VALIDATION_SUCCEEDED

#### Submissions API Group
Operations for creating and managing individual claim submissions.
  
- **GET /api/v0/submissions** - List submissions with filtering
  - Paginated results with filtering by:
    - `offices` (required) - List of office account numbers
    - `submission_id` (optional)
    - `submitted_date_from` (optional)
    - `submitted_date_to` (optional)
    - `area_of_law` (optional) - CRIME LOWER, LEGAL HELP, MEDIATION
    - `submission_period` (optional)
    - `submission_statuses` (optional)

- **POST /api/v0/submissions** - Create a new submission
  - Creates submission record associated with a bulk submission
  - Required fields: submission_id, bulk_submission_id, office_account_number, submission_period, area_of_law, status, provider_user_id, created_by_user_id
  
- **GET /api/v0/submissions/{id}** - Get submission summary by ID
  - Returns submission details with associated claims (ID and status only) and matter start IDs
  
- **PATCH /api/v0/submissions/{id}** - Update submission status
  - Updates status field of existing submission
  - Supports status transitions: CREATED, READY_FOR_VALIDATION, VALIDATION_IN_PROGRESS, VALIDATION_SUCCEEDED, VALIDATION_FAILED, REPLACED

#### Claims API Group
Operations for managing individual claims within submissions.

- **POST /api/v0/submissions/{id}/claims** - Add a claim to a submission
  - Creates claim record associated with specified submission
  - Required fields: status, line_number, matter_type_code, created_by_user_id
  
- **GET /api/v0/submissions/{submission-id}/claims/{claim-id}** - Get specific claim details
  - Returns complete claim details for a specific claim within a submission
  
- **PATCH /api/v0/submissions/{submission-id}/claims/{claim-id}** - Update claim fields
  - Partial update of claim fields
  - Only provided fields are updated
  
- **GET /api/v0/claims** - Search and filter claims
  - Paginated results with filtering by:
    - `office_code` (required) - Provider office account number
    - `submission_id` (optional)
    - `submission_statuses` (optional)
    - `fee_code` (optional) - Fee type/category code
    - `unique_file_number` (optional) - Provider's case reference
    - `unique_client_number` (optional) - Client identifier
    - `unique_case_id` (optional) - Case identifier
    - `claim_statuses` (optional) - READY_TO_PROCESS, VALID, INVALID

### Security

- API key authentication for all endpoints
- Rate limiting implemented using Resilience4j:
  - Per-API-key rate limiting with different limits per service type
  - Endpoint-specific limits for bulk operations, event services, and default operations
  - Environment-specific limits (UAT, Staging, Production)
  - 429 Too Many Requests response with Retry-After header

### Technical Details

- Base path: `/api/v0`
- Content-Type: `application/json` (except bulk submissions which accept `multipart/form-data`)
- All IDs use UUID format
- Pagination support for list endpoints
- Standard HTTP status codes:
  - 200 OK - Successful GET requests
  - 201 Created - Successful POST requests with Location header
  - 204 No Content - Successful PATCH requests
  - 400 Bad Request - Invalid request parameters
  - 401 Unauthorized - Missing or invalid authentication
  - 403 Forbidden - Insufficient permissions
  - 404 Not Found - Resource not found
  - 415 Unsupported Media Type - Invalid content type
  - 429 Too Many Requests - Rate limit exceeded
  - 500 Internal Server Error - Server error

## API Versioning

This API uses URL path versioning (e.g., `/api/v0`). The current version is `v0`, indicating the API is in initial development.

## Breaking Changes

As this is version 0.0.1, no breaking changes have been introduced yet. Future breaking changes will be documented here with migration guidance.

## Deprecation Notices

No deprecations at this time.

---

For detailed API specifications, see the [OpenAPI specification](claims-data/api/open-api-specification.yml).

For authentication details, see [ADR-0001: API Key Authentication](docs/architecture/decisions/ADR-0001-api-key-authentication.md).

For rate limiting details, see [Rate Limiting Documentation](docs/RATE_LIMITING_TESTING.md).
