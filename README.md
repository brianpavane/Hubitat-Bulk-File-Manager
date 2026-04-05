# Hubitat Bulk File Manager

**Version:** 1.0.8  
**Author:** Brian Pavane  
**Namespace:** `bpavane`  
**Category:** Utility  

A full-featured file manager application for the Hubitat Elevation hub, providing search, bulk selection, copy, move, delete, and folder creation for all files stored on the hub.

---

## Features

| Feature | Description |
|---|---|
| **Finder-style browser** | Flat file listing in a macOS Finder-inspired table with icon, name, type, size, and date |
| **Zero-config install** | Works immediately after adding — no settings required |
| **Search / filter** | Real-time case-insensitive search across all files |
| **Sorting** | Sort directly from the file-manager header bar by name, type, size, or modified date |
| **Inline selection** | Select files directly from the file viewer with per-row checkbox-style toggles |
| **Bulk selection** | Select All / Clear All toolbar buttons |
| **Pagination** | Browse large libraries with previous / next paging controls |
| **Delete** | Mandatory confirmation page with full file list before any deletion |
| **Copy** | Type a destination path or prefix; copies with original basename preserved |
| **Move** | Safe-move: sources are only deleted after all copies succeed |
| **Status bar** | Live count of files, total size, and selected size |
| **Version display** | Current version shown in all page titles and the status bar |
| **Auth support** | Optional hub security token for hubs with login enabled |

---

## Requirements

- Hubitat Elevation hub running firmware **2.3.4 or later**
- Hub security token (optional — only required if hub login is enabled)

---

## Installation

### Option A — Import directly in Hubitat (recommended)

1. In the Hubitat UI, navigate to **Apps Code**.
2. Click **+ New App**.
3. Click **Import** (top-right of the editor).
4. Paste the following URL and click **Import**:

   ```
   https://raw.githubusercontent.com/brianpavane/Hubitat-Bulk-File-Manager/main/HubitatBulkFileManager.groovy
   ```

5. Click **Save** in the code editor.
6. Navigate to **Apps → + Add User App**.
7. Select **Hubitat Bulk File Manager** and click **Done**.

### Option B — Manual paste

1. Open [`HubitatBulkFileManager.groovy`](HubitatBulkFileManager.groovy) and copy all content.
2. In the Hubitat UI, navigate to **Apps Code → + New App**.
3. Paste the code, click **Save**.
4. Navigate to **Apps → + Add User App → Hubitat Bulk File Manager**.

### Option C — Hubitat Package Manager (HPM)

If you manage apps via HPM, add this repository URL when prompted for a custom package source:

```
https://raw.githubusercontent.com/brianpavane/Hubitat-Bulk-File-Manager/main/packageManifest.json
```

---

## Configuration

Open the app in **Apps → Hubitat Bulk File Manager**, then tap **⚙ Settings**.

| Setting | Default | Description |
|---|---|---|
| **Hub IP Address** | Auto-detected | Override if the hub cannot detect its own IP |
| **Hub Security Token** | _(blank)_ | Bearer token for hubs with login enabled (find it in **Settings → Hub Login Security → Access Token**) |
| **Files per page** | `200` | Caps the number of files rendered per page (range: 10–2000) |

---

## Usage Guide

### Searching

Type in the **Search** box and the file list filters in real time. Search matches anywhere in the filename (case-insensitive) across all hub files.

### Selecting files

Use the inline selection control beside each file to toggle it on or off.  
Use **☑ Select All** to select every file visible in the current search/sort view.  
Use **☐ Clear** to deselect everything.  

The **status bar** at the bottom always shows how many files are selected and their total size.

### Sorting and paging

Click the **Name**, **Type**, **Size**, or **Modified** headers in the file manager to sort the visible list. Click the active header again to flip ascending or descending order.

When the library is larger than the current page size, use the **Previous** and **Next** links under the table to move through all files.

### Deleting files

1. Select the files you want to remove.
2. Click **🗑 Delete (N)** in the toolbar.
3. A confirmation page lists every file to be deleted.
4. Click **Yes, Delete All** — or **Cancel** to go back.

> Deletion is **permanent and irreversible**. There is no recycle bin.

### Copying files

1. Select source files.
2. Click **📋 Copy (N)** in the toolbar.
3. Browse to the destination folder using the **📁** links, or type a path directly.
4. Click **✅ Confirm Copy Here**.

### Moving files

Same as Copy, but the source files are removed after all copies succeed.  
If any copy fails, the operation is aborted and **no sources are deleted**.

---

## Architecture Notes

```
Hubitat Hub
└── /local/ (flat file store)
    ├── image.png
    ├── photos/vacation/img1.png   ← "/" treated as path separator
    └── docs/readme.txt

App pages (4)
├── mainPage              — listing, search, sort, toolbar
├── confirmDeletePage     — delete confirmation
├── destinationPickerPage — destination input for copy/move
└── settingsPage          — hub connection config

File API strategy
├── List   → HTTP GET  /hub/fileManager/json
├── Upload → uploadHubFile(name, bytes)          [sandbox built-in]
├── Delete → deleteHubFile(name)                 [sandbox built-in, HTTP fallback]
└── Download → downloadHubFile(name)             [sandbox built-in, HTTP GET fallback]
```

---

## Known Limitations

| Limitation | Explanation |
|---|---|
| No undo | All delete operations are permanent |
| Flat namespace | Hubitat stores all files in one namespace; this app reflects that reality |
| Binary copy | Large binary files (> 10 MB) may timeout during copy/move due to HTTP limits |
| No rename | Rename is not available; use Copy-then-Delete as a workaround |

---

## Development

### Project structure

```
Hubitat-Bulk-File-Manager/
├── HubitatBulkFileManager.groovy   ← single-file Hubitat app (what you import)
├── build.gradle                    ← Gradle build (tests only)
├── settings.gradle
├── README.md
└── src/
    └── test/
        └── groovy/
            ├── support/
            │   └── AppTestHarness.groovy   ← mock Hubitat sandbox
            └── HubitatBulkFileManagerSpec.groovy  ← Spock test suite
```

### Running the tests

**Prerequisites:** Java 11+ and Gradle 7+

```bash
# Clone the repository
git clone https://github.com/brianpavane/Hubitat-Bulk-File-Manager.git
cd Hubitat-Bulk-File-Manager

# Run the full test suite
gradle test

# Run with detailed output
gradle test --info

# Run a single test class
gradle test --tests "HubitatBulkFileManagerSpec"
```

Test results are written to `build/reports/tests/test/index.html`.

### Test coverage

The test suite (`HubitatBulkFileManagerSpec.groovy`) covers:

| Group | Methods tested | Test cases |
|---|---|---|
| Size formatting | `formatSize` | 11 |
| Date formatting | `formatDate` | 9 |
| MIME detection | `getMimeType` | 25 |
| File icons | `getFileIcon` | 17 |
| HTML escaping | `escapeHtml` | 7 |
| File filtering/sorting | `filterFiles` | 10 |
| Pagination | `paginateFiles`, `buildPaginationBar` | 3 |
| Selection size | `computeSelectedSize` | 5 |
| Finder table | `buildFinderTable` | 11 |
| Selection options | `buildSelectionOptions` | 4 |
| File listing API | `getFileList` | 6 |
| Delete operation | `performDelete` | 6 |
| Copy operation | `performCopy` | 6 |
| Move operation | `performMove` | 3 |
| Button handlers | `appButtonHandler` | 7 |
| Hub URL/auth | `getHubBaseUrl`, `makeAuthHeaders` | 6 |
| **Total** | | **148 tests, 0 failures** |

---

## Version History

| Version | Date | Notes |
|---|---|---|
| 1.0.8 | 2026-04-04 | Replaced non-working inline HTML sort and selection links with Hubitat-native file-manager controls |
| 1.0.7 | 2026-04-04 | Formatted numeric modified timestamps as dates, added previous/next paging, and kept sorting controls in the file manager view |
| 1.0.6 | 2026-04-04 | Added inline file selection checkboxes and clickable file-viewer header sorting |
| 1.0.5 | 2026-04-04 | Added dual-port file-manager probing and clearer file-list diagnostics |
| 1.0.4 | 2026-04-04 | Finder-style flat listing, version display in all pages, zero-config install, removed virtual-directory code |
| 1.0.3 | 2026-04-04 | Updated README GitHub URLs to use the correct `brianpavane` username |
| 1.0.2 | 2026-04-04 | Refactored main view into an Explorer-style browser table with folders and files shown together |
| 1.0.1 | 2026-04-04 | Added clickable file-table header sorting for Name, Size, and Modified columns |
| 1.0.0 | 2026-04-04 | Initial release |

---

## License

MIT License — see [LICENSE](LICENSE) for details.
