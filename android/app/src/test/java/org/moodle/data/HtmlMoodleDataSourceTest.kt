package org.moodle.data

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.moodle.core.html.DefaultHtmlMoodleDataSource
import org.moodle.core.html.HtmlCookieStorage
import org.moodle.core.html.HtmlMoodleException
import org.moodle.core.html.HtmlSessionClientFactory
import org.moodle.core.html.MoodleHtmlParser
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.MoodlePublicConfig
import org.moodle.core.model.SiteAccount
import java.net.URLDecoder

class HtmlMoodleDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: DefaultHtmlMoodleDataSource
    private lateinit var storage: MemoryCookieStorage
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()
        baseUrl = server.url("/moodle").toString().trimEnd('/')
        storage = MemoryCookieStorage()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        dataSource = DefaultHtmlMoodleDataSource(
            HtmlSessionClientFactory(client, storage, Gson()),
            MoodleHtmlParser(),
            Gson(),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `login posts hidden token, stores secure cookie, and parses courses`() = runTest {
        server.dispatcher = MoodleDispatcher()

        val login = dataSource.login("account-a", config(), "student", "correct horse")
        assertEquals("Student Name", login.identity.fullName)
        assertTrue(storage.read("account-a").orEmpty().contains("MoodleSession"))
        assertFalse(storage.read("account-a").orEmpty().contains("correct horse"))

        val courses = dataSource.courses(account("account-a"))
        assertEquals(listOf("Algorithms"), courses.map { it.fullName })

        val posted = server.requestCount
        assertTrue(posted >= 4)
    }

    @Test
    fun `cookies are isolated by account and a missing session requires reauthentication`() = runTest {
        server.dispatcher = MoodleDispatcher()
        dataSource.login("account-a", config(), "student", "correct horse")

        val error = runCatching { dataSource.courses(account("account-b")) }.exceptionOrNull()
        assertTrue(error is HtmlMoodleException)
        assertEquals("session_expired", (error as HtmlMoodleException).code)
        assertEquals(null, storage.read("account-b"))
    }

    @Test
    fun `Moodle 39 falls back from missing my courses page to dashboard`() = runTest {
        server.dispatcher = MoodleDispatcher(modernCoursesPageAvailable = false)
        dataSource.login("account-a", config(), "student", "correct horse")

        val courses = dataSource.courses(account("account-a"))

        assertEquals(listOf("Legacy course"), courses.map { it.fullName })
    }

    @Test
    fun `cross origin redirects are rejected`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(302)
            .addHeader("Location", "https://outside.example/login")
                .build(),
        )

        val error = runCatching {
            dataSource.login("account-a", config(), "student", "password")
        }.exceptionOrNull()
        assertTrue(error is HtmlMoodleException)
        assertEquals("cross_origin_blocked", (error as HtmlMoodleException).code)
    }

    @Test
    fun `message ajax uses authenticated same site session and maps conversations`() = runTest {
        server.dispatcher = MoodleDispatcher()
        val login = dataSource.login("account-a", config(), "student", "correct horse")
        val account = account("account-a").copy(userId = login.identity.userId)

        val conversations = dataSource.conversations(account)
        val messages = dataSource.conversationMessages(account, conversations.single().id)
        val users = dataSource.searchMessageUsers(account, "Ada")

        assertEquals("Ada Lovelace", conversations.single().name)
        assertEquals("Welcome", messages.single().bodyText)
        assertEquals("Ada Lovelace", users.single().fullName)
    }

    private fun config() = MoodlePublicConfig(baseUrl, "Campus", false, 1, null, true)

    private fun account(id: String) = SiteAccount(
        id = id,
        baseUrl = baseUrl,
        siteName = "Campus",
        username = "student",
        userId = null,
        fullName = "Student Name",
        connectionMode = ConnectionMode.NativeHtml,
    )

    private inner class MoodleDispatcher(
        private val modernCoursesPageAvailable: Boolean = true,
    ) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            return when {
                path == "/moodle/login/index.php" && request.method == "GET" -> html(loginPage())
                path == "/moodle/login/index.php" && request.method == "POST" -> {
                    val body = URLDecoder.decode(request.body?.utf8().orEmpty(), Charsets.UTF_8.name())
                    if (!body.contains("logintoken=one-time") ||
                        !body.contains("username=student") ||
                        !body.contains("password=correct horse")
                    ) {
                        html(loginPage("Invalid login"))
                    } else {
                        MockResponse.Builder()
                            .code(303)
                            .addHeader("Location", "/moodle/my/")
                            .addHeader("Set-Cookie", "MoodleSession=session-a; Path=/moodle; Secure; HttpOnly")
                            .build()
                    }
                }
                path == "/moodle/my/" && hasSession(request) -> html(dashboard(!modernCoursesPageAvailable))
                path == "/moodle/my/courses.php" && hasSession(request) && modernCoursesPageAvailable -> html(coursesPage())
                path == "/moodle/my/courses.php" && hasSession(request) -> MockResponse.Builder().code(404).build()
                path == "/moodle/lib/ajax/service.php" && hasSession(request) -> messageAjax(request)
                else -> html(loginPage())
            }
        }

        private fun hasSession(request: RecordedRequest): Boolean =
            request.headers["Cookie"].orEmpty().contains("MoodleSession=session-a")

        private fun messageAjax(request: RecordedRequest): MockResponse {
            val body = request.body?.utf8().orEmpty()
            val data = when {
                body.contains("core_message_get_conversations") ->
                    """{"conversations":[{"id":91,"type":1,"name":"","timemodified":20,"unreadcount":1,"members":[{"id":123,"fullname":"Student Name","canmessage":true},{"id":456,"fullname":"Ada Lovelace","canmessage":true}],"messages":[{"id":701,"useridfrom":456,"text":"Welcome","timecreated":20}]}]}"""
                body.contains("core_message_get_conversation_messages") ->
                    """{"messages":[{"id":701,"conversationid":91,"useridfrom":456,"userfullname":"Ada Lovelace","text":"Welcome","timecreated":20}]}"""
                body.contains("core_message_message_search_users") ->
                    """{"contacts":[],"noncontacts":[{"id":456,"fullname":"Ada Lovelace","canmessage":true}]}"""
                else -> "true"
            }
            return MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""[{"error":false,"data":$data}]""")
                .build()
        }
    }

    private fun loginPage(error: String = "") = """
        <html><body id="page-login-index">
          <div class="loginerrors">$error</div>
          <form id="login" action="/moodle/login/index.php" method="post">
            <input type="hidden" name="logintoken" value="one-time">
            <input name="username"><input name="password" type="password">
          </form>
        </body></html>
    """.trimIndent()

    private fun dashboard(includeCourse: Boolean = false) = """
        <html><head><title>Campus</title></head><body class="theme_boost">
          <nav data-region="drawer"><span class="usertext">Student Name</span></nav>
          <script>M.cfg = {sesskey: "safe-session-key", userid: 123};</script>
          ${if (includeCourse) "<a href='/moodle/course/view.php?id=77'>Legacy course</a>" else ""}
        </body></html>
    """.trimIndent()

    private fun coursesPage() = """
        <html><body class="theme_boost">
          <a href="/moodle/course/view.php?id=42" data-course-name="Algorithms">Algorithms</a>
        </body></html>
    """.trimIndent()

    private fun html(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .body(body)
        .build()

    private class MemoryCookieStorage : HtmlCookieStorage {
        private val values = mutableMapOf<String, String>()
        override fun read(accountId: String): String? = values[accountId]
        override fun write(accountId: String, serializedCookies: String) {
            values[accountId] = serializedCookies
        }
    }
}
