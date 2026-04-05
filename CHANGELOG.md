# Changelog

All notable changes to Hubitat Bulk File Manager are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Versioning follows [Semantic Versioning](https://semver.org/).

---

## [1.0.9] — 2026-04-04

### Added
- Hubitat-hosted web file manager served from a new `/ui` app endpoint
- Local JSON API endpoints for listing files and performing delete, copy, and move operations
- Launch link from the standard Hubitat app page into the on-hub web UI

### Changed
- Spock test suite expanded to 154 tests, 0 failures
- Test harness now supports endpoint rendering and JSON request bodies for web UI coverage

## [1.0.8] — 2026-04-04

### Changed
- Replaced non-working inline HTML sort links with Hubitat-native `Sort By` and `Direction` controls in the File Manager section
- Replaced inline checkbox-style file toggles with a Hubitat-native multi-select field for files on the current page
- Added a note in the File Manager view clarifying that Hubitat redraws dynamic pages after sort and selection changes
- Spock test suite remains at 148 tests, 0 failures

## [1.0.7] — 2026-04-04

### Changed
- Numeric `Modified` timestamps from Hubitat are now formatted into readable dates in the file viewer
- Large file libraries now page through the full result set with previous and next navigation
- Top-level search controls were simplified so sorting lives in the file-manager header row instead of the search form
- Spock test suite updated to 148 tests, 0 failures

## [1.0.6] — 2026-04-04

### Changed
- Added inline checkbox-style file selection directly in the file viewer
- Removed the need to use the separate bottom selector for one-off file selection
- File viewer column headers now support clickable sorting for `Name`, `Type`, `Size`, and `Modified`
- Spock test suite updated to 145 tests, 0 failures

## [1.0.5] — 2026-04-04

### Changed
- File listing now probes Hubitat File Manager on both port `8080` and port `80`
- Accepted file-manager response shapes expanded to support `files`, `fileList`, `name`, `fileName`, `date`, and `lastModified`
- Main page now shows a diagnostic banner confirming which file-manager endpoint worked or why listing failed
- Spock test suite updated to 138 tests, 0 failures

## [1.0.4] — 2026-04-04

### Added
- Finder-style flat file browser table on the main page (macOS `-apple-system` font, alternating rows, hover highlight, sort indicators)
- Version number displayed in all page titles and the status bar

### Changed
- Main page now zero-configuration: `install: true`, all inputs `required: false`, hub connection errors handled gracefully
- `filterFiles` replaces `filterAndSortFiles` — no `currentPath` parameter; all files treated as peers
- `buildSelectionOptions` simplified — no path filtering, keys are full flat filenames
- Spock test suite updated to 130 tests across 16 functional areas, 0 failures

### Removed
- All virtual-directory / subfolder simulation: `inferDirectories`, `parentPath`, `buildBreadcrumb`, `urlEncode`, `state.currentPath`
- `newFolderPage`, `createFolder`, `btnNewFolder`, `btnCreateFolder`
- Explorer-style `buildFileBrowserTable` and `buildSortHeaderLink` (replaced by `buildFinderTable`)

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
  Version is tracked in the file header (`Version: 1.0.9`) and in this changelog.
- The `/shannon` security-scan skill is not yet registered in this workspace;  
  a manual code review was performed instead (see Security section above).

[1.0.9]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.9
[1.0.8]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.8
[1.0.7]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.7
[1.0.6]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.6
[1.0.5]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.5
[1.0.4]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.4
[1.0.3]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.3
[1.0.2]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.2
[1.0.1]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.1
[1.0.0]: https://github.com/brianpavane/Hubitat-Bulk-File-Manager/releases/tag/v1.0.0
