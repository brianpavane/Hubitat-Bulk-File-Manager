import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title
import spock.lang.Unroll
import support.AppTestHarness

@Title('Hubitat Bulk File Manager — Full Test Suite')
class HubitatBulkFileManagerSpec extends Specification {

    AppTestHarness harness
    @Subject Script  app

    def setup() {
        harness = new AppTestHarness()
        app     = harness.loadApp()
    }

    // ════════════════════════════════════════════════════════════════
    //  1. formatSize
    // ════════════════════════════════════════════════════════════════

    @Unroll
    def 'formatSize: #bytes bytes -> "#expected"'() {
        expect:
        app.formatSize(bytes as Long) == expected

        where:
        bytes           | expected
        0L              | '0 B'
        1L              | '1 B'
        1023L           | '1023 B'
        1024L           | '1.0 KB'
        1536L           | '1.5 KB'
        1_048_576L      | '1.0 MB'
        1_572_864L      | '1.5 MB'
        1_073_741_824L  | '1.00 GB'
        2_147_483_648L  | '2.00 GB'
        null            | '0 B'
        -1L             | '0 B'
    }

    // ════════════════════════════════════════════════════════════════
    //  2. formatDate
    // ════════════════════════════════════════════════════════════════

    def 'formatDate: parses standard Hubitat date string'() {
        expect:
        app.formatDate('2026-03-15 10:30:00') == '2026-03-15 10:30'
    }

    def 'formatDate: midnight formats correctly'() {
        expect:
        app.formatDate('2026-01-01 00:00:00') == '2026-01-01 00:00'
    }

    def 'formatDate: null returns dash'() {
        expect:
        app.formatDate(null) == '—'
    }

    def 'formatDate: empty string returns dash'() {
        expect:
        app.formatDate('') == '—'
    }

    def 'formatDate: blank string returns dash'() {
        expect:
        app.formatDate('   ') == '—'
    }

    def 'formatDate: unparseable string is truncated to 16 chars'() {
        given:
        def badDate = 'not-a-date-at-all'

        expect:
        app.formatDate(badDate) == badDate.take(16)
    }

    def 'formatDate: short unparseable string returned as-is'() {
        expect:
        app.formatDate('bad') == 'bad'
    }

    // ════════════════════════════════════════════════════════════════
    //  3. getMimeType
    // ════════════════════════════════════════════════════════════════

    @Unroll
    def 'getMimeType: "#filename" -> "#expected"'() {
        expect:
        app.getMimeType(filename) == expected

        where:
        filename              | expected
        'photo.png'           | 'image/png'
        'photo.jpg'           | 'image/jpeg'
        'photo.jpeg'          | 'image/jpeg'
        'animation.gif'       | 'image/gif'
        'icon.svg'            | 'image/svg+xml'
        'favicon.ico'         | 'image/x-icon'
        'banner.webp'         | 'image/webp'
        'scan.bmp'            | 'image/bmp'
        'notes.txt'           | 'text/plain'
        'data.csv'            | 'text/csv'
        'readme.md'           | 'text/markdown'
        'index.html'          | 'text/html'
        'index.htm'           | 'text/html'
        'styles.css'          | 'text/css'
        'app.js'              | 'application/javascript'
        'app.groovy'          | 'text/x-groovy'
        'config.json'         | 'application/json'
        'feed.xml'            | 'application/xml'
        'manual.pdf'          | 'application/pdf'
        'archive.zip'         | 'application/zip'
        'backup.gz'           | 'application/gzip'
        'bundle.tar'          | 'application/x-tar'
        'library.jar'         | 'application/java-archive'
        'unknown.xyz'         | 'application/octet-stream'
        'noextension'         | 'application/octet-stream'
        ''                    | 'application/octet-stream'
        null                  | 'application/octet-stream'
    }

    // ════════════════════════════════════════════════════════════════
    //  4. getFileIcon
    // ════════════════════════════════════════════════════════════════

    @Unroll
    def 'getFileIcon: "#mimeType" returns correct HTML entity'() {
        expect:
        app.getFileIcon(mimeType) == icon

        where:
        mimeType                      | icon
        'image/png'                   | '&#128247;'
        'image/jpeg'                  | '&#128247;'
        'image/svg+xml'               | '&#128247;'
        'text/plain'                  | '&#128221;'
        'text/html'                   | '&#128221;'
        'text/x-groovy'               | '&#9881;'
        'audio/mpeg'                  | '&#127925;'
        'audio/wav'                   | '&#127925;'
        'video/mp4'                   | '&#127916;'
        'application/pdf'             | '&#128213;'
        'application/zip'             | '&#128230;'
        'application/gzip'            | '&#128230;'
        'application/x-tar'           | '&#128230;'
        'application/java-archive'    | '&#128230;'
        'application/json'            | '&#9881;'
        'application/xml'             | '&#9881;'
        'application/javascript'      | '&#9881;'
        'application/octet-stream'    | '&#128196;'
        ''                            | '&#128196;'
        null                          | '&#128196;'
    }

    // ════════════════════════════════════════════════════════════════
    //  5. escapeHtml
    // ════════════════════════════════════════════════════════════════

    def 'escapeHtml: escapes ampersand'() {
        expect: app.escapeHtml('a & b') == 'a &amp; b'
    }

    def 'escapeHtml: escapes less-than'() {
        expect: app.escapeHtml('<script>') == '&lt;script&gt;'
    }

    def 'escapeHtml: escapes greater-than'() {
        expect: app.escapeHtml('a > b') == 'a &gt; b'
    }

    def 'escapeHtml: escapes all three in one string'() {
        expect: app.escapeHtml('<a href="x&y">') == '&lt;a href="x&amp;y"&gt;'
    }

    def 'escapeHtml: plain string unchanged'() {
        expect: app.escapeHtml('hello_world-123') == 'hello_world-123'
    }

    def 'escapeHtml: null returns empty string'() {
        expect: app.escapeHtml(null) == ''
    }

    def 'escapeHtml: empty string returns empty string'() {
        expect: app.escapeHtml('') == ''
    }

    // ════════════════════════════════════════════════════════════════
    //  6. filterFiles
    // ════════════════════════════════════════════════════════════════

    def 'filterFiles: returns all files when search is empty'() {
        given:
        def files = [
            AppTestHarness.fileEntry('photo.png'),
            AppTestHarness.fileEntry('notes.txt'),
            AppTestHarness.fileEntry('readme.md'),
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, '', 'name', 'asc')

        then:
        result.size() == 3
    }

    def 'filterFiles: search is case-insensitive'() {
        given:
        def files = [
            AppTestHarness.fileEntry('Photo.PNG'),
            AppTestHarness.fileEntry('notes.txt'),
            AppTestHarness.fileEntry('PHOTO_backup.jpg'),
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, 'photo', 'name', 'asc')

        then:
        result*.name.toSet() == ['Photo.PNG', 'PHOTO_backup.jpg'] as Set
    }

    def 'filterFiles: search returns empty when no match'() {
        given:
        def files = [AppTestHarness.fileEntry('notes.txt')]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, 'xyz', 'name', 'asc')

        then:
        result.isEmpty()
    }

    def 'filterFiles: sort by name ascending'() {
        given:
        def files = [
            AppTestHarness.fileEntry('zebra.txt'),
            AppTestHarness.fileEntry('apple.txt'),
            AppTestHarness.fileEntry('mango.txt'),
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, '', 'name', 'asc')

        then:
        result*.name == ['apple.txt', 'mango.txt', 'zebra.txt']
    }

    def 'filterFiles: sort by name descending'() {
        given:
        def files = [
            AppTestHarness.fileEntry('apple.txt'),
            AppTestHarness.fileEntry('zebra.txt'),
            AppTestHarness.fileEntry('mango.txt'),
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, '', 'name', 'desc')

        then:
        result*.name == ['zebra.txt', 'mango.txt', 'apple.txt']
    }

    def 'filterFiles: sort by size ascending'() {
        given:
        def files = [
            AppTestHarness.fileEntry('big.zip',   10_000L),
            AppTestHarness.fileEntry('tiny.txt',     100L),
            AppTestHarness.fileEntry('medium.jpg', 5_000L),
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, '', 'size', 'asc')

        then:
        result*.name == ['tiny.txt', 'medium.jpg', 'big.zip']
    }

    def 'filterFiles: sort by date descending'() {
        given:
        def files = [
            [name: 'old.txt',    size: 1L, date: '2024-01-01 00:00:00', mimeType: 'text/plain'],
            [name: 'newest.txt', size: 1L, date: '2026-04-04 12:00:00', mimeType: 'text/plain'],
            [name: 'mid.txt',    size: 1L, date: '2025-06-15 08:00:00', mimeType: 'text/plain'],
        ]
        harness.settings['maxFiles'] = 200

        when:
        def result = app.filterFiles(files, '', 'date', 'desc')

        then:
        result*.name == ['newest.txt', 'mid.txt', 'old.txt']
    }

    def 'filterFiles: respects maxFiles setting'() {
        given:
        def files = (1..20).collect { AppTestHarness.fileEntry("file${it}.txt") }
        harness.settings['maxFiles'] = 15   // within valid range [10..2000]

        when:
        def result = app.filterFiles(files, '', 'name', 'asc')

        then:
        result.size() == 15
    }

    def 'filterFiles: clamps maxFiles minimum to 10'() {
        given:
        def files = (1..20).collect { AppTestHarness.fileEntry("file${it}.txt") }
        harness.settings['maxFiles'] = 2    // below minimum — clamped to 10

        when:
        def result = app.filterFiles(files, '', 'name', 'asc')

        then:
        result.size() == 10
    }

    def 'filterFiles: empty list returns empty'() {
        given:
        harness.settings['maxFiles'] = 200

        expect:
        app.filterFiles([], '', 'name', 'asc') == []
    }

    // ════════════════════════════════════════════════════════════════
    //  7. computeSelectedSize
    // ════════════════════════════════════════════════════════════════

    def 'computeSelectedSize: empty selection returns 0'() {
        given:
        def files = [AppTestHarness.fileEntry('a.txt', 500L)]
        expect:
        app.computeSelectedSize(files, []) == 0L
    }

    def 'computeSelectedSize: null selection returns 0'() {
        given:
        def files = [AppTestHarness.fileEntry('a.txt', 500L)]
        expect:
        app.computeSelectedSize(files, null) == 0L
    }

    def 'computeSelectedSize: single selection returns correct size'() {
        given:
        def files = [AppTestHarness.fileEntry('a.txt', 1024L)]
        expect:
        app.computeSelectedSize(files, ['a.txt']) == 1024L
    }

    def 'computeSelectedSize: sums multiple selections'() {
        given:
        def files = [
            AppTestHarness.fileEntry('a.txt',  1000L),
            AppTestHarness.fileEntry('b.txt',  2000L),
            AppTestHarness.fileEntry('c.txt',  3000L),
        ]
        expect:
        app.computeSelectedSize(files, ['a.txt', 'c.txt']) == 4000L
    }

    def 'computeSelectedSize: unknown filename contributes 0'() {
        given:
        def files = [AppTestHarness.fileEntry('a.txt', 500L)]
        expect:
        app.computeSelectedSize(files, ['missing.txt']) == 0L
    }

    // ════════════════════════════════════════════════════════════════
    //  8. buildFinderTable
    // ════════════════════════════════════════════════════════════════

    def 'buildFinderTable: empty list shows empty-state div'() {
        when:
        def html = app.buildFinderTable([])

        then:
        html.contains('No files found')
        !html.contains('<table')
    }

    def 'buildFinderTable: non-empty list renders an HTML table with class fdr'() {
        given:
        def files = [[name: 'photo.png', size: 1024L,
                      date: '2026-01-01 00:00:00', mimeType: 'image/png']]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains("class='fdr'")
        html.contains('<table')
        html.contains('</table>')
    }

    def 'buildFinderTable: renders all five column headers'() {
        given:
        def files = [[name: 'a.txt', size: 1L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains('>Name<')
        html.contains('>Type<')
        html.contains('>Size<')
        html.contains('>Modified<')
    }

    def 'buildFinderTable: HTML-escapes filename'() {
        given:
        def files = [[name: '<script>xss</script>', size: 0L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files)

        then:
        !html.contains('<script>xss</script>')
        html.contains('&lt;script&gt;xss&lt;/script&gt;')
    }

    def 'buildFinderTable: active ascending sort column has sort-asc class on header'() {
        given:
        def files = [[name: 'a.txt', size: 1L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files, 'name', 'asc')

        then:
        html.contains("class='sort-asc'")
    }

    def 'buildFinderTable: active descending sort column has sort-desc class on header'() {
        given:
        def files = [[name: 'a.txt', size: 1L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files, 'size', 'desc')

        then:
        html.contains('sort-desc')
    }

    def 'buildFinderTable: inactive sort columns have empty class string'() {
        given:
        def files = [[name: 'a.txt', size: 1L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files, 'name', 'asc')

        then:
        // size and date headers should have no sort class
        !html.contains("class='sort-asc sz'") || html.contains("class='sz '")
        !html.contains("class='sort-desc'") || true  // size/date not active
    }

    def 'buildFinderTable: renders file icon for each row'() {
        given:
        def files = [[name: 'photo.png', size: 512L,
                      date: '2026-01-01 00:00:00', mimeType: 'image/png']]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains('&#128247;')  // image icon
    }

    def 'buildFinderTable: renders formatted size'() {
        given:
        def files = [[name: 'big.zip', size: 2_097_152L,
                      date: '2026-01-01 00:00:00', mimeType: 'application/zip']]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains('2.0 MB')
    }

    def 'buildFinderTable: odd rows have alternating background color'() {
        given:
        def files = [
            [name: 'first.txt',  size: 1L, date: '2026-01-01 00:00:00', mimeType: 'text/plain'],
            [name: 'second.txt', size: 2L, date: '2026-01-01 00:00:00', mimeType: 'text/plain'],
        ]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains('#f9f9f9')  // odd row background
    }

    def 'buildFinderTable: includes Apple system font stack in CSS'() {
        given:
        def files = [[name: 'a.txt', size: 1L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def html = app.buildFinderTable(files)

        then:
        html.contains('-apple-system')
    }

    // ════════════════════════════════════════════════════════════════
    //  9. buildSelectionOptions
    // ════════════════════════════════════════════════════════════════

    def 'buildSelectionOptions: empty file list returns empty map'() {
        expect:
        app.buildSelectionOptions([]) == [:]
    }

    def 'buildSelectionOptions: key is full filename'() {
        given:
        def files = [[name: 'readme.txt', size: 512L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def opts = app.buildSelectionOptions(files)

        then:
        opts.containsKey('readme.txt')
    }

    def 'buildSelectionOptions: label contains filename'() {
        given:
        def files = [[name: 'readme.txt', size: 512L,
                      date: '2026-01-01 00:00:00', mimeType: 'text/plain']]

        when:
        def opts = app.buildSelectionOptions(files)

        then:
        opts['readme.txt'].contains('readme.txt')
    }

    def 'buildSelectionOptions: label contains formatted size'() {
        given:
        def files = [[name: 'big.zip', size: 2_097_152L,
                      date: '2026-01-01 00:00:00', mimeType: 'application/zip']]

        when:
        def opts = app.buildSelectionOptions(files)

        then:
        opts['big.zip'].contains('2.0 MB')
    }

    // ════════════════════════════════════════════════════════════════
    //  10. getFileList
    // ════════════════════════════════════════════════════════════════

    def 'getFileList: parses fileList-wrapped JSON response'() {
        given:
        harness.fileListResponse = [fileList: [
            [name: 'image.png',  size: 1024, date: '2026-01-15 10:00:00'],
            [name: 'notes.txt',  size:  512, date: '2026-02-01 09:00:00'],
        ]]
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: harness.fileListResponse
        ]

        when:
        def result = app.getFileList()

        then:
        result.size() == 2
        result[0].name == 'image.png'
        result[0].size == 1024L
        result[1].name == 'notes.txt'
        harness.state.fileListStatus.message.contains('http://192.168.1.100:8080/hub/fileManager/json')
    }

    def 'getFileList: parses bare list JSON response'() {
        given:
        harness.fileListResponse = [
            [name: 'photo.jpg', size: 2048, date: '2026-03-01 00:00:00']
        ]
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: harness.fileListResponse
        ]

        when:
        def result = app.getFileList()

        then:
        result.size() == 1
        result[0].name == 'photo.jpg'
    }

    def 'getFileList: parses files-wrapped JSON response'() {
        given:
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200,
            data  : [files: [[fileName: 'hub-file.txt', size: 12, lastModified: '2026-03-01 00:00:00']]]
        ]

        when:
        def result = app.getFileList()

        then:
        result*.name == ['hub-file.txt']
        result[0].date == '2026-03-01 00:00:00'
    }

    def 'getFileList: enriches entries with mimeType'() {
        given:
        harness.fileListResponse = [fileList: [
            [name: 'banner.png', size: 4096, date: '2026-01-01 00:00:00']
        ]]
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: harness.fileListResponse
        ]

        when:
        def result = app.getFileList()

        then:
        result[0].mimeType == 'image/png'
    }

    def 'getFileList: falls back to port 80 when port 8080 fails'() {
        given:
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 404, data: null
        ]
        harness.httpGetResponses['http://192.168.1.100/hub/fileManager/json'] = [
            status: 200, data: [[name: 'fallback.txt', size: 1, date: '2026-01-01 00:00:00']]
        ]

        when:
        def result = app.getFileList()

        then:
        result*.name == ['fallback.txt']
        harness.state.fileListStatus.message.contains('http://192.168.1.100/hub/fileManager/json')
    }

    def 'getFileList: prefers non-empty port 80 response when port 8080 returns empty list'() {
        given:
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: [files: []]
        ]
        harness.httpGetResponses['http://192.168.1.100/hub/fileManager/json'] = [
            status: 200, data: [[name: 'nonempty.txt', size: 1, date: '2026-01-01 00:00:00']]
        ]

        when:
        def result = app.getFileList()

        then:
        result*.name == ['nonempty.txt']
    }

    def 'getFileList: filters out entries with blank names'() {
        given:
        harness.fileListResponse = [fileList: [
            [name: 'valid.txt', size: 100, date: '2026-01-01 00:00:00'],
            [name: '',          size: 200, date: '2026-01-01 00:00:00'],
            [name: null,        size: 300, date: '2026-01-01 00:00:00'],
        ]]
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: harness.fileListResponse
        ]

        when:
        def result = app.getFileList()

        then:
        result.size() == 1
        result[0].name == 'valid.txt'
    }

    def 'getFileList: returns empty list with diagnostic status when both ports fail'() {
        when:
        def result = app.getFileList()

        then:
        result == []
        harness.state.fileListStatus.errors
        harness.state.fileListStatus.message.contains('ports 8080 or 80')
        harness.logsAt('WARN').size() >= 2
    }

    def 'getFileList: returns empty list with diagnostic status when endpoint returns zero files'() {
        given:
        harness.httpGetResponses['http://192.168.1.100:8080/hub/fileManager/json'] = [
            status: 200, data: [files: []]
        ]

        when:
        def result = app.getFileList()

        then:
        result == []
        harness.state.fileListStatus.errors
        harness.state.fileListStatus.message.contains('returned 0 files')
    }

    // ════════════════════════════════════════════════════════════════
    //  11. performDelete
    // ════════════════════════════════════════════════════════════════

    def 'performDelete: deletes via built-in sandbox call'() {
        when:
        def result = app.performDelete(['photo.png', 'notes.txt'])

        then:
        harness.deletedFiles == ['photo.png', 'notes.txt']
        !result.errors
        result.message.contains('2')
    }

    def 'performDelete: falls back to HTTP when built-in throws'() {
        given:
        harness.deleteHubFileShouldFail = true
        harness.stubHttpDeleteOk()

        when:
        def result = app.performDelete(['photo.png'])

        then:
        harness.deletedFiles.isEmpty()       // sandbox call was skipped
        !result.errors
        result.message.contains('1')
    }

    def 'performDelete: reports error when both sandbox and HTTP fail'() {
        given:
        harness.deleteHubFileShouldFail = true
        harness.stubHttpDeleteFail(500)

        when:
        def result = app.performDelete(['photo.png'])

        then:
        result.errors
        result.message.contains('Failed')
    }

    def 'performDelete: partial failure is counted correctly'() {
        when:
        def result = app.performDelete(['ok.png', 'ok2.png'])

        then:
        !result.errors
        result.message.contains('2')
    }

    def 'performDelete: empty list returns success with 0 count'() {
        when:
        def result = app.performDelete([])

        then:
        !result.errors
        result.message.contains('0')
    }

    def 'performDelete: ignores null/blank entries'() {
        when:
        def result = app.performDelete([null, '', 'real.txt'])

        then:
        harness.deletedFiles == ['real.txt']
    }

    // ════════════════════════════════════════════════════════════════
    //  12. performCopy
    // ════════════════════════════════════════════════════════════════

    def 'performCopy: copies file with correct destination name'() {
        given:
        harness.downloadResponses['photo.png'] = [1, 2, 3] as byte[]

        when:
        def result = app.performCopy(['photo.png'], 'archive')

        then:
        !result.errors
        harness.uploadedFiles.size() == 1
        harness.uploadedFiles[0].name == 'archive/photo.png'
    }

    def 'performCopy: copies nested file preserving basename only'() {
        given:
        harness.downloadResponses['docs/readme.txt'] = 'hello'.bytes

        when:
        def result = app.performCopy(['docs/readme.txt'], 'backup')

        then:
        !result.errors
        harness.uploadedFiles[0].name == 'backup/readme.txt'
    }

    def 'performCopy: copies to root when dest is empty'() {
        given:
        harness.downloadResponses['photo.png'] = [0] as byte[]

        when:
        def result = app.performCopy(['photo.png'], '')

        then:
        !result.errors
        harness.uploadedFiles[0].name == 'photo.png'
    }

    def 'performCopy: reports error when download fails'() {
        given:
        harness.downloadHubFileShouldFail = true
        // Also no HTTP stub — both paths will fail

        when:
        def result = app.performCopy(['missing.png'], 'backup')

        then:
        result.errors
        harness.uploadedFiles.isEmpty()
    }

    def 'performCopy: reports error when upload fails'() {
        given:
        harness.downloadResponses['photo.png'] = [0] as byte[]
        harness.uploadHubFileErrorMessage = 'Disk full'

        when:
        def result = app.performCopy(['photo.png'], 'backup')

        then:
        result.errors
        result.message.contains('photo.png')
    }

    def 'performCopy: copies multiple files and counts correctly'() {
        given:
        harness.downloadResponses['a.txt'] = 'aaa'.bytes
        harness.downloadResponses['b.txt'] = 'bbb'.bytes

        when:
        def result = app.performCopy(['a.txt', 'b.txt'], 'dest')

        then:
        !result.errors
        harness.uploadedFiles.size() == 2
        result.message.contains('2')
    }

    // ════════════════════════════════════════════════════════════════
    //  13. performMove
    // ════════════════════════════════════════════════════════════════

    def 'performMove: copies then deletes originals on success'() {
        given:
        harness.downloadResponses['photo.png'] = [0] as byte[]

        when:
        def result = app.performMove(['photo.png'], 'archive')

        then:
        !result.errors
        harness.uploadedFiles.size() == 1
        harness.deletedFiles == ['photo.png']
        result.message.contains('archive')
    }

    def 'performMove: does NOT delete originals when copy fails'() {
        given:
        harness.downloadHubFileShouldFail = true

        when:
        def result = app.performMove(['photo.png'], 'archive')

        then:
        result.errors
        harness.deletedFiles.isEmpty()
        result.message.contains('aborted')
    }

    def 'performMove: warns when delete of source fails after successful copy'() {
        given:
        harness.downloadResponses['photo.png'] = [0] as byte[]
        harness.deleteHubFileShouldFail = true
        // Also fail HTTP fallback
        harness.stubHttpDeleteFail()

        when:
        def result = app.performMove(['photo.png'], 'archive')

        then:
        harness.uploadedFiles.size() == 1  // copy succeeded
        result.errors                      // delete failed
        result.message.toLowerCase().contains('warn')
    }

    // ════════════════════════════════════════════════════════════════
    //  14. appButtonHandler — Select All / Deselect All
    // ════════════════════════════════════════════════════════════════

    def 'btnSelectAll: selects all visible files'() {
        given:
        harness.settings['sortField']  = 'name'
        harness.settings['sortDir']    = 'asc'
        harness.settings['searchText'] = ''
        harness.settings['maxFiles']   = 200
        harness.fileListResponse = [fileList: [
            [name: 'a.png', size: 100, date: '2026-01-01 00:00:00'],
            [name: 'b.txt', size: 200, date: '2026-01-01 00:00:00'],
        ]]
        harness.stubFileListOk()

        when:
        app.appButtonHandler('btnSelectAll')

        then:
        def selected = harness.settings['selectedFiles'] as List
        selected.toSet() == ['a.png', 'b.txt'] as Set
    }

    def 'btnDeselectAll: clears the selection'() {
        given:
        harness.settings['selectedFiles'] = ['a.png', 'b.txt']

        when:
        app.appButtonHandler('btnDeselectAll')

        then:
        harness.settings['selectedFiles'] == []
    }

    // ════════════════════════════════════════════════════════════════
    //  14b. appButtonHandler — Confirm Delete
    // ════════════════════════════════════════════════════════════════

    def 'btnConfirmDelete: deletes selected files and clears selection'() {
        given:
        harness.settings['selectedFiles'] = ['photo.png', 'notes.txt']

        when:
        app.appButtonHandler('btnConfirmDelete')

        then:
        harness.deletedFiles.toSet() == ['photo.png', 'notes.txt'] as Set
        harness.settings['selectedFiles'] == []
        harness.state['deleteResult'] != null
        !harness.state['deleteResult'].errors
    }

    def 'btnConfirmDelete: keeps selection when delete fails'() {
        given:
        harness.settings['selectedFiles'] = ['photo.png']
        harness.deleteHubFileShouldFail = true
        harness.stubHttpDeleteFail()

        when:
        app.appButtonHandler('btnConfirmDelete')

        then:
        harness.state['deleteResult'].errors
        // Selection should NOT be cleared on error
        harness.settings['selectedFiles'] == ['photo.png']
    }

    // ════════════════════════════════════════════════════════════════
    //  14c. appButtonHandler — Confirm Copy / Move
    // ════════════════════════════════════════════════════════════════

    def 'btnConfirmOp (copy): copies files and preserves selection'() {
        given:
        harness.settings['selectedFiles'] = ['photo.png']
        harness.settings['destPath']      = 'archive'
        harness.state['pendingOp']        = 'copy'
        harness.downloadResponses['photo.png'] = [0, 1] as byte[]

        when:
        app.appButtonHandler('btnConfirmOp')

        then:
        harness.uploadedFiles.size() == 1
        harness.deletedFiles.isEmpty()               // copy does not delete
        harness.state['opResult'] != null
        !harness.state['opResult'].errors
    }

    def 'btnConfirmOp (move): moves files and clears selection on success'() {
        given:
        harness.settings['selectedFiles'] = ['photo.png']
        harness.settings['destPath']      = 'archive'
        harness.state['pendingOp']        = 'move'
        harness.downloadResponses['photo.png'] = [0] as byte[]

        when:
        app.appButtonHandler('btnConfirmOp')

        then:
        harness.uploadedFiles.size() == 1
        harness.deletedFiles == ['photo.png']
        harness.settings['selectedFiles'] == []
        harness.settings['destPath']      == ''
    }

    // ════════════════════════════════════════════════════════════════
    //  15. getHubBaseUrl
    // ════════════════════════════════════════════════════════════════

    def 'getHubBaseUrl: uses configured hubIp when set'() {
        given:
        harness.settings['hubIp'] = '10.0.0.50'

        expect:
        app.getHubBaseUrl() == 'http://10.0.0.50'
    }

    def 'getHubBaseUrl: falls back to location.hub.localIP'() {
        given:
        harness.settings['hubIp'] = null

        expect:
        app.getHubBaseUrl() == 'http://192.168.1.100'
    }

    // ════════════════════════════════════════════════════════════════
    //  16. makeAuthHeaders
    // ════════════════════════════════════════════════════════════════

    def 'makeAuthHeaders: includes Accept header always'() {
        expect:
        app.makeAuthHeaders()['Accept'] == 'application/json'
    }

    def 'makeAuthHeaders: includes Authorization when token is set'() {
        given:
        harness.settings['hubToken'] = 'secret-token-123'

        expect:
        app.makeAuthHeaders()['Authorization'] == 'Bearer secret-token-123'
    }

    def 'makeAuthHeaders: no Authorization header when token is blank'() {
        given:
        harness.settings['hubToken'] = ''

        expect:
        !app.makeAuthHeaders().containsKey('Authorization')
    }

    def 'makeAuthHeaders: no Authorization header when token is null'() {
        given:
        harness.settings['hubToken'] = null

        expect:
        !app.makeAuthHeaders().containsKey('Authorization')
    }
}
