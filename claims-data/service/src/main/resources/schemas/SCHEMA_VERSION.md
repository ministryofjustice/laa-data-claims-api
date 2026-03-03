# XML Schema Version Guide

This document tracks changes to the LSCSMS Bulk Load XML Schema and provides
guidance to providers on version compatibility.

Providers must ensure their XML files conform to the latest supported schema
version listed below.

---

## Current Supported Version

| Version | Status | XSD File | Release Date |
|----------|--------|----------|--------------|
| v3 | ✅ Current | LSCSMSBulkLoadSchemaV3.xsd | 2026-02-18   |

Only the **Current** version is accepted by the service unless explicitly stated.

---

## Version History

### v3 – 2026-02-18 (Current)

**Summary**
- Defines the LAA Data Claims bulk load XML submission structure.

- The schema validates the structural format:
  submission → office → schedule → (outcome | newMatterStarts | immigrationCLR)
- Business rules (e.g. date logic) are enforced by the application.


The schema enforces:

- Required attributes:
    - `office@account` (6 character alphanumeric)
    - `schedule@areaOfLaw`
    - `schedule@submissionPeriod` (e.g. JAN-2025)
    - `schedule@scheduleNum`

- Controlled enumerations for:
    - `areaOfLaw` (MEDIATION, CRIME LOWER, LEGAL HELP)
    - Outcome item names
    - Matter start codes

- Structural rules:
    - A submission contains a single office
    - An office contains a schedule
    - A schedule may contain:
        - Multiple `outcome` entries
        - Multiple `newMatterStarts`
        - Multiple `immigrationCLR` entries

- Format constraints via patterns:
    - Office account number: `[A-Z0-9]{6}`
    - Submission period: `MMM-YYYY`
    - Schedule number: alphanumeric + `/`
    - Upper snake case attributes
    - Non-blank string enforcement

Business validation rules (e.g. cross-field validation, date logic, financial calculations)
are enforced by the application layer and not by the schema.

**Breaking Changes**
- N/A - initial version

**Migration Notes**
- Ensure XML root element is `submission`
- Ensure namespace matches:
  `http://www.legalservices.gov.uk/sms/ActivityManagement/XMLSchema/`
- Validate locally before submission using the current schema version

---

## Versioning Strategy

Schema versioning follows a structured and predictable approach to ensure
backward compatibility, provider stability, and clear governance.

### Version Format

The schema uses **semantic versioning** internally:
`MAJOR.MINOR`

Example:
- `3.0` – Initial v3 release
- `3.1` – Backward-compatible enhancement
- `4.0` – Breaking change

File names will continue to use the major version:

- `LSCSMSBulkLoadSchemaV3.xsd`
- `LSCSMSBulkLoadSchemaV4.xsd`

The internal schema attribute will reflect the technical version:

```xml
<xs:schema version="3.0">
```

---

## Provider Guidance

Before submitting files:

1. Download the latest supported schema
2. Validate locally using:

```bash
xmllint --noout --schema LSCSMSBulkLoadSchemaV3.xsd your-file.xml


