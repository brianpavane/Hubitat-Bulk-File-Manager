/**
 *  Hubitat Bulk File Manager
 *  Version: 1.0.9
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

mappings {
    path("/ui")        { action: [GET: "renderWebUi"] }
    path("/api/files") { action: [GET: "apiFiles"] }
    path("/api/config"){ action: [GET: "apiConfig"] }
    path("/api/delete"){ action: [POST: "apiDelete"] }
    path("/api/copy")  { action: [POST: "apiCopy"] }
    path("/api/move")  { action: [POST: "apiMove"] }
}

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
    def version   = "1.0.9"
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
    def requestedPage = params?.page != null ? safeInt(params.page, 1) : safeInt(state.currentPage, 1)
    state.currentPage = Math.max(1, requestedPage)
    def allFiles  = getFileList()
    def sortField = (settings.sortField  ?: "name").toString()
    def sortDir   = (settings.sortDir    ?: "asc").toString()
    def search    = (settings.searchText ?: "").toString()
    def filteredFiles = filterFiles(allFiles, search, sortField, sortDir)
    def pageSize  = getPageSize()
    def pageCount = Math.max(1, (int) Math.ceil((filteredFiles.size() ?: 0) / (pageSize as double)))
    def currentPage = Math.min(state.currentPage as int, pageCount)
    state.currentPage = currentPage
    def files     = paginateFiles(filteredFiles, currentPage, pageSize)
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

        // ── Search ─────────────────────────────────────────────────────
        section {
            input "searchText", "text",
                  title         : "&#128269; Search files\u2026",
                  required      : false,
                  submitOnChange: true,
                  width         : 8
            href url: "${getHubBaseUrl()}/apps/api/${app.id ?: ''}/ui",
                 title: "Open Web File Manager",
                 description: "Launch the on-hub JavaScript file manager",
                 style: "external",
                 required: false
        }

        // ── Finder-style file browser ──────────────────────────────────
        section("Files  /local/") {
            paragraph "<small style='color:#777;'>Search filters this list. Sorting and bulk actions live here in the File Manager view.</small>"
            href "mainPage",
                 title      : buildSortControlTitle("Name", "name", sortField, sortDir),
                 description: "",
                 params     : buildSortParams("name", sortField, sortDir),
                 width      : 2
            href "mainPage",
                 title      : buildSortControlTitle("Type", "mimeType", sortField, sortDir),
                 description: "",
                 params     : buildSortParams("mimeType", sortField, sortDir),
                 width      : 2
            href "mainPage",
                 title      : buildSortControlTitle("Size", "size", sortField, sortDir),
                 description: "",
                 params     : buildSortParams("size", sortField, sortDir),
                 width      : 2
            href "mainPage",
                 title      : buildSortControlTitle("Modified", "date", sortField, sortDir),
                 description: "",
                 params     : buildSortParams("date", sortField, sortDir),
                 width      : 2
            href "mainPage",
                 title      : sortDir == "asc" ? "Ascending" : "Descending",
                 description: "Current direction",
                 params     : [sortField: sortField, sortDir: toggleSortDir(sortDir), page: 1],
                 width      : 2
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
            input "selectedFiles", "enum",
                  title         : "Files On This Page",
                  options       : buildSelectionOptions(files),
                  multiple      : true,
                  required      : false,
                  submitOnChange: true
            paragraph buildFinderTable(files, selected, sortField, sortDir)
            paragraph buildPaginationBar(currentPage, pageCount, filteredFiles.size(), pageSize)
        }

        // ── Status bar ─────────────────────────────────────────────────
        section {
            paragraph "<small style='color:#777;'>" +
                      "${allFiles.size()} file(s) total &nbsp;&#183;&nbsp; " +
                      "${files.size()} shown on page ${currentPage} of ${pageCount} &nbsp;&#183;&nbsp; " +
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

    dynamicPage(name: "confirmDeletePage", title: "Confirm Delete  \u2022  v1.0.9",
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
                title: "${op.capitalize()} Files  \u2022  v1.0.9",
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
    dynamicPage(name: "settingsPage", title: "Settings  \u2022  v1.0.9",
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
                  title       : "Files per page",
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
            def files    = paginateFiles(filterFiles(allFiles,
                               (settings.searchText ?: "").toString(),
                               (settings.sortField  ?: "name").toString(),
                               (settings.sortDir    ?: "asc").toString()),
                               safeInt(state.currentPage, 1),
                               getPageSize())
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
    log.info "Hubitat Bulk File Manager v1.0.9 installed"
    initialize()
}

def updated() {
    log.info "Hubitat Bulk File Manager v1.0.9 updated"
    initialize()
}

def initialize() {
    state.lastResult   = null
    state.deleteResult = null
    state.opResult     = null
    state.pendingOp    = null
}

// ════════════════════════════════════════════════════════════════
//  WEB UI ENDPOINTS
// ════════════════════════════════════════════════════════════════

def renderWebUi() {
    render contentType: "text/html", data: buildWebUiHtml()
}

def apiConfig() {
    renderJson([
        version : "1.0.9",
        pageSize: getPageSize(),
        hubBase : getHubBaseUrl()
    ])
}

def apiFiles() {
    def files = getFileList()
    renderJson([
        files         : files,
        total         : files.size(),
        pageSize      : getPageSize(),
        fileListStatus: state.fileListStatus ?: [:]
    ])
}

def apiDelete() {
    def body = getRequestBodyMap()
    def files = normalizeRequestedFiles(body.files)
    def result = performDelete(files)
    if (!result.errors) {
        app.updateSetting("selectedFiles", [type: "enum", value: []])
    }
    renderJson([
        ok    : !result.errors,
        result: result
    ], result.errors ? 400 : 200)
}

def apiCopy() {
    def body = getRequestBodyMap()
    def files = normalizeRequestedFiles(body.files)
    def dest = (body.destination ?: "").toString().trim()
    def result = performCopy(files, dest)
    renderJson([
        ok    : !result.errors,
        result: result
    ], result.errors ? 400 : 200)
}

def apiMove() {
    def body = getRequestBodyMap()
    def files = normalizeRequestedFiles(body.files)
    def dest = (body.destination ?: "").toString().trim()
    def result = performMove(files, dest)
    if (!result.errors) {
        app.updateSetting("selectedFiles", [type: "enum", value: []])
    }
    renderJson([
        ok    : !result.errors,
        result: result
    ], result.errors ? 400 : 200)
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
    def filtered = allFiles.findAll { f ->
        if (!search?.trim()) return true
        (f.name ?: "").toLowerCase().contains(search.toLowerCase())
    }
    filtered.sort { a, b ->
        def av, bv
        switch (sortField) {
            case "mimeType": av = a.mimeType ?: "";         bv = b.mimeType ?: "";         break
            case "size": av = (a.size ?: 0L) as Long; bv = (b.size ?: 0L) as Long; break
            case "date": av = a.date ?: "";            bv = b.date ?: "";            break
            default:     av = a.name ?: "";            bv = b.name ?: "";            break
        }
        sortDir == "desc" ? bv <=> av : av <=> bv
    }
    return filtered
}

def paginateFiles(List files, int currentPage, int pageSize) {
    def safePage = Math.max(1, currentPage ?: 1)
    def safeSize = Math.max(10, pageSize ?: 200)
    def offset = (safePage - 1) * safeSize
    if (offset >= files.size()) return []
    return files.drop(offset).take(safeSize)
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
 * Sort indicators and selection state mirror the native controls above.
 */
def buildFinderTable(List files, List selectedFiles = [], String sortField = "name", String sortDir = "asc") {
    if (files.isEmpty()) {
        return """<div style="padding:30px;text-align:center;color:#aaa;font-size:13px;\
background:#fafafa;border-radius:6px;border:1px dashed #ddd;">
&#128194; No files found.</div>"""
    }

    def selectedSet = ((selectedFiles ?: []) as List).collect { it?.toString() }.findAll { it } as Set
    def nameCls  = sortField == "name" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""
    def typeCls  = sortField == "mimeType" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""
    def sizeCls  = sortField == "size" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""
    def dateCls  = sortField == "date" ? (sortDir == "asc" ? "sort-asc" : "sort-desc") : ""

    def sb = new StringBuilder()
    sb.append("""<style>
.fdr{width:100%;border-collapse:collapse;font-size:13px;\
font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,sans-serif;}
.fdr thead tr{background:#efefef;}
.fdr th{padding:7px 10px;text-align:left;border-bottom:2px solid #d4d4d4;\
font-weight:600;color:#333;white-space:nowrap;user-select:none;}
.fdr th a{color:#333;text-decoration:none;display:block;}
.fdr th a:hover{text-decoration:underline;}
.fdr th.sz{text-align:right;}
.fdr td{padding:5px 10px;border-bottom:1px solid #f0f0f0;vertical-align:middle;}
.fdr tr:hover td{background:#e8f0fe;}
.fdr .sz{text-align:right;font-family:monospace;font-size:12px;color:#555;}
.fdr .dt{color:#777;font-size:12px;}
.fdr .mt{color:#bbb;font-size:11px;}
.fdr .ck{width:34px;text-align:center;font-size:16px;}
.fdr .ic{width:30px;text-align:center;font-size:15px;}
.sort-asc::after{content:' \u25b2';font-size:10px;}
.sort-desc::after{content:' \u25bc';font-size:10px;}
</style>
<table class='fdr'>
<thead><tr>
  <th class='ck'>Sel</th>
  <th class='ic'></th>
  <th class='${nameCls}'>Name</th>
  <th class='${typeCls}'>Type</th>
  <th class='sz ${sizeCls}'>Size</th>
  <th class='${dateCls}'>Modified</th>
</tr></thead>
<tbody>""")

    files.eachWithIndex { f, i ->
        def bg = (i % 2 == 1) ? " style='background:#f9f9f9;'" : ""
        def selectionIcon = selectedSet.contains(f.name) ? "&#9745;" : "&#9744;"
        sb.append("""<tr${bg}>
<td class='ck'>${selectionIcon}</td>
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

def buildPaginationBar(int currentPage, int pageCount, int totalFiles, int pageSize) {
    if (pageCount <= 1) {
        return "<small style='color:#777;'>Showing all ${totalFiles} file(s).</small>"
    }
    def prevLink = currentPage > 1
        ? "<a href='?page=${currentPage - 1}'>&#8592; Previous</a>"
        : "<span style='color:#aaa;'>&#8592; Previous</span>"
    def nextLink = currentPage < pageCount
        ? "<a href='?page=${currentPage + 1}'>Next &#8594;</a>"
        : "<span style='color:#aaa;'>Next &#8594;</span>"
    def start = ((currentPage - 1) * pageSize) + 1
    def end   = Math.min(totalFiles, currentPage * pageSize)
    return "<div style='display:flex;justify-content:space-between;align-items:center;font-size:12px;color:#666;padding-top:6px;'>" +
           "<span>${prevLink}</span>" +
           "<span>Page ${currentPage} of ${pageCount} &nbsp;&#183;&nbsp; Showing ${start}-${end} of ${totalFiles}</span>" +
           "<span>${nextLink}</span>" +
           "</div>"
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

def buildSortControlTitle(String label, String field, String activeSortField = "name", String activeSortDir = "asc") {
    def active = activeSortField == field
    def indicator = active ? (activeSortDir == "asc" ? " ▲" : " ▼") : ""
    return "${label}${indicator}"
}

def buildSortParams(String field, String activeSortField = "name", String activeSortDir = "asc") {
    def nextDir = (activeSortField == field) ? toggleSortDir(activeSortDir) : "asc"
    return [sortField: field, sortDir: nextDir, page: 1]
}

def toggleSortDir(String sortDir = "asc") {
    return sortDir == "desc" ? "asc" : "desc"
}

def buildWebUiHtml() {
    def version = "1.0.9"
    def appId = app?.id ?: ""
    def basePath = "/apps/api/${appId}"
    return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Hubitat Bulk File Manager</title>
  <style>
    :root{--bg:#f3f4f6;--panel:#ffffff;--line:#d9dde3;--text:#17212b;--muted:#5f6b76;--accent:#2563eb;--accent-soft:#dbeafe;--danger:#c0392b;--good:#13795b;}
    *{box-sizing:border-box} body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,sans-serif;background:linear-gradient(180deg,#eef4ff 0%,#f7f8fa 45%,#eef2f7 100%);color:var(--text)}
    .shell{max-width:1280px;margin:0 auto;padding:24px}
    .hero,.panel{background:rgba(255,255,255,.92);backdrop-filter:blur(12px);border:1px solid rgba(217,221,227,.95);border-radius:18px;box-shadow:0 14px 40px rgba(15,23,42,.08)}
    .hero{padding:22px 24px 18px;margin-bottom:18px}
    .hero h1{margin:0 0 6px;font-size:28px}.hero p{margin:0;color:var(--muted)}
    .toolbar{display:grid;grid-template-columns:1.4fr repeat(6,minmax(0,1fr));gap:10px;align-items:end;padding:18px}
    .field label{display:block;font-size:12px;font-weight:700;color:var(--muted);margin-bottom:6px;text-transform:uppercase;letter-spacing:.04em}
    .field input,.field select{width:100%;padding:10px 12px;border:1px solid var(--line);border-radius:12px;background:#fff;color:var(--text);font-size:14px}
    .btnrow{display:flex;gap:8px;flex-wrap:wrap;padding:0 18px 18px}.btn{border:none;border-radius:12px;padding:10px 14px;font-size:14px;font-weight:700;cursor:pointer;background:#eef2f7;color:var(--text)}
    .btn.primary{background:var(--accent);color:#fff}.btn.danger{background:#fff0ef;color:var(--danger)} .btn:disabled{opacity:.45;cursor:not-allowed}
    .meta{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:0 18px 18px;color:var(--muted);font-size:13px}
    .status{margin:0 18px 18px;padding:12px 14px;border-radius:14px;font-size:14px;display:none}.status.show{display:block}.status.ok{background:#ecfdf3;color:var(--good);border:1px solid #b7ebcf}.status.err{background:#fff1f2;color:var(--danger);border:1px solid #f5c2c7}
    .tablewrap{overflow:auto;padding:0 18px 18px}.fdr{width:100%;border-collapse:collapse;min-width:820px}.fdr thead tr{background:#f7f8fa}.fdr th,.fdr td{padding:10px 12px;border-bottom:1px solid #edf0f4;text-align:left}
    .fdr th button{border:none;background:none;padding:0;font:inherit;font-weight:800;color:var(--text);cursor:pointer}.fdr th button.active{color:var(--accent)}
    .fdr tr:hover td{background:#f8fbff}.fdr .num{text-align:right;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#475569}.fdr .muted{color:#64748b}.fdr .check{width:36px}.empty{padding:32px;text-align:center;color:#94a3b8}
    .pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:0 18px 20px;color:var(--muted);font-size:13px}.pager .nav{display:flex;gap:8px}
    @media (max-width: 980px){.toolbar{grid-template-columns:1fr 1fr}.field.search{grid-column:1/-1}}
    @media (max-width: 640px){.shell{padding:14px}.toolbar{grid-template-columns:1fr}.field.search{grid-column:auto}.meta,.pager{flex-direction:column;align-items:flex-start}}
  </style>
</head>
<body>
  <div class="shell">
    <section class="hero">
      <h1>Hubitat Bulk File Manager</h1>
      <p>Web UI on Hubitat itself. Search filters instantly, sorting is client-side, and file actions call the app API only when needed. v${version}</p>
    </section>
    <section class="panel">
      <div class="toolbar">
        <div class="field search">
          <label for="search">Search</label>
          <input id="search" type="search" placeholder="Filter files by name">
        </div>
        <div class="field">
          <label for="pageSize">Rows</label>
          <select id="pageSize">
            <option>50</option><option>100</option><option>200</option><option>500</option><option>1000</option>
          </select>
        </div>
        <div class="field">
          <label>Sort</label>
          <input value="Use table headers" readonly>
        </div>
        <div class="field">
          <label>Selected</label>
          <input id="selectedCount" value="0 files" readonly>
        </div>
        <div class="field">
          <label>Total Size</label>
          <input id="selectedSize" value="0 B" readonly>
        </div>
        <div class="field">
          <label>Files</label>
          <input id="totalCount" value="0 files" readonly>
        </div>
        <div class="field">
          <label>Status</label>
          <input id="loadStatus" value="Loading..." readonly>
        </div>
      </div>
      <div class="btnrow">
        <button class="btn" id="selectPage">Select Page</button>
        <button class="btn" id="clearSelection">Clear</button>
        <button class="btn" id="refreshBtn">Refresh</button>
        <button class="btn primary" id="copyBtn" disabled>Copy</button>
        <button class="btn primary" id="moveBtn" disabled>Move</button>
        <button class="btn danger" id="deleteBtn" disabled>Delete</button>
      </div>
      <div class="meta">
        <span id="pageMeta">Loading files...</span>
        <span>All controls below live inside this file manager view.</span>
      </div>
      <div id="status" class="status"></div>
      <div class="tablewrap">
        <table class="fdr">
          <thead>
            <tr>
              <th class="check"><input id="checkAll" type="checkbox" aria-label="Select visible files"></th>
              <th></th>
              <th><button data-sort="name">Name</button></th>
              <th><button data-sort="mimeType">Type</button></th>
              <th class="num"><button data-sort="size">Size</button></th>
              <th><button data-sort="date">Modified</button></th>
            </tr>
          </thead>
          <tbody id="fileRows"><tr><td colspan="6" class="empty">Loading files...</td></tr></tbody>
        </table>
      </div>
      <div class="pager">
        <div class="nav">
          <button class="btn" id="prevBtn">Previous</button>
          <button class="btn" id="nextBtn">Next</button>
        </div>
        <div id="pagerMeta">Page 1</div>
      </div>
    </section>
  </div>
  <script>
    const apiBase = ${groovy.json.JsonOutput.toJson(basePath)};
    const state = { files: [], filtered: [], selected: new Set(), sortField: 'name', sortDir: 'asc', search: '', page: 1, pageSize: 200 };
    const el = {
      search: document.getElementById('search'),
      pageSize: document.getElementById('pageSize'),
      selectedCount: document.getElementById('selectedCount'),
      selectedSize: document.getElementById('selectedSize'),
      totalCount: document.getElementById('totalCount'),
      loadStatus: document.getElementById('loadStatus'),
      pageMeta: document.getElementById('pageMeta'),
      pagerMeta: document.getElementById('pagerMeta'),
      fileRows: document.getElementById('fileRows'),
      status: document.getElementById('status'),
      checkAll: document.getElementById('checkAll'),
      prevBtn: document.getElementById('prevBtn'),
      nextBtn: document.getElementById('nextBtn'),
      selectPage: document.getElementById('selectPage'),
      clearSelection: document.getElementById('clearSelection'),
      refreshBtn: document.getElementById('refreshBtn'),
      copyBtn: document.getElementById('copyBtn'),
      moveBtn: document.getElementById('moveBtn'),
      deleteBtn: document.getElementById('deleteBtn')
    };
    const sortButtons = Array.from(document.querySelectorAll('[data-sort]'));
    const iconFor = (mime) => {
      if (!mime) return '📄';
      if (mime.startsWith('image/')) return '🖼️';
      if (mime.startsWith('text/')) return '📝';
      if (mime.startsWith('audio/')) return '🎵';
      if (mime.startsWith('video/')) return '🎬';
      if (mime.includes('pdf')) return '📕';
      if (mime.includes('zip') || mime.includes('tar') || mime.includes('gzip') || mime.includes('archive')) return '📦';
      if (mime.includes('json') || mime.includes('xml') || mime.includes('javascript') || mime.includes('groovy')) return '⚙️';
      return '📄';
    };
    const formatSize = (bytes) => {
      const n = Number(bytes || 0);
      if (n < 1024) return n + ' B';
      if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
      if (n < 1073741824) return (n / 1048576).toFixed(1) + ' MB';
      return (n / 1073741824).toFixed(2) + ' GB';
    };
    const formatDate = (value) => {
      if (!value) return '—';
      const raw = String(value);
      const digitsOnly = raw.length > 0 && raw.split('').every(ch => ch >= '0' && ch <= '9');
      if (digitsOnly) {
        let epoch = Number(value);
        if (raw.length <= 10) epoch *= 1000;
        return new Date(epoch).toLocaleString();
      }
      const parsed = new Date(raw.replace(' ', 'T'));
      return Number.isNaN(parsed.getTime()) ? raw.slice(0, 16) : parsed.toLocaleString();
    };
    const compare = (a, b) => {
      const field = state.sortField;
      const dir = state.sortDir === 'desc' ? -1 : 1;
      const av = field === 'size' ? Number(a[field] || 0) : String(a[field] || '').toLowerCase();
      const bv = field === 'size' ? Number(b[field] || 0) : String(b[field] || '').toLowerCase();
      return av < bv ? -1 * dir : av > bv ? 1 * dir : 0;
    };
    const currentPageFiles = () => {
      const start = (state.page - 1) * state.pageSize;
      return state.filtered.slice(start, start + state.pageSize);
    };
    const selectedBytes = () => state.files.reduce((sum, file) => state.selected.has(file.name) ? sum + Number(file.size || 0) : sum, 0);
    const flash = (message, ok = true) => {
      el.status.textContent = message;
      el.status.className = 'status show ' + (ok ? 'ok' : 'err');
    };
    const syncSummary = () => {
      const totalPages = Math.max(1, Math.ceil(state.filtered.length / state.pageSize));
      const pageFiles = currentPageFiles();
      const start = pageFiles.length ? ((state.page - 1) * state.pageSize) + 1 : 0;
      const end = pageFiles.length ? start + pageFiles.length - 1 : 0;
      el.selectedCount.value = state.selected.size + ' file' + (state.selected.size === 1 ? '' : 's');
      el.selectedSize.value = formatSize(selectedBytes());
      el.totalCount.value = state.files.length + ' files';
      el.pageMeta.textContent = state.filtered.length + ' matching file(s). Showing ' + start + '-' + (end || 0) + '.';
      el.pagerMeta.textContent = 'Page ' + state.page + ' of ' + totalPages;
      el.prevBtn.disabled = state.page <= 1;
      el.nextBtn.disabled = state.page >= totalPages;
      const active = state.selected.size === 0;
      el.copyBtn.disabled = active;
      el.moveBtn.disabled = active;
      el.deleteBtn.disabled = active;
      el.checkAll.checked = pageFiles.length > 0 && pageFiles.every(file => state.selected.has(file.name));
      sortButtons.forEach(btn => {
        const activeSort = btn.dataset.sort === state.sortField;
        btn.classList.toggle('active', activeSort);
        btn.textContent = btn.dataset.sort === 'mimeType' ? 'Type' : btn.dataset.sort === 'date' ? 'Modified' : btn.dataset.sort.charAt(0).toUpperCase() + btn.dataset.sort.slice(1);
        if (activeSort) btn.textContent += state.sortDir === 'asc' ? ' ▲' : ' ▼';
      });
    };
    const renderRows = () => {
      const pageFiles = currentPageFiles();
      if (pageFiles.length === 0) {
        el.fileRows.innerHTML = '<tr><td colspan="6" class="empty">No files match the current filter.</td></tr>';
        syncSummary();
        return;
      }
      el.fileRows.innerHTML = pageFiles.map(file => {
        const checked = state.selected.has(file.name) ? 'checked' : '';
        return '<tr>' +
          '<td class="check"><input type="checkbox" data-file="' + encodeURIComponent(file.name) + '" ' + checked + '></td>' +
          '<td>' + iconFor(file.mimeType) + '</td>' +
          '<td>' + escapeHtml(file.name) + '</td>' +
          '<td class="muted">' + escapeHtml(file.mimeType || '—') + '</td>' +
          '<td class="num">' + formatSize(file.size || 0) + '</td>' +
          '<td class="muted">' + formatDate(file.date || '') + '</td>' +
        '</tr>';
      }).join('');
      el.fileRows.querySelectorAll('input[type="checkbox"][data-file]').forEach(box => {
        box.addEventListener('change', () => {
          const name = decodeURIComponent(box.dataset.file);
          if (box.checked) state.selected.add(name); else state.selected.delete(name);
          syncSummary();
        });
      });
      syncSummary();
    };
    const applyView = () => {
      const needle = state.search.trim().toLowerCase();
      state.filtered = state.files
        .filter(file => !needle || String(file.name || '').toLowerCase().includes(needle))
        .sort(compare);
      const totalPages = Math.max(1, Math.ceil(state.filtered.length / state.pageSize));
      state.page = Math.min(Math.max(1, state.page), totalPages);
      renderRows();
    };
    const callApi = async (path, body) => {
      const response = await fetch(apiBase + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await response.json();
      if (!response.ok || !data.ok) throw new Error(data?.result?.message || 'Request failed');
      flash(data.result.message, true);
      return data;
    };
    const refreshFiles = async (showBanner = false) => {
      const response = await fetch(apiBase + '/api/files');
      const data = await response.json();
      state.files = Array.isArray(data.files) ? data.files : [];
      state.selected = new Set([...state.selected].filter(name => state.files.some(file => file.name === name)));
      el.loadStatus.value = data.fileListStatus?.message || ('Loaded ' + state.files.length + ' files');
      if (data.pageSize) state.pageSize = Number(data.pageSize) || state.pageSize;
      el.pageSize.value = String(state.pageSize);
      applyView();
      if (showBanner) flash('Refreshed ' + state.files.length + ' file(s).', true);
    };
    const escapeHtml = (text) => String(text ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    el.search.addEventListener('input', () => { state.search = el.search.value; state.page = 1; applyView(); });
    el.pageSize.addEventListener('change', () => { state.pageSize = Number(el.pageSize.value) || 200; state.page = 1; applyView(); });
    el.prevBtn.addEventListener('click', () => { if (state.page > 1) { state.page -= 1; renderRows(); } });
    el.nextBtn.addEventListener('click', () => { const totalPages = Math.max(1, Math.ceil(state.filtered.length / state.pageSize)); if (state.page < totalPages) { state.page += 1; renderRows(); } });
    el.checkAll.addEventListener('change', () => { currentPageFiles().forEach(file => el.checkAll.checked ? state.selected.add(file.name) : state.selected.delete(file.name)); renderRows(); });
    el.selectPage.addEventListener('click', () => { currentPageFiles().forEach(file => state.selected.add(file.name)); renderRows(); });
    el.clearSelection.addEventListener('click', () => { state.selected.clear(); renderRows(); });
    el.refreshBtn.addEventListener('click', () => refreshFiles(true).catch(err => flash(err.message, false)));
    sortButtons.forEach(btn => btn.addEventListener('click', () => {
      const field = btn.dataset.sort;
      if (state.sortField === field) state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
      else { state.sortField = field; state.sortDir = 'asc'; }
      state.page = 1;
      applyView();
    }));
    el.copyBtn.addEventListener('click', async () => {
      const destination = prompt('Copy selected files to destination path or prefix:');
      if (destination === null) return;
      try { await callApi('/api/copy', { files: [...state.selected], destination }); } catch (err) { flash(err.message, false); }
    });
    el.moveBtn.addEventListener('click', async () => {
      const destination = prompt('Move selected files to destination path or prefix:');
      if (destination === null) return;
      try {
        await callApi('/api/move', { files: [...state.selected], destination });
        state.selected.clear();
        await refreshFiles();
      } catch (err) { flash(err.message, false); }
    });
    el.deleteBtn.addEventListener('click', async () => {
      if (!confirm('Delete ' + state.selected.size + ' selected file(s)? This cannot be undone.')) return;
      try {
        await callApi('/api/delete', { files: [...state.selected] });
        state.selected.clear();
        await refreshFiles();
      } catch (err) { flash(err.message, false); }
    });
    refreshFiles().catch(err => {
      el.loadStatus.value = 'Failed to load files';
      flash(err.message || 'Unable to load files.', false);
    });
  </script>
</body>
</html>"""
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
        if (dateStr ==~ /^\d+$/) {
            def epoch = dateStr as Long
            if (dateStr.length() <= 10) epoch *= 1000L
            def sdfEpoch = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            return sdfEpoch.format(new Date(epoch))
        }
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

def urlEncode(String s) {
    java.net.URLEncoder.encode(s ?: "", "UTF-8").replace("+", "%20")
}

def getPageSize() {
    def maxRaw = (settings.maxFiles ?: 200) as int
    return Math.max(10, Math.min(2000, maxRaw))
}

def safeInt(def raw, int fallback = 1) {
    try {
        return (raw ?: fallback) as int
    } catch (ignored) {
        return fallback
    }
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

def normalizeRequestedFiles(def rawFiles) {
    if (rawFiles instanceof List) {
        return rawFiles.collect { it?.toString()?.trim() }.findAll { it }
    }
    if (rawFiles instanceof String) {
        return rawFiles.split(",").collect { it?.trim() }.findAll { it }
    }
    return []
}

def getRequestBodyMap() {
    def json = request?.JSON
    if (json instanceof Map) return json
    if (json instanceof String && json.trim()) {
        try {
            return new groovy.json.JsonSlurper().parseText(json) as Map
        } catch (ignored) { }
    }
    return params instanceof Map ? params : [:]
}

def renderJson(Map payload, int status = 200) {
    render contentType: "application/json", status: status,
           data: groovy.json.JsonOutput.toJson(payload)
}
