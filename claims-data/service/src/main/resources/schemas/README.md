# XML Validation Guide for Vendors

Before uploading XML files to the Submit a Bulk Claim (SaBC) service, you can validate them locally or directly within your XML‑generation tool using the supplied XSD schema.
Validating your XML in advance helps you identify structural or formatting issues early, reducing the chance of rejected submissions.

## 📥 Feedback

If you use this XSD file, please tell us whether it is useful and share any feedback in our survey:

[Give feedback on the XSD file](https://www.smartsurvey.co.uk/s/SaBC_XSD_github_feedback/)

## ⭐ Why Validate?

- Detect issues before submitting to the service
- Avoid repeated failed uploads
- Ensure XML files conform to **LSCSMS Bulk Load Schema v3**
- Reduce support delays

## 🛠 Tools

The simplest validation tool is **xmllint**, available on:

- Linux
- macOS
- Windows (via WSL)

## 📦 Install xmllint

### Ubuntu / Debian
```bash
sudo apt-get install libxml2-utils
```

### macOS (Homebrew)
```bash
brew install libxml2
```

### Windows (WSL)
Install WSL (Ubuntu recommended), then use the Linux command above.

## ✅ Validate Your XML File

Run the following command:
```bash
xmllint --noout --schema schemas/LSCSMSBulkLoadSchemaV3.xsd my-file.xml
```

- `--noout` suppresses normal output
- `--schema` tells xmllint which XSD to validate against

## 📥 Example Outputs

### Valid XML
```
my-file.xml validates
```

### Invalid XML
```
missing_office.xml:4: element submission: Schemas validity error :
Element 'submission': Missing child element(s). Expected is ( office ).
missing_office.xml fails to validate
```

## 💡 Tips for Vendors

- Always validate before uploading
- Fix errors based on line numbers shown by xmllint
- Save files using UTF‑8 encoding
- XML element names are case‑sensitive

## 📁 Recommended Directory Structure

```
project/
  ├── schemas/
  │     └── LSCSMSBulkLoadSchemaV3.xsd
  └── xml/
        └── my-file.xml
```

Validate with:
```bash
xmllint --noout --schema schemas/LSCSMSBulkLoadSchemaV3.xsd xml/my-file.xml
```