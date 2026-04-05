# Changelog

All notable changes to Hubitat Bulk File Manager are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [1.0.3] — 2026-04-04

### Changed
- Updated README import, package-source, and clone URLs to use the correct GitHub username `brianpavane`
- Removed the outdated README note about replacing the username in import URLs

## [1.0.2] — 2026-04-04

### Added
- Explorer-style browser table on the main page with folders and files shown together
- Parent-folder row (`..`) inside the main listing for filesystem-style navigation

### Changed
- Main screen now shows directory contents immediately without requiring search
- Search remains optional and only filters the visible file rows in the current folder
- Spock test suite expanded to 156 tests, 0 failures

## [1.0.1] — 2026-04-04

### Added
- Clickable file-table column headers for **Name**, **Size**, and **Modified**
- Sort-direction indicators on the active column header

### Changed
- Header clicks now update the same persisted sort state used by the existing sort controls
- Spock test suite expanded to 153 tests, 0 failures

## [1.0.0] — 2026-04-04

### Added
- Full file listing via Hubitat's `/hub/fileManager/json` API with name, MIME type, size, and date columns
- Simulated directory navigation — virtual folders inferred from `/` separators in flat filenames
- Breadcrumb trail with HTML-escaped path segments
- Real-time search / filter within the current directory (case-insensitive)
- Column sorting by name, size, or date — ascending or descending — persisted as settings
- Bulk file selection via multi-select `enum` input with running size total in status bar
- **Select All** / **Clear** toolbar buttons
- **Delete** with mandatory per-file confirmation page before execution
- **Copy** — downloads source, re-uploads to destination; basename is preserved
- **Move** — safe-move: sources only deleted after all copies succeed
- **New Folder** — creates `<name>/.keep` placeholder; sanitises folder name to `[a-zA-Z0-9_-]`
- Configurable hub IP address and optional Bearer token for secured hubs
- Configurable max-files-per-page (default 200, range 10–2000)
- Result banner on main page after every operation (success / partial / failure)
- Spock test suite — 150 tests across 18 functional areas, 0 failures

### Security
- HTML-escaped all user-controlled strings (`escapeHtml`) before rendering into raw HTML paragraphs
- Fixes XSS vectors in the delete-confirmation file list and the breadcrumb path segments

### Notes
- This project is a single-file Hubitat Groovy app; there is no `package.json`.  
  Version is tracked in the file header (`Version: 1.0.3`) and in this changelog.
- The `/shannon` security-scan skill is not yet registered in this workspace;  
  a manual code review was performed instead (see Security section above).

[1.0.3]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.3
[1.0.2]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.2
[1.0.1]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.1
[1.0.0]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.0
