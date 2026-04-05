/**
 *  Hubitat Bulk File Manager
 *  Version: 1.0.5
 *  Author:  Brian Pavane
 *
 *  Features:
 *    - Finder-style flat file listing on the main page
 *    - Real-time search / filter (case-insensitive)
 *    - Sort by name, size, or date (ascending / descending)
 *    - Bulk and individual file selection (with running size total)
 *    - Delete with mandatory confirmation step
 *    - Copy and Move to a user-specified destination path
 *    - Configurable hub IP and optional security token
 *    - Zero-configuration install — works immediately after adding
 *
 *  Architecture notes:
 *    - Hubitat files are stored in a flat namespace (/local/).
 *      There are no subdirectories; this app treats all files as peers.
 *    - File listing: HTTP GET /hub/fileManager/json
 *    - Upload (copy/move target): uploadHubFile(name, bytes)  [sandbox built-in]
 *    - Download (copy/move source): downloadHubFile(name)     [sandbox, with HTTP fallback]
 *    - Delete: deleteHubFile(name)                            [sandbox, with HTTP fallback]
 */

// ════════════════════════════════════════════════════════════════
//  DEFINITION
// ════════════════════════════════════════════════════════════════

definition(
    name          : "Hubitat Bulk File Manager",
    namespace     : "bpavane",
    author        : "Brian Pavane",
    description   : "Finder-style bulk file manager for Hubitat hub files",
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
    page(name: "settingsPage",          content: "settingsPage")
}

// ────────────────────────────────────────────────────────────────
//  mainPage
// ────────────────────────────────────────────────────────────────

def mainPage() {
    def version   = "1.0.5"
    def allFiles  = getFileList()
    def sortField = (settings.sortField  ?: "name").toString()
    def sortDir   = (settings.sortDir    ?: "asc").toString()
    def search    = (settings.searchText ?: "").toString()
    def files     = filterFiles(allFiles, search, sortField, sortDir)
    def selected  = (settings.selectedFiles ?: []) as List
    def selCount  = selected.size()
    def selSize   = computeSelectedSize(allFiles, selected)
    def totalSize = (allFiles.sum { it.size ?: 0L } ?: 0L) as Long

    dynamicPage(name: "mainPage", title: "Hubitat Bulk File Manager  \u2022  v${version}",
                install: true, uninstall: true) {

        // ── Result banner ──────────────────────────────────────────────
        if (state.lastResult) {
            def r     = state.lastResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;margin-bottom:4px;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
        }

        if (state.fileListStatus?.message) {
            def s = state.fileListStatus
            def color = s.errors ? "#c0392b" : "#2c7a7b"
            section {
                paragraph """<div style="padding:8px 12px;border-left:5px solid ${color};\
background:#fafafa;margin-bottom:4px;border-radius:3px;font-size:12px;color:#555;">\
&#128421;&nbsp;${escapeHtml(s.message)}</div>"""
            }
        }

        // ── Search + Sort ──────────────────────────────────────────────
        section {
            input "searchText", "text",
                  title         : "&#128269; Search files\u2026",
                  required      : false,
                  submitOnChange: true,
                  width         : 6
            input "sortField", "enum",
                  title        : "Sort by",
                  options      : ["name": "Name", "size": "Size", "date": "Date Modified"],
                  defaultValue : "name",
                  required     : false,
                  submitOnChange: true,
                  width        : 3
            input "sortDir", "enum",
                  title        : "Direction",
                  options      : ["asc": "Ascending", "desc": "Descending"],
                  defaultValue : "asc",
                  required     : false,
                  submitOnChange: true,
                  width        : 3
        }

        // ── Toolbar ────────────────────────────────────────────────────
        section("Actions") {
            input "btnSelectAll",   "button", title: "&#9745; Select All",  width: 2
            input "btnDeselectAll", "button", title: "&#9744; Clear",       width: 2
            if (selCount > 0) {
                href "confirmDeletePage",
                     title      : "&#128465; Delete (${selCount})",
                     description: "Permanently delete ${selCount} item(s)",
                     width      : 2
                href "destinationPickerPage",
                     title      : "&#128203; Copy (${selCount})",
                     description: "Copy to another filename / path",
                     params     : [op: "copy"],
                     width      : 2
                href "destinationPickerPage",
                     title      : "&#9986; Move (${selCount})",
                     description: "Move to another filename / path",
                     params     : [op: "move"],
                     width      : 2
            }
        }

        // ── Finder-style file browser ──────────────────────────────────
        section("Files  /local/") {
            paragraph buildFinderTable(files, sortField, sortDir)
        }

        // ── Selection input ────────────────────────────────────────────
        section("Select Files") {
            def options = buildSelectionOptions(files)
            if (options) {
                input "selectedFiles", "enum",
                      title         : "${selCount} of ${options.size()} selected" +
                                      "  (${formatSize(selSize)} / ${formatSize(totalSize)})",
                      multiple      : true,
                      options       : options,
                      required      : false,
                      submitOnChange: true
            } else {
                paragraph "<i style='color:#999;font-size:13px;'>No files found.</i>"
            }
        }

        // ── Status bar ─────────────────────────────────────────────────
        section {
            paragraph "<small style='color:#777;'>" +
                      "${allFiles.size()} file(s) total &nbsp;&#183;&nbsp; " +
                      "${files.size()} shown &nbsp;&#183;&nbsp; " +
                      "Total: ${formatSize(totalSize)} &nbsp;&#183;&nbsp; " +
                      "${selCount} selected (${formatSize(selSize)}) &nbsp;&#183;&nbsp; " +
                      "v${version}" +
                      "</small>"
            href "settingsPage",
                 title      : "&#9881; Settings",
                 description: "Hub connection and display settings"
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  confirmDeletePage
// ────────────────────────────────────────────────────────────────

def confirmDeletePage() {
    def toDelete  = (settings.selectedFiles ?: []) as List
    def completed = state.deleteResult != null

    dynamicPage(name: "confirmDeletePage", title: "Confirm Delete  \u2022  v1.0.5",
                install: false, uninstall: false) {

        if (completed) {
            def r     = state.deleteResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section("Result") {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
            state.lastResult   = state.deleteResult
            state.deleteResult = null
            section {
                href "mainPage", title: "&#8592; Back to File Manager", description: ""
            }
        } else {
            if (!toDelete) {
                section {
                    paragraph "No files are selected. Go back and select items first."
                    href "mainPage", title: "&#8592; Back", description: ""
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
                href  "mainPage", title: "&#10005; Cancel", description: ""
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  destinationPickerPage
// ────────────────────────────────────────────────────────────────

def destinationPickerPage() {
    if (params?.op) state.pendingOp = params.op

    def op        = (state.pendingOp ?: "copy").toString()
    def completed = state.opResult != null

    dynamicPage(name: "destinationPickerPage",
                title: "${op.capitalize()} Files  \u2022  v1.0.5",
                install: false, uninstall: false) {

        if (completed) {
            def r     = state.opResult
            def color = r.errors ? "#c0392b" : "#27ae60"
            section("Result") {
                paragraph """<div style="padding:10px 14px;border-left:5px solid ${color};\
background:#fafafa;border-radius:3px;font-size:13px;">\
${r.errors ? "&#9888;" : "&#10003;"}&nbsp;${r.message}</div>"""
            }
            state.lastResult = state.opResult
            state.opResult   = null
            state.pendingOp  = null
            section {
                href "mainPage", title: "&#8592; Back to File Manager", description: ""
            }
        } else {
            def srcFiles = (settings.selectedFiles ?: []) as List
            section("${op.capitalize()} ${srcFiles.size()} file(s)") {
                paragraph "<b>Files to ${op}:</b><ul style='margin:4px 0;font-size:13px;'>" +
                          srcFiles.collect { "<li>${escapeHtml(it?.toString())}</li>" }.join("") +
                          "</ul>"
                input "destPath", "text",
                      title   : "Destination path or prefix",
                      required: true
                paragraph """<small style='color:#777;'>
Examples: <code>backup/photo.jpg</code> (single file) or <code>backup</code> (prefix \u2014 each file \
is ${op}d to <code>backup/filename</code>).  Hubitat files are stored flat; \
any <code>/</code> in the name is part of the filename.</small>"""
                input "btnConfirmOp", "button", title: "&#10003; Confirm ${op.capitalize()}"
                href  "mainPage", title: "&#10005; Cancel", description: ""
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
//  settingsPage
// ────────────────────────────────────────────────────────────────

def settingsPage() {
    dynamicPage(name: "settingsPage", title: "Settings  \u2022  v1.0.5",
                install: false, uninstall: false) {

        section("Hub Connection") {
            paragraph "Calls the hub\u2019s local file manager API at " +
                      "<code>/hub/fileManager/json</code>.  Leave the IP blank to auto-detect.  " +
                      "A security token is only needed if hub login is enabled."
            input "hubIp",    "text",     title: "Hub IP Address (blank = auto-detect)", required: false
            input "hubToken", "password", title: "Hub Security Token (optional)",        required: false
        }
        section("Display") {
            input "maxFiles", "number",
                  title       : "Max files shown",
                  defaultValue: 200,
                  range       : "10..2000",
                  required    : false
        }
        section {
            href "mainPage", title: "&#8592; Back to File Manager", description: ""
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  BUTTON HANDLER
// ════════════════════════════════════════════════════════════════

def appButtonHandler(String btn) {
    log.debug "Bulk File Manager \u2014 button: ${btn}"
    switch (btn) {

        case "btnSelectAll":
            def allFiles = getFileList()
            def files    = filterFiles(allFiles,
                               (settings.searchText ?: "").toString(),
                               (settings.sortField  ?: "name").toString(),
                               (settings.sortDir    ?: "asc").toString())
            def allKeys  = buildSelectionOptions(files).keySet().toList()
            app.updateSetting("selectedFiles", [type: "enum", value: allKeys])
            break

        case "btnDeselectAll":
            app.updateSetting("selectedFiles", [type: "enum", value: []])
            break

        case "btnConfirmDelete":
            def toDelete = (settings.selectedFiles ?: []) as List
            state.deleteResult = performDelete(toDelete)
            if (!state.deleteResult.errors) {
                app.updateSetting("selectedFiles", [type: "enum", value: []])
            }
            break

        case "btnConfirmOp":
            def srcFiles = (settings.selectedFiles ?: []) as List
            def dest     = (settings.destPath ?: "").toString().trim()
            def op       = (state.pendingOp   ?: "copy").toString()
            state.opResult = (op == "move") ? performMove(srcFiles, dest)
                                            : performCopy(srcFiles, dest)
            if (!state.opResult.errors && op == "move") {
                app.updateSetting("selectedFiles", [type: "enum", value: []])
            }
            app.updateSetting("destPath", [type: "text", value: ""])
            state.pendingOp = null
            break

        default:
            log.warn "Bulk File Manager \u2014 unhandled button: ${btn}"
    }
}

// ════════════════════════════════════════════════════════════════
//  APP LIFECYCLE
// ════════════════════════════════════════════════════════════════

def installed() {
    log.info "Hubitat Bulk File Manager v1.0.5 installed"
    initialize()
}

def updated() {
    log.info "Hubitat Bulk File Manager v1.0.5 updated"
    initialize()
}

def initialize() {
    state.lastResult   = null
    state.deleteResult = null
    state.opResult     = null
    state.pendingOp    = null
}

// ════════════════════════════════════════════════════════════════
//  FILE OPERATIONS
// ════════════════════════════════════════════════════════════════

/**
 * Returns the flat file list from the hub's file manager API.
 * Each entry: [name: String, size: Long, date: String, mimeType: String]
 */
def getFileList() {
    def files = []
    def emptySuccess = null
    def errors = []

    for (baseUrl in getFileManagerBaseUrls()) {
        try {
            def candidate = null
            def success = false
            httpGet([
                uri    : "${baseUrl}/hub/fileManager/json",
                headers: makeAuthHeaders(),
                timeout: 30
            ]) { resp ->
                if (resp.status != 200) {
                    log.warn "getFileList: HTTP ${resp.status} via ${baseUrl}"
                    errors << "HTTP ${resp.status} via ${baseUrl}"
                    return
                }
                success = true
                def data = resp.data
                def list = (data instanceof List) ? data : (data?.files ?: data?.fileList ?: [])
                candidate = list.collect { f ->
                    def n = (f.name ?: f.fileName ?: "").toString()
                    [
                        name    : n,
                        size    : (f.size ?: 0L) as Long,
                        date    : (f.date ?: f.lastModified ?: "").toString(),
                        mimeType: getMimeType(n)
                    ]
                }.findAll { it.name }
            }

            if (!success) continue

            if (candidate && !candidate.isEmpty()) {
                files = candidate
                state.fileListStatus = [
                    message: "Loaded ${candidate.size()} file(s) from ${baseUrl}/hub/fileManager/json",
                    errors : false,
                    baseUrl: baseUrl
                ]
                return files
            }

            if (emptySuccess == null) {
                emptySuccess = [
                    files  : candidate ?: [],
                    baseUrl: baseUrl
                ]
            }
        } catch (e) {
            log.warn "getFileList: ${e.message} via ${baseUrl}"
            errors << "${e.message} via ${baseUrl}"
        }
    }

    if (emptySuccess != null) {
        state.fileListStatus = [
            message: "Connected to ${emptySuccess.baseUrl}/hub/fileManager/json but it returned 0 files.",
            errors : true,
            baseUrl: emptySuccess.baseUrl
        ]
        return emptySuccess.files
    }

    state.fileListStatus = [
        message: "Unable to load Hubitat File Manager listing on ports 8080 or 80. " +
                 (errors ? "Last result: ${errors[0]}" : "Check hub IP and token settings."),
        errors : true
    ]
    return files
}

/**
 * Deletes each file. Tries the built-in sandbox call first; falls back to HTTP POST.
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
                          "Failed: ${errors.take(5).join(', ')}${errors.size() > 5 ? '\u2026' : ''}"
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
        def srcName  = raw?.toString()?.trim()
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
                          "Failed: ${errors.take(5).join(', ')}${errors.size() > 5 ? '\u2026' : ''}"
                        : "Copied ${copied} file(s) to ${dest ?: '/'} successfully.",
        errors : !errors.isEmpty()
    ]
}

/**
 * Moves each file to destPath.
 * Safe-move: sources are only deleted after ALL copies succeed.
 */
def performMove(List fileNames, String destPath) {
    def copyResult = performCopy(fileNames, destPath)
    if (copyResult.errors) {
        return [
            message: "Move aborted: copy step had errors \u2014 no originals deleted. ${copyResult.message}",
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
 * Returns the filtered and sorted file list.
 * Hubitat's file store is flat — no path-based filtering is needed.
 */
def filterFiles(List allFiles, String search, String sortField, String sortDir) {
    def maxRaw   = (settings.maxFiles ?: 200) as int
    def max      = Math.max(10, Math.min(2000, maxRaw))
    def filtered = allFiles.findAll { f ->
        if (!search?.trim()) return true
        (f.name ?: "").toLowerCase().contains(search.toLowerCase())
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
 * Sums the sizes of the selected files (matched by name key in allFiles).
 */
def computeSelectedSize(List allFiles, List selected) {
    if (!selected) return 0L
    def lookup = allFiles.collectEntries { [(it.name): (it.size ?: 0L) as Long] }
    return ((selected.sum { lookup[it] ?: 0L }) ?: 0L) as Long
}

// ════════════════════════════════════════════════════════════════
//  UI BUILDERS
// ════════════════════════════════════════════════════════════════

/**
 * Renders the flat file list as a Finder-style HTML table.
 * Sort indicators appear on the active column header.
 */
def buildFinderTable(List files, String sortField = "name", String sortDir = "asc") {
    if (files.isEmpty()) {
        return """<div style="padding:30px;text-align:center;color:#aaa;font-size:13px;\
background:#fafafa;border-radius:6px;border:1px dashed #ddd;">
&#128194; No files found.</div>"""
    }

    def nameCls  = sortField == "name" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""
    def sizeCls  = sortField == "size" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""
    def dateCls  = sortField == "date" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""

    def sb = new StringBuilder()
    sb.append("""<style>
.fdr{width:100%;border-collapse:collapse;font-size:13px;\
font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,sans-serif;}
.fdr thead tr{background:#efefef;}
.fdr th{padding:7px 10px;text-align:left;border-bottom:2px solid #d4d4d4;\
font-weight:600;color:#333;white-space:nowrap;user-select:none;}
.fdr th.sz{text-align:right;}
.fdr td{padding:5px 10px;border-bottom:1px solid #f0f0f0;vertical-align:middle;}
.fdr tr:hover td{background:#e8f0fe;}
.fdr .sz{text-align:right;font-family:monospace;font-size:12px;color:#555;}
.fdr .dt{color:#777;font-size:12px;}
.fdr .mt{color:#bbb;font-size:11px;}
.fdr .ic{width:30px;text-align:center;font-size:15px;}
.sort-asc::after{content:' \u25b2';font-size:10px;}
.sort-desc::after{content:' \u25bc';font-size:10px;}
</style>
<table class='fdr'>
<thead><tr>
  <th class='ic'></th>
  <th class='${nameCls}'>Name</th>
  <th>Type</th>
  <th class='sz ${sizeCls}'>Size</th>
  <th class='${dateCls}'>Modified</th>
</tr></thead>
<tbody>""")

    files.eachWithIndex { f, i ->
        def bg = (i % 2 == 1) ? " style='background:#f9f9f9;'" : ""
        sb.append("""<tr${bg}>
<td class='ic'>${getFileIcon(f.mimeType)}</td>
<td>${escapeHtml(f.name)}</td>
<td class='mt'>${escapeHtml(f.mimeType ?: '\u2014')}</td>
<td class='sz'>${formatSize(f.size ?: 0L)}</td>
<td class='dt'>${formatDate(f.date ?: '')}</td>
</tr>""")
    }
    sb.append("</tbody></table>")
    return sb.toString()
}

/**
 * Builds the options map for the selectedFiles enum input.
 * Key = full filename; label = icon + name + size + date.
 */
def buildSelectionOptions(List files) {
    def options = [:]
    files.each { f ->
        def label = "${getFileIcon(f.mimeType)} ${f.name}" +
                    "   (${formatSize(f.size ?: 0L)}, ${formatDate(f.date ?: '')})"
        options[f.name] = label
    }
    return options
}

// ════════════════════════════════════════════════════════════════
//  UTILITY HELPERS
// ════════════════════════════════════════════════════════════════

def formatSize(Long bytes) {
    if (!bytes || bytes < 0L) return "0 B"
    if (bytes < 1_024L)            return "${bytes} B"
    if (bytes < 1_048_576L)        return "${String.format('%.1f', bytes / 1_024.0)} KB"
    if (bytes < 1_073_741_824L)    return "${String.format('%.1f', bytes / 1_048_576.0)} MB"
    return "${String.format('%.2f', bytes / 1_073_741_824.0)} GB"
}

def formatDate(String dateStr) {
    if (!dateStr?.trim()) return "\u2014"
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
        png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg",
        gif: "image/gif", svg: "image/svg+xml", ico: "image/x-icon",
        webp: "image/webp", bmp: "image/bmp",
        txt: "text/plain", csv: "text/csv", md: "text/markdown",
        html: "text/html", htm: "text/html", css: "text/css",
        js: "application/javascript", groovy: "text/x-groovy",
        json: "application/json", xml: "application/xml",
        pdf: "application/pdf",
        zip: "application/zip", gz: "application/gzip",
        tar: "application/x-tar", jar: "application/java-archive"
    ][ext]) ?: "application/octet-stream"
}

def getFileIcon(String mimeType) {
    if (!mimeType) return "&#128196;"
    if (mimeType.startsWith("image/"))  return "&#128247;"   // 🖼
    if (mimeType.contains("json")  ||
        mimeType.contains("xml")   ||
        mimeType.contains("javascript") ||
        mimeType.contains("groovy")) return "&#9881;"         // ⚙️
    if (mimeType.startsWith("text/"))   return "&#128221;"   // 📝
    if (mimeType.startsWith("audio/"))  return "&#127925;"   // 🎵
    if (mimeType.startsWith("video/"))  return "&#127916;"   // 🎬
    if (mimeType.contains("pdf"))       return "&#128213;"   // 📕
    if (mimeType.contains("zip")   ||
        mimeType.contains("tar")   ||
        mimeType.contains("gzip")  ||
        mimeType.contains("archive")) return "&#128230;"     // 📦
    return "&#128196;"                                        // 📄
}

def escapeHtml(String s) {
    s?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""
}

def getHubBaseUrl() {
    def ip = settings.hubIp?.trim() ?: location.hub?.localIP ?: "127.0.0.1"
    return "http://${ip}"
}

def getFileManagerBaseUrls() {
    def ip = settings.hubIp?.trim() ?: location.hub?.localIP ?: "127.0.0.1"
    if (ip.contains(":")) return ["http://${ip}"]
    return ["http://${ip}:8080", "http://${ip}"]
}

def makeAuthHeaders() {
    def headers = ["Accept": "application/json"]
    if (settings.hubToken?.trim()) {
        headers["Authorization"] = "Bearer ${settings.hubToken}"
    }
    return headers
}
