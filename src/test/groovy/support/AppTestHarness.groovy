package support

/**
 * AppTestHarness
 *
 * Simulates the Hubitat Groovy sandbox so that the app script can be
 * loaded and its methods called without a live hub.
 *
 * Usage:
 *   def harness = new AppTestHarness()
 *   def app     = harness.loadApp()          // returns parsed Script
 *   app.formatSize(1024L)                    // call any app method directly
 *
 * Controlling mock behaviour:
 *   harness.settings['sortField'] = 'size'  // pre-set a setting
 *   harness.state['currentPath']  = 'docs/' // pre-set state
 *   harness.fileListResponse = [...]        // stub the file-list JSON
 *   harness.deleteHubFileShouldFail = true  // force sandbox delete to throw
 */
class AppTestHarness {

    // ── Simulated Hubitat runtime state ─────────────────────────────────────
    Map  state    = [:]
    Map  settings = [:]
    Map  params   = [:]
    def  requestJson = null

    List capturedLog = []
    Map  rendered = [:]

    // ── Captured side-effects ────────────────────────────────────────────────
    List uploadedFiles = []   // [name: String, size: int]
    List deletedFiles  = []   // String filenames

    // ── Mock tuning ──────────────────────────────────────────────────────────

    /** Raw file-list returned by GET /hub/fileManager/json (list OR map with fileList). */
    def fileListResponse = [fileList: []]

    /** Force the built-in deleteHubFile to throw, so the HTTP fallback is exercised. */
    boolean deleteHubFileShouldFail = false

    /** Force the built-in downloadHubFile to throw, so the HTTP fallback is exercised. */
    boolean downloadHubFileShouldFail = false

    /**
     * Per-filename download responses.
     * Key  = filename (String)
     * Value = byte[] content (or null to simulate a missing file)
     */
    Map downloadResponses = [:]

    /**
     * HTTP GET stub responses keyed by URI string.
     * Value must be an object with .status (int) and .data (Object) properties.
     */
    Map httpGetResponses = [:]

    /**
     * HTTP POST stub responses keyed by URI string.
     * Value must be an object with .status (int) and .data (Object) properties.
     */
    Map httpPostResponses = [:]

    /** If non-null, uploadHubFile will throw this message instead of succeeding. */
    String uploadHubFileErrorMessage = null

    // ── Script loader ────────────────────────────────────────────────────────

    /**
     * Parse the app Groovy script with a fully-mocked Hubitat binding and
     * return the Script object.  Individual methods can be called directly
     * on the returned script without ever calling run().
     */
    Script loadApp() {
        def harness   = this
        def settingsMap = this.settings
        def stateMap    = this.state

        def binding = new Binding()

        // Core runtime objects
        binding.settings = settingsMap
        binding.state    = stateMap
        binding.params   = this.params

        // Logger — captures to capturedLog list
        binding.log = [
            debug: { msg -> harness.capturedLog << [level: 'DEBUG', msg: msg?.toString()] },
            info:  { msg -> harness.capturedLog << [level: 'INFO',  msg: msg?.toString()] },
            warn:  { msg -> harness.capturedLog << [level: 'WARN',  msg: msg?.toString()] },
            error: { msg -> harness.capturedLog << [level: 'ERROR', msg: msg?.toString()] }
        ]

        // Hub location
        binding.location = [hub: [localIP: '192.168.1.100']]

        // App object — updateSetting writes into the shared settingsMap
        binding.app = [
            id: 12345,
            updateSetting: { String name, Map valueMap ->
                settingsMap[name] = valueMap.value
            }
        ]
        binding.request = [JSON: this.requestJson]

        // ── Hubitat DSL stubs (no-ops so the script parses without error) ────
        binding.definition  = { Map m -> }
        binding.mappings    = { Closure c -> }
        binding.preferences = { Closure c -> /* intentionally empty — do not execute */ }
        binding.render      = { Map m ->
            harness.rendered = m
            return m.data ?: m.text
        }

        // ── Hubitat file sandbox methods ─────────────────────────────────────
        binding.uploadHubFile = { String name, byte[] data ->
            if (harness.uploadHubFileErrorMessage) {
                throw new Exception(harness.uploadHubFileErrorMessage)
            }
            harness.uploadedFiles << [name: name, size: data?.length ?: 0]
        }

        binding.deleteHubFile = { String name ->
            if (harness.deleteHubFileShouldFail) {
                throw new Exception("Mock: deleteHubFile disabled")
            }
            harness.deletedFiles << name
        }

        binding.downloadHubFile = { String name ->
            if (harness.downloadHubFileShouldFail) {
                throw new Exception("Mock: downloadHubFile disabled")
            }
            if (!harness.downloadResponses.containsKey(name)) {
                throw new Exception("Mock: no download stub for '${name}'")
            }
            def content = harness.downloadResponses[name]
            if (content == null) throw new Exception("Mock: file '${name}' is null/missing")
            return content
        }

        // ── HTTP client stubs ─────────────────────────────────────────────────
        binding.httpGet = { Map reqParams, Closure callback ->
            def uri  = reqParams?.uri?.toString()
            def resp = harness.httpGetResponses[uri]
            if (resp != null) callback(resp)
            else throw new Exception("Mock: no httpGet stub for '${uri}'")
        }

        binding.httpPost = { Map reqParams, Closure callback ->
            def uri  = reqParams?.uri?.toString()
            def resp = harness.httpPostResponses[uri]
            if (resp != null) callback(resp)
            else throw new Exception("Mock: no httpPost stub for '${uri}'")
        }

        binding.httpPostJson = { Map reqParams, Closure callback ->
            def uri  = reqParams?.uri?.toString()
            def resp = harness.httpPostResponses[uri]
            if (resp != null) callback(resp)
            else throw new Exception("Mock: no httpPostJson stub for '${uri}'")
        }

        // Parse — does NOT call run(), so definition() and preferences {} are never executed
        def appPath = System.getProperty('app.source.path',
                          'HubitatBulkFileManager.groovy')
        def shell = new GroovyShell(getClass().classLoader, binding)
        return shell.parse(new File(appPath))
    }

    // ── Convenience helpers for building stub responses ───────────────────────

    /** Stub a successful file-list GET response using the current fileListResponse. */
    void stubFileListOk() {
        httpGetResponses['http://192.168.1.100/hub/fileManager/json'] = [
            status: 200,
            data  : fileListResponse
        ]
    }

    /** Stub a failing file-list GET response (HTTP 403). */
    void stubFileListFail(int status = 403) {
        httpGetResponses['http://192.168.1.100/hub/fileManager/json'] = [
            status: status,
            data  : null
        ]
    }

    /** Stub a successful HTTP delete POST response. */
    void stubHttpDeleteOk() {
        httpPostResponses['http://192.168.1.100/hub/fileManager/delete'] = [
            status: 200,
            data  : [result: 'ok']
        ]
    }

    /** Stub a failing HTTP delete POST response. */
    void stubHttpDeleteFail(int status = 500) {
        httpPostResponses['http://192.168.1.100/hub/fileManager/delete'] = [
            status: status,
            data  : null
        ]
    }

    /** Build a synthetic file-list entry. */
    static Map fileEntry(String name, long size = 1024L, String date = '2026-01-15 10:00:00') {
        [name: name, size: size, date: date]
    }

    /** Returns log entries at a given level. */
    List logsAt(String level) {
        capturedLog.findAll { it.level == level }
    }
}
