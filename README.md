# Hubitat Bulk File Manager

**Version:** 1.0.2  
**Author:** Brian Pavane  
**Namespace:** `bpavane`  
**Category:** Utility  

A full-featured file manager application for the Hubitat Elevation hub, providing search, bulk selection, copy, move, delete, and folder creation for all files stored on the hub.

---

## Features

| Feature | Description |
|---|---|
| **Directory navigation** | Simulated folder tree inferred from `/`-separated filenames with breadcrumb trail |
| **Explorer-style browser** | Main screen shows folders and files together in a Finder/Explorer-like table |
| **Search / filter** | Real-time case-insensitive search within the current directory |
| **Sorting** | Sort by name, size, or date — ascending or descending |
| **Header sorting** | Click Name, Size, or Modified column headers to toggle sort direction |
| **Individual selection** | Select single files via the multi-select input |
| **Bulk selection** | Select All / Clear All toolbar buttons |
| **Delete** | Mandatory confirmation page with full file list before any deletion |
| **Copy** | Browse or type a destination path; copies with original basename preserved |
| **Move** | Safe-move: sources are only deleted after all copies succeed |
| **New Folder** | Creates a virtual folder using a `.keep` placeholder file |
| **Status bar** | Live count of folders, files, total size, and selected size |
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
   https://raw.githubusercontent.com/bpavane/Hubitat-Bulk-File-Manager/main/HubitatBulkFileManager.groovy
   ```

5. Click **Save** in the code editor.
6. Navigate to **Apps → + Add User App**.
7. Select **Hubitat Bulk File Manager** and click **Done**.

> **Note:** If your GitHub username differs from `bpavane`, replace it in the URL above before importing.

### Option B — Manual paste

1. Open [`HubitatBulkFileManager.groovy`](HubitatBulkFileManager.groovy) and copy all content.
2. In the Hubitat UI, navigate to **Apps Code → + New App**.
3. Paste the code, click **Save**.
4. Navigate to **Apps → + Add User App → Hubitat Bulk File Manager**.

### Option C — Hubitat Package Manager (HPM)

If you manage apps via HPM, add this repository URL when prompted for a custom package source:

```
https://raw.githubusercontent.com/bpavane/Hubitat-Bulk-File-Manager/main/packageManifest.json
```

---

## Configuration

Open the app in **Apps → Hubitat Bulk File Manager**, then tap **⚙ Settings**.

| Setting | Default | Description |
|---|---|---|
| **Hub IP Address** | Auto-detected | Override if the hub cannot detect its own IP |
| **Hub Security Token** | _(blank)_ | Bearer token for hubs with login enabled (find it in **Settings → Hub Login Security → Access Token**) |
| **Max files per directory** | `200` | Caps the number of files rendered per page (range: 10–2000) |

---

## Usage Guide

### Navigating directories

The hub file system is **flat** — all files live in `/local/`. The app treats `/` characters in filenames as path separators to simulate a folder hierarchy.

- The **breadcrumb** at the top shows your current location.
- Click any **📁 folder link** to descend into it.
- Click **📁 ..** to go up one level.

### Searching

Type in the **Search** box and the file list filters in real time. Search matches anywhere in the filename (case-insensitive) within the current directory only.

### Selecting files

Use the **Select Items** dropdown to choose individual files.  
Use **☑ Select All** to select every file visible in the current search/sort view.  
Use **☐ Clear** to deselect everything.  

The **status bar** at the bottom always shows how many files are selected and their total size.

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

### Creating a new folder

1. Navigate to the parent location.
2. Click **📁 New Folder** in the toolbar.
3. Enter a folder name (letters, numbers, hyphens, underscores).
4. Click **📁 Create Folder**.

> Folders are implemented as a hidden `.keep` file (`foldername/.keep`).  
> A folder appears to be empty once all files inside it are deleted (the `.keep` file remains).

---

## Architecture Notes

```
Hubitat Hub
└── /local/ (flat file store)
    ├── image.png
    ├── photos/vacation/img1.png   ← "/" treated as path separator
    └── docs/readme.txt

App pages (5)
├── mainPage              — listing, search, sort, toolbar
├── confirmDeletePage     — delete confirmation
├── destinationPickerPage — folder browser for copy/move
├── newFolderPage         — folder creation
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
| Flat namespace | The hub stores all files in one namespace; directories are simulated |
| No recursive search | Search applies to the current directory level only |
| `.keep` files visible | Folder placeholders appear in listings; do not delete them if you want to preserve the folder |
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
git clone https://github.com/bpavane/Hubitat-Bulk-File-Manager.git
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
| Date formatting | `formatDate` | 7 |
| MIME detection | `getMimeType` | 25 |
| File icons | `getFileIcon` | 17 |
| HTML escaping | `escapeHtml` | 7 |
| Path utilities | `parentPath` | 7 |
| Directory inference | `inferDirectories` | 7 |
| File filtering/sorting | `filterAndSortFiles` | 9 |
| Selection size | `computeSelectedSize` | 5 |
| Breadcrumb HTML | `buildBreadcrumb` | 4 |
| Selection options | `buildSelectionOptions` | 4 |
| File listing API | `getFileList` | 6 |
| Delete operation | `performDelete` | 6 |
| Copy operation | `performCopy` | 6 |
| Move operation | `performMove` | 3 |
| Create folder | `createFolder` | 6 |
| Button handlers | `appButtonHandler` | 9 |
| Hub URL/auth | `getHubBaseUrl`, `makeAuthHeaders` | 6 |
| **Total** | | **~145 assertions** |

---

## Version History

| Version | Date | Notes |
|---|---|---|
| 1.0.2 | 2026-04-04 | Refactored main view into an Explorer-style browser table with folders and files shown together |
| 1.0.1 | 2026-04-04 | Added clickable file-table header sorting for Name, Size, and Modified columns |
| 1.0.0 | 2026-04-04 | Initial release |

---

## License

MIT License — see [LICENSE](LICENSE) for details.
