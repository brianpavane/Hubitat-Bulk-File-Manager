/**
 *  Hubitat Bulk File Manager
 *  Version: 1.0.2
 *  Author:  Brian Pavane
 *
 *  Features:
 *    - Simulated directory navigation with breadcrumb trail
 *    - Real-time search / filter within the current directory
 *    - Sort by name, size, or date (ascending / descending)
 *    - Bulk and individual file selection (with running size total)
 *    - Delete with mandatory confirmation step
 *    - Copy and Move to a browsable destination path
 *    - Create new folder (stored as a .keep placeholder file)
 *    - Configurable hub IP and optional security token
 *
 *  Architecture notes:
 *    - All file listing is retrieved via HTTP GET /hub/fileManager/json.
 *    - Uploads use the built-in uploadHubFile(name, bytes) sandbox call.
 *    - Downloads try downloadHubFile(name) first, falling back to
 *      HTTP GET /local/<name> for broader compatibility.
 *    - Deletes try the built-in deleteHubFile(name) first, falling back
 *      to HTTP POST /hub/fileManager/delete.
 *    - "Directories" are virtual: they are inferred from '/' separators
 *      embedded in flat filenames.  A new folder is created by uploading
 *      a zero-byte <folder>/.keep placeholder.
 */

// ════════════════════════════════════════════════════════════════
//  DEFINITION
// ════════════════════════════════════════════════════════════════

definition(
    name          : "Hubitat Bulk File Manager",
    namespace     : "bpavane",
    author        : "Brian Pavane",
    description   : "Full-featured bulk file manager for Hubitat hub files",
    category      : "Utility",
    iconUrl       : "",
    iconX2Url     : "",
    singleInstance: true
)

// ════════════════════════════════════════════════════════════════
//  PAGES
// ════════════════════════════════════════════════════════════════

preferences {
    page(name: "mainPage",              content: "mainPage")
    page(name: "confirmDeletePage",     content: "confirmDeletePage")
    page(name: "destinationPickerPage", content: "destinationPickerPage")
    page(name: "newFolderPage",         content: "newFolderPage")
    page(name: "settingsPage",          content: "settingsPage")
}

// ────────────────────────────────────────────────────────────────
//  mainPage
// ────────────────────────────────────────────────────────────────

def mainPage() {
    // Apply navigation params supplied by href links
    if (params?.path != null) state.currentPath = params.path
    if (params?.sortField != null) {
        def requestedSortField = params.sortField.toString()
        app.updateSetting("sortField", [type: "enum", value: requestedSortField])
        settings.sortField = requestedSortField
    }
    if (params?.sortDir != null) {
        def requestedSortDir = params.sortDir.toString()
        app.updateSetting("sortDir", [type: "enum", value: requestedSortDir])
        settings.sortDir = requestedSortDir
    }

    def allFiles    = getFileList()
    def currentPath = (state.currentPath ?: "").toString()
    def sortField   = (settings.sortField  ?: "name").toString()
    def sortDir     = (settings.sortDir    ?: "asc").toString()
    def searchText  = (settings.searchText ?: "").toString()

    def dirs      = inferDirectories(allFiles, currentPath)
    def files     = filterAndSortFiles(allFiles, currentPath, searchText, sortField, sortDir)
    def selected  = (settings.selectedFiles ?: []) as List
    def selCount  = selected.size()
    def selSize   = computeSelectedSize(allFiles, selected)
    def totalSize = (files.sum { it.size ?: 0L } ?: 0L) as Long

    dynamicPage(name: "mainPage", title: "Hubitat Bulk File Manager",
                install: true, uninstall: true) {

        // ── Result banner (persists until replaced by a new operation) ──
        if (state.lastResult) {
            def r     = state.lastResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;margin-bottom:4px;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
        }

        // ── Path breadcrumb + search ───────────────────────────────────
        section {
            paragraph buildBreadcrumb(currentPath)
            paragraph "<small style='color:#777;'>Browse the current folder below. Search is optional and only filters the visible file rows.</small>"
            input "searchText", "text",
                  title: "Search files in current folder…",
                  required: false, submitOnChange: true, width: 8
        }

        // ── Toolbar ────────────────────────────────────────────────────
        section("Actions") {
            input "btnSelectAll",   "button", title: "&#9745; Select All",  width: 2
            input "btnDeselectAll", "button", title: "&#9744; Clear",       width: 2
            href  "newFolderPage",
                  title      : "&#128193; New Folder",
                  description: "Create a subfolder in ${currentPath ?: '/'}",
                  width      : 2
            if (selCount > 0) {
                href "confirmDeletePage",
                     title      : "&#128465; Delete (${selCount})",
                     description: "Permanently delete ${selCount} item(s)",
                     width      : 2
                href "destinationPickerPage",
                     title      : "&#128203; Copy (${selCount})",
                     description: "Copy to another location",
                     params     : [op: "copy"],
                     width      : 2
                href "destinationPickerPage",
                     title      : "&#9986; Move (${selCount})",
                     description: "Move to another location",
                     params     : [op: "move"],
                     width      : 2
            }
        }

        // ── Sort controls ──────────────────────────────────────────────
        section {
            input "sortField", "enum",
                  title        : "Sort by",
                  options      : ["name": "Name", "size": "Size", "date": "Date Modified"],
                  defaultValue : "name",
                  required     : false,
                  submitOnChange: true,
                  width        : 4
            input "sortDir", "enum",
                  title        : "Direction",
                  options      : ["asc": "Ascending", "desc": "Descending"],
                  defaultValue : "asc",
                  required     : false,
                  submitOnChange: true,
                  width        : 4
        }

        // ── Explorer-style browser table ───────────────────────────────
        section("Browser") {
            paragraph buildFileBrowserTable(dirs, files, currentPath, sortField, sortDir)
        }

        // ── Selection input ────────────────────────────────────────────
        section("Select Items") {
            def options = buildSelectionOptions(files, currentPath)
            if (options) {
                input "selectedFiles", "enum",
                      title         : "${selCount} of ${options.size()} selected" +
                                      " (${formatSize(selSize)} / ${formatSize(totalSize)})",
                      multiple      : true,
                      options       : options,
                      required      : false,
                      submitOnChange: true
            } else {
                paragraph "<i style='color:#999;font-size:13px;'>No files in this location.</i>"
            }
        }

        // ── Status bar ─────────────────────────────────────────────────
        section {
            paragraph "<small style='color:#777;'>" +
                      "${dirs.size()} folder(s) &nbsp;&#183;&nbsp; " +
                      "${files.size()} file(s) &nbsp;&#183;&nbsp; " +
                      "Total: ${formatSize(totalSize)} &nbsp;&#183;&nbsp; " +
                      "${selCount} selected (${formatSize(selSize)})" +
                      "</small>"
            href "settingsPage", title: "&#9881; Settings", description: "Hub connection and display settings"
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  confirmDeletePage
// ────────────────────────────────────────────────────────────────

def confirmDeletePage() {
    def toDelete  = (settings.selectedFiles ?: []) as List
    def completed = state.deleteResult != null

    dynamicPage(name: "confirmDeletePage", title: "Confirm Delete",
                install: false, uninstall: false) {

        if (completed) {
            def r     = state.deleteResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section("Result") {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
            // Promote to mainPage banner and clear local flag
            state.lastResult  = state.deleteResult
            state.deleteResult = null
            section {
                href "mainPage",
                     title      : "&#8592; Back to File Manager",
                     description: "",
                     params     : [path: state.currentPath ?: ""]
            }
        } else {
            if (!toDelete) {
                section {
                    paragraph "No files are selected. Go back and select items first."
                    href "mainPage", title: "&#8592; Back", description: "",
                         params: [path: state.currentPath ?: ""]
                }
                return
            }
            section("You are about to permanently delete ${toDelete.size()} item(s):") {
                paragraph "<ul style='margin:4px 0;font-size:13px;'>" +
                          toDelete.collect { "<li>${escapeHtml(it?.toString())}</li>" }.join("") +
                          "</ul><br><b style='color:#c0392b;'>This action cannot be undone.</b>"
            }
            section {
                input "btnConfirmDelete", "button", title: "&#128465; Yes, Delete All"
                href  "mainPage",
                      title      : "&#10005; Cancel",
                      description: "",
                      params     : [path: state.currentPath ?: ""]
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  destinationPickerPage
// ────────────────────────────────────────────────────────────────

def destinationPickerPage() {
    // First entry from mainPage carries the op type; reset picker path
    if (params?.op) {
        state.pendingOp      = params.op
        state.destPickerPath = state.currentPath ?: ""
    }
    // Subsequent navigation within the picker updates the path
    if (params?.destPath != null) state.destPickerPath = params.destPath

    def op         = (state.pendingOp      ?: "copy").toString()
    def pickerPath = (state.destPickerPath ?: "").toString()
    def completed  = state.opResult != null

    dynamicPage(name: "destinationPickerPage",
                title: "${op.capitalize()} — Choose Destination",
                install: false, uninstall: false) {

        if (completed) {
            def r     = state.opResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section("Result") {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
            state.lastResult     = state.opResult
            state.opResult       = null
            state.pendingOp      = null
            state.destPickerPath = null
            section {
                href "mainPage",
                     title      : "&#8592; Back to File Manager",
                     description: "",
                     params     : [path: state.currentPath ?: ""]
            }
        } else {
            def allFiles = getFileList()
            def dirs     = inferDirectories(allFiles, pickerPath)

            section("Navigate to Destination") {
                paragraph buildBreadcrumb(pickerPath)
                if (pickerPath) {
                    href "destinationPickerPage",
                         title      : "&#128193; ..",
                         description: "Up to: ${parentPath(pickerPath) ?: '/'}",
                         params     : [destPath: parentPath(pickerPath), op: op]
                }
                dirs.each { dir ->
                    href "destinationPickerPage",
                         title      : "&#128193; ${dir}/",
                         description: "Navigate into folder",
                         params     : [destPath: "${pickerPath}${dir}/", op: op]
                }
                if (dirs.isEmpty() && !pickerPath) {
                    paragraph "<i style='color:#999;font-size:13px;'>No subfolders found at root. " +
                              "Type a destination path below or use root (/).</i>"
                }
            }

            def srcFiles = (settings.selectedFiles ?: []) as List
            section("Confirm ${op.capitalize()} Here") {
                input "destPath", "text",
                      title       : "Destination path (edit if needed)",
                      defaultValue: pickerPath,
                      required    : true
                paragraph "<small>${srcFiles.size()} file(s) will be ${op}d to the path above.</small>"
                input "btnConfirmOp", "button", title: "&#10003; Confirm ${op.capitalize()}"
                href  "mainPage",
                      title      : "&#10005; Cancel",
                      description: "",
                      params     : [path: state.currentPath ?: ""]
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  newFolderPage
// ────────────────────────────────────────────────────────────────

def newFolderPage() {
    def completed = state.folderResult != null

    dynamicPage(name: "newFolderPage", title: "Create New Folder",
                install: false, uninstall: false) {

        if (completed) {
            def r     = state.folderResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section("Result") {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
            state.lastResult   = state.folderResult
            state.folderResult = null
            section {
                href "mainPage",
                     title      : "&#8592; Back to File Manager",
                     description: "",
                     params     : [path: state.currentPath ?: ""]
            }
        } else {
            def parent = state.currentPath ?: "/"
            section("New Folder under: ${parent}") {
                input "newFolderName", "text",
                      title   : "Folder name",
                      required: true
                paragraph "<small style='color:#777;'>Allowed characters: letters, numbers, hyphens, " +
                          "underscores.  Other characters are converted to underscores.</small>"
                input "btnCreateFolder", "button", title: "&#128193; Create Folder"
                href  "mainPage",
                      title      : "&#10005; Cancel",
                      description: "",
                      params     : [path: state.currentPath ?: ""]
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  settingsPage
// ────────────────────────────────────────────────────────────────

def settingsPage() {
    dynamicPage(name: "settingsPage", title: "Settings",
                install: false, uninstall: false) {

        section("Hub Connection") {
            paragraph "The app calls the hub's local file manager API at " +
                      "<code>/hub/fileManager/json</code>.  Leave the IP blank to auto-detect.  " +
                      "If hub security is enabled, paste your access token below."
            input "hubIp",    "text",     title: "Hub IP Address (blank = auto-detect)", required: false
            input "hubToken", "password", title: "Hub Security Token (optional)",        required: false
        }
        section("Display") {
            input "maxFiles", "number",
                  title       : "Max files shown per directory",
                  defaultValue: 200,
                  range       : "10..2000",
                  required    : false
        }
        section {
            href "mainPage",
                 title      : "&#8592; Back to File Manager",
                 description: "",
                 params     : [path: state.currentPath ?: ""]
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  BUTTON HANDLER
// ════════════════════════════════════════════════════════════════

def appButtonHandler(String btn) {
    log.debug "Bulk File Manager — button: ${btn}"
    switch (btn) {

        // ── Selection ──────────────────────────────────────────────────
        case "btnSelectAll":
            def allFiles    = getFileList()
            def currentPath = (state.currentPath ?: "").toString()
            def files       = filterAndSortFiles(
                                allFiles, currentPath,
                                (settings.searchText ?: "").toString(),
                                (settings.sortField  ?: "name").toString(),
                                (settings.sortDir    ?: "asc").toString())
            def allKeys = buildSelectionOptions(files, currentPath).keySet().toList()
            app.updateSetting("selectedFiles", [type: "enum", value: allKeys])
            break

        case "btnDeselectAll":
            app.updateSetting("selectedFiles", [type: "enum", value: []])
            break

        // ── Delete (confirmed) ─────────────────────────────────────────
        case "btnConfirmDelete":
            def toDelete = (settings.selectedFiles ?: []) as List
            state.deleteResult = performDelete(toDelete)
            if (!state.deleteResult.errors) {
                app.updateSetting("selectedFiles", [type: "enum", value: []])
            }
            break

        // ── Copy / Move (confirmed) ────────────────────────────────────
        case "btnConfirmOp":
            def srcFiles = (settings.selectedFiles ?: []) as List
            def dest     = (settings.destPath ?: "").toString().trim()
            def op       = (state.pendingOp  ?: "copy").toString()
            state.opResult = (op == "move") ? performMove(srcFiles, dest)
                                            : performCopy(srcFiles, dest)
            if (!state.opResult.errors && op == "move") {
                app.updateSetting("selectedFiles", [type: "enum", value: []])
            }
            app.updateSetting("destPath", [type: "text", value: ""])
            break

        // ── Create folder ──────────────────────────────────────────────
        case "btnCreateFolder":
            def name = (settings.newFolderName ?: "").toString().trim()
            state.folderResult = createFolder(name)
            app.updateSetting("newFolderName", [type: "text", value: ""])
            break

        default:
            log.warn "Bulk File Manager — unhandled button: ${btn}"
    }
}

// ════════════════════════════════════════════════════════════════
//  APP LIFECYCLE
// ════════════════════════════════════════════════════════════════

def installed() {
    log.info "Hubitat Bulk File Manager installed"
    initialize()
}

def updated() {
    log.info "Hubitat Bulk File Manager updated"
    initialize()
}

def initialize() {
    if (state.currentPath  == null) state.currentPath  = ""
    state.lastResult   = null
    state.deleteResult = null
    state.opResult     = null
    state.folderResult = null
    state.pendingOp    = null
}

// ════════════════════════════════════════════════════════════════
//  FILE OPERATIONS
// ════════════════════════════════════════════════════════════════

/**
 * Returns the full flat file list from the hub's file manager API.
 * Each entry: [name: String, size: Long, date: String, mimeType: String]
 */
def getFileList() {
    def files = []
    try {
        httpGet([
            uri    : "${getHubBaseUrl()}/hub/fileManager/json",
            headers: makeAuthHeaders(),
            timeout: 30
        ]) { resp ->
            if (resp.status != 200) {
                log.warn "getFileList: HTTP ${resp.status}"
                return
            }
            def data = resp.data
            // API may return a list directly or wrap it in { fileList: [...] }
            def list = (data instanceof List) ? data : (data?.fileList ?: [])
            files = list.collect { f ->
                def n = (f.name ?: "").toString()
                [
                    name    : n,
                    size    : (f.size ?: 0L) as Long,
                    date    : (f.date ?: "").toString(),
                    mimeType: getMimeType(n)
                ]
            }.findAll { it.name }
        }
    } catch (e) {
        log.error "getFileList: ${e.message}"
    }
    return files
}

/**
 * Deletes each file in fileNames.
 * Tries the built-in sandbox call first; falls back to HTTP POST.
 */
def performDelete(List fileNames) {
    def deleted = 0
    def errors  = []

    fileNames.each { raw ->
        def name = raw?.toString()?.trim()
        if (!name) return
        try {
            deleteHubFile(name)
            deleted++
        } catch (e1) {
            try {
                httpPostJson([
                    uri    : "${getHubBaseUrl()}/hub/fileManager/delete",
                    headers: makeAuthHeaders(),
                    body   : [fileName: name],
                    timeout: 30
                ]) { resp ->
                    if (resp.status == 200) deleted++
                    else { log.warn "Delete HTTP ${resp.status} for ${name}"; errors << name }
                }
            } catch (e2) {
                log.error "Delete failed for ${name}: ${e2.message}"
                errors << name
            }
        }
    }

    def n = fileNames.size()
    return [
        message: errors ? "Deleted ${deleted} of ${n} item(s). " +
                          "Failed: ${errors.take(5).join(', ')}${errors.size() > 5 ? '…' : ''}"
                        : "Deleted ${deleted} item(s) successfully.",
        errors : !errors.isEmpty()
    ]
}

/**
 * Copies each file to destPath, preserving the base filename.
 * Downloads source content then re-uploads under the new path.
 */
def performCopy(List fileNames, String destPath) {
    def copied = 0
    def errors = []
    def dest   = destPath?.replaceAll('/+$', '') ?: ""

    fileNames.each { raw ->
        def srcName = raw?.toString()?.trim()
        if (!srcName) return
        def baseName = srcName.contains("/") ? srcName.tokenize("/").last() : srcName
        def destName = dest ? "${dest}/${baseName}" : baseName
        try {
            def bytes = downloadFileBytes(srcName)
            if (bytes == null) { errors << srcName; return }
            uploadHubFile(destName, bytes)
            copied++
        } catch (e) {
            log.error "Copy failed for ${srcName}: ${e.message}"
            errors << srcName
        }
    }

    def n = fileNames.size()
    return [
        message: errors ? "Copied ${copied} of ${n} file(s). " +
                          "Failed: ${errors.take(5).join(', ')}${errors.size() > 5 ? '…' : ''}"
                        : "Copied ${copied} file(s) to ${dest ?: '/'} successfully.",
        errors : !errors.isEmpty()
    ]
}

/**
 * Moves each file to destPath.
 * Only deletes sources if ALL copies succeed (safe-move semantics).
 */
def performMove(List fileNames, String destPath) {
    def copyResult = performCopy(fileNames, destPath)
    if (copyResult.errors) {
        return [
            message: "Move aborted: copy step had errors — no originals deleted. ${copyResult.message}",
            errors : true
        ]
    }
    def deleteResult = performDelete(fileNames)
    return [
        message: "Moved ${fileNames.size()} file(s) to ${destPath ?: '/'}." +
                 (deleteResult.errors ? " Warning: some source files could not be removed." : ""),
        errors : deleteResult.errors
    ]
}

/**
 * Creates a new virtual folder by uploading a zero-byte .keep placeholder.
 */
def createFolder(String folderName) {
    if (!folderName?.trim()) {
        return [message: "Folder name cannot be empty.", errors: true]
    }
    def clean       = folderName.replaceAll(/[^a-zA-Z0-9_\-]/, "_")
    def currentPath = (state.currentPath ?: "").toString()
    def placeholder = "${currentPath}${clean}/.keep"
    try {
        uploadHubFile(placeholder, new byte[0])
        return [message: "Folder '${clean}' created at ${currentPath ?: '/'}.", errors: false]
    } catch (e) {
        log.error "createFolder: ${e.message}"
        return [message: "Failed to create folder '${clean}': ${e.message}", errors: true]
    }
}

/**
 * Downloads a file's raw bytes.
 * Tries the built-in sandbox call first; falls back to HTTP GET /local/<name>.
 */
def downloadFileBytes(String filename) {
    try {
        return downloadHubFile(filename)
    } catch (e1) {
        try {
            def result = null
            httpGet([
                uri    : "${getHubBaseUrl()}/local/${filename}",
                headers: makeAuthHeaders(),
                timeout: 60
            ]) { resp ->
                result = resp.data?.bytes
            }
            return result
        } catch (e2) {
            log.error "downloadFileBytes failed for ${filename}: ${e2.message}"
            return null
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  FILTERING AND SORTING
// ════════════════════════════════════════════════════════════════

/**
 * Returns files that are direct children of currentPath
 * (no deeper nesting), optionally matching a search term,
 * sorted by sortField in sortDir order.
 */
def filterAndSortFiles(List allFiles, String currentPath,
                       String search, String sortField, String sortDir) {
    def maxRaw   = (settings.maxFiles ?: 200) as int
    def max      = Math.max(10, Math.min(2000, maxRaw))
    def filtered = allFiles.findAll { f ->
        def name = (f.name ?: "").toString()
        if (!name.startsWith(currentPath)) return false
        def remainder = name.substring(currentPath.length())
        if (!remainder || remainder.contains("/")) return false   // skip dirs and self
        if (search) return remainder.toLowerCase().contains(search.toLowerCase())
        return true
    }
    filtered.sort { a, b ->
        def av, bv
        switch (sortField) {
            case "size": av = (a.size ?: 0L) as Long; bv = (b.size ?: 0L) as Long; break
            case "date": av = a.date ?: "";            bv = b.date ?: "";            break
            default:     av = a.name ?: "";            bv = b.name ?: "";            break
        }
        sortDir == "desc" ? bv <=> av : av <=> bv
    }
    return filtered.take(max)
}

/**
 * Returns sorted list of virtual sub-directory names directly under currentPath.
 */
def inferDirectories(List allFiles, String currentPath) {
    def dirs = [] as Set
    allFiles.each { f ->
        def name = (f.name ?: "").toString()
        if (!name.startsWith(currentPath)) return
        def remainder = name.substring(currentPath.length())
        def parts = remainder.tokenize("/")
        if (parts.size() > 1) dirs << parts[0]
    }
    return dirs.sort()
}

/**
 * Sums sizes of the selected files (by name key in allFiles).
 */
def computeSelectedSize(List allFiles, List selected) {
    if (!selected) return 0L
    def lookup = allFiles.collectEntries { [(it.name): (it.size ?: 0L) as Long] }
    return ((selected.sum { lookup[it] ?: 0L }) ?: 0L) as Long
}

// ════════════════════════════════════════════════════════════════
//  UI BUILDERS
// ════════════════════════════════════════════════════════════════

def buildBreadcrumb(String path) {
    def sb = new StringBuilder()
    sb.append("<div style='font-family:monospace;font-size:13px;padding:4px 0;'>")
    sb.append("&#128194; <b>/local</b>")
    if (path) {
        path.replaceAll('/+$', '').tokenize("/").each { part ->
            sb.append(" <span style='color:#aaa;'>&#8250;</span> <b>${escapeHtml(part)}</b>")
        }
    }
    sb.append("</div>")
    return sb.toString()
}

/**
 * Renders the file listing as an HTML table (display only — selection
 * is handled separately by the enum input below the table).
 */
def buildFileBrowserTable(List dirs, List files, String currentPath,
                          String sortField = "name", String sortDir = "asc") {
    def hasParent = !!currentPath
    if (!hasParent && dirs.isEmpty() && files.isEmpty()) {
        return "<i style='color:#999;font-size:13px;'>This location is empty.</i>"
    }
    def sb = new StringBuilder()
    sb.append("""<style>
.fmtbl{width:100%;border-collapse:collapse;font-size:12px;font-family:monospace;}
.fmtbl th{background:#3c3f41;color:#eee;padding:5px 8px;text-align:left;}
.fmtbl td{padding:4px 8px;border-bottom:1px solid #eee;vertical-align:middle;}
.fmtbl tr:hover td{background:#f0f4ff;}
.fmtbl .sz{text-align:right;}
.fmtbl .dt{color:#888;}
.fmtbl .mt{color:#aaa;font-size:11px;}
.fmtbl th a{color:#eee;text-decoration:none;display:block;}
.fmtbl th a:hover{text-decoration:underline;}
.fmtbl .nav a,.fmtbl .nm a{color:#2c3e50;text-decoration:none;display:block;}
.fmtbl .nav a:hover,.fmtbl .nm a:hover{text-decoration:underline;}
</style>
<table class='fmtbl'>
<thead><tr>
  <th>Type</th>
  <th>${buildSortHeaderLink("Name", "name", currentPath, sortField, sortDir)}</th>
  <th>MIME Type</th>
  <th class='sz'>${buildSortHeaderLink("Size", "size", currentPath, sortField, sortDir)}</th>
  <th>${buildSortHeaderLink("Modified", "date", currentPath, sortField, sortDir)}</th>
</tr></thead><tbody>""")

    if (hasParent) {
        def parent = parentPath(currentPath)
        sb.append("""<tr>
<td class='nav'>&#128193;</td>
<td class='nm'><a href='?path=${urlEncode(parent)}'>&#128193; ..</a></td>
<td class='mt'>Folder</td>
<td class='sz'>-</td>
<td class='dt'>Up to ${escapeHtml(parent ?: '/')}</td>
</tr>""")
    }

    dirs.each { dir ->
        def fullPath = "${currentPath}${dir}/"
        sb.append("""<tr>
<td class='nav'>&#128193;</td>
<td class='nm'><a href='?path=${urlEncode(fullPath)}'>&#128193; ${escapeHtml(dir)}</a></td>
<td class='mt'>Folder</td>
<td class='sz'>-</td>
<td class='dt'>Folder</td>
</tr>""")
    }

    files.each { f ->
        def name = f.name.substring(currentPath.length())
        sb.append("""<tr>
<td>${getFileIcon(f.mimeType)}</td>
<td>${escapeHtml(name)}</td>
<td class='mt'>${f.mimeType ?: '—'}</td>
<td class='sz'>${formatSize(f.size ?: 0L)}</td>
<td class='dt'>${formatDate(f.date ?: '')}</td>
</tr>""")
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

def buildSortHeaderLink(String label, String field, String currentPath,
                        String activeSortField = "name", String activeSortDir = "asc") {
    def nextDir   = (activeSortField == field && activeSortDir == "asc") ? "desc" : "asc"
    def indicator = (activeSortField == field) ? (activeSortDir == "asc" ? " &#9650;" : " &#9660;") : ""
    def pathPart  = currentPath ? "&path=${urlEncode(currentPath)}" : ""
    return "<a href='?sortField=${urlEncode(field)}&sortDir=${urlEncode(nextDir)}${pathPart}'>" +
           "${escapeHtml(label)}${indicator}</a>"
}

/**
 * Builds the options map for the selectedFiles enum input.
 * Keys are the full file names (used in all operations).
 * Labels include icon, short name, size, and date for context.
 */
def buildSelectionOptions(List files, String currentPath) {
    def options = [:]
    files.each { f ->
        def shortName = f.name.substring(currentPath.length())
        def label     = "${getFileIcon(f.mimeType)} ${shortName}" +
                        "   (${formatSize(f.size ?: 0L)}, ${formatDate(f.date ?: '')})"
        options[f.name] = label
    }
    return options
}

// ════════════════════════════════════════════════════════════════
//  UTILITY HELPERS
// ════════════════════════════════════════════════════════════════

def parentPath(String path) {
    if (!path) return ""
    def parts = path.replaceAll('/+$', '').tokenize("/")
    if (parts.size() <= 1) return ""
    parts.removeLast()
    return parts.join("/") + "/"
}

def formatSize(Long bytes) {
    if (!bytes || bytes < 0L) return "0 B"
    if (bytes < 1_024L)            return "${bytes} B"
    if (bytes < 1_048_576L)        return "${String.format('%.1f', bytes / 1_024.0)} KB"
    if (bytes < 1_073_741_824L)    return "${String.format('%.1f', bytes / 1_048_576.0)} MB"
    return "${String.format('%.2f', bytes / 1_073_741_824.0)} GB"
}

def formatDate(String dateStr) {
    if (!dateStr?.trim()) return "—"
    try {
        def sdf1 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        def sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
        return sdf2.format(sdf1.parse(dateStr))
    } catch (ignored) {
        return dateStr.take(16)
    }
}

def getMimeType(String filename) {
    if (!filename) return "application/octet-stream"
    def ext = filename.tokenize(".").last()?.toLowerCase() ?: ""
    return ([
        // Images
        png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg",
        gif: "image/gif", svg: "image/svg+xml", ico: "image/x-icon",
        webp: "image/webp", bmp: "image/bmp",
        // Text / Code
        txt: "text/plain", csv: "text/csv", md: "text/markdown",
        html: "text/html", htm: "text/html", css: "text/css",
        js: "application/javascript", groovy: "text/x-groovy",
        // Data
        json: "application/json", xml: "application/xml",
        // Documents
        pdf: "application/pdf",
        // Archives
        zip: "application/zip", gz: "application/gzip",
        tar: "application/x-tar", jar: "application/java-archive"
    ][ext]) ?: "application/octet-stream"
}

def getFileIcon(String mimeType) {
    if (!mimeType) return "&#128196;"          // 📄
    // Images first so image/svg+xml doesn't fall through to the xml code-check below
    if (mimeType.startsWith("image/"))  return "&#128247;"  // 🖼
    // Code/data types — checked before text/* so groovy/js/json/xml win over plain 📝
    if (mimeType.contains("json")  ||
        mimeType.contains("xml")   ||
        mimeType.contains("javascript") ||
        mimeType.contains("groovy")) return "&#9881;"       // ⚙️
    if (mimeType.startsWith("text/"))   return "&#128221;"  // 📝
    if (mimeType.startsWith("audio/"))  return "&#127925;"  // 🎵
    if (mimeType.startsWith("video/"))  return "&#127916;"  // 🎬
    if (mimeType.contains("pdf"))       return "&#128213;"  // 📕
    if (mimeType.contains("zip")   ||
        mimeType.contains("tar")   ||
        mimeType.contains("gzip")  ||
        mimeType.contains("archive")) return "&#128230;"    // 📦
    return "&#128196;"                                       // 📄
}

def escapeHtml(String s) {
    s?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""
}

def urlEncode(String s) {
    java.net.URLEncoder.encode(s ?: "", "UTF-8").replace("+", "%20")
}

def getHubBaseUrl() {
    def ip = settings.hubIp?.trim() ?: location.hub?.localIP ?: "127.0.0.1"
    return "http://${ip}"
}

def makeAuthHeaders() {
    def headers = ["Accept": "application/json"]
    if (settings.hubToken?.trim()) {
        headers["Authorization"] = "Bearer ${settings.hubToken}"
    }
    return headers
}
