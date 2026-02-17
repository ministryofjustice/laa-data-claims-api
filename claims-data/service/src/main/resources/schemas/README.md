# XML Validation Guide

Before uploading files into the service, you can validate them locally using standard tools.  
This helps identify structural or formatting issues early and avoids rejected uploads.

---

## Using `xmllint` 

`xmllint` is available on Linux, macOS and WSL.

### Install

#### Ubuntu / Debian
```bash
sudo apt-get install libxml2-utils
```
#### macOS (Homebrew)
```
brew install libxml2
```
#### Validate an XML file
```
xmllint --noout --schema schemas/LSCSMSBulkLoadSchemaV3.xsd my-file.xml
```
#### Example output

##### Valid XML 
```
my-file.xml validates
```
##### Invalid XML 
```
missing_office.xml:4: element submission: Schemas validity error : 
Element 'submission': Missing child element(s). Expected is ( office ).
missing_office.xml fails to validate
```