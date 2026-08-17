package org.moodle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moodle.core.html.MoodleHtmlParser
import org.moodle.core.model.HtmlThemeFamily

class MoodleHtmlParserTest {
    private val parser = MoodleHtmlParser()

    @Test
    fun `parses login token and same form fields`() {
        val document = parser.document(
            """
            <html><body id="page-login-index"><form id="login" action="/moodle/login/index.php" method="post">
              <input type="hidden" name="logintoken" value="one-time-token">
              <input id="username" name="username"><input id="password" name="password" type="password">
            </form></body></html>
            """.trimIndent(),
            "https://example.edu/moodle/login/index.php",
        )
        val form = parser.loginForm(document)
        assertNotNull(form)
        assertEquals("https://example.edu/moodle/login/index.php", form?.actionUrl)
        assertEquals("one-time-token", form?.hiddenFields?.get("logintoken"))
        assertTrue(parser.isLoginPage(document))
    }

    @Test
    fun `parses modern course sections and modules without localized selectors`() {
        val document = parser.document(
            """
            <html><body class="theme_boost"><nav data-region="drawer"><span class="usertext">Student Name</span></nav>
              <a href="/moodle/course/view.php?id=42" data-course-name="Algorithms">Algorithms</a>
              <section data-sectionid="7"><h3 class="sectionname">Week 1</h3>
                <div class="activity" data-for="cmitem"><a href="/moodle/mod/page/view.php?id=91"><span class="instancename">Overview</span></a></div>
                <div class="activity"><a href="/moodle/mod/assign/view.php?id=92">Essay</a></div>
              </section>
            </body></html>
            """.trimIndent(),
            "https://example.edu/moodle/my/",
        )
        val identity = parser.identity(document, "Example")
        assertEquals(HtmlThemeFamily.Modern, identity.themeFamily)
        assertEquals("Student Name", identity.fullName)
        assertEquals("Algorithms", parser.courses(document).single().fullName)
        val section = parser.courseSections(document, 42).single()
        assertEquals(7, section.id)
        assertEquals(listOf("page", "assign"), section.modules.map { it.moduleType })
    }

    @Test
    fun `legacy and structural pages use fallback adapters`() {
        val legacy = parser.document(
            "<html><body><div id='page-navbar'></div><div class='block_navigation'></div></body></html>",
            "https://example.edu/",
        )
        assertEquals(HtmlThemeFamily.Legacy, parser.identity(legacy, "Campus").themeFamily)
        val fallback = parser.document("<html><body><main>Campus</main></body></html>", "https://example.edu/")
        assertEquals(HtmlThemeFamily.StructuralFallback, parser.identity(fallback, "Campus").themeFamily)
    }

    @Test
    fun `sanitizer removes executable and unsafe content`() {
        val clean = parser.sanitize(
            "<p onclick='steal()'>Hello <strong>student</strong></p><script>steal()</script>" +
                "<a href='javascript:steal()'>bad</a><iframe src='https://other.example'></iframe>",
        )
        assertTrue(clean.contains("Hello"))
        assertTrue(clean.contains("<strong>"))
        assertFalse(clean.contains("onclick"))
        assertFalse(clean.contains("script"))
        assertFalse(clean.contains("iframe"))
        assertFalse(clean.contains("javascript:"))
    }

    @Test
    fun `module page becomes native sanitized content and downloadable files`() {
        val page = parser.document(
            """
            <html><body><main role="main"><h1>Lecture notes</h1>
              <div class="generalbox"><p onclick="bad()">Week one <strong>overview</strong></p></div>
              <a href="/moodle/pluginfile.php/42/notes.pdf?forcedownload=1">Notes PDF</a>
            </main></body></html>
            """.trimIndent(),
            "https://example.edu/moodle/mod/page/view.php?id=9",
        )

        val content = parser.moduleContent(page, "Fallback", page.location())

        assertEquals("Lecture notes", content.title)
        assertTrue(content.bodyHtml.contains("Week one"))
        assertFalse(content.bodyHtml.contains("onclick"))
        assertEquals("Notes PDF", content.files.single().name)
        assertTrue(content.files.single().url.startsWith("https://example.edu/moodle/pluginfile.php"))
    }

    @Test
    fun `non login page has no login form`() {
        val page = parser.document("<html><body id='page-my-index'>Dashboard</body></html>", "https://example.edu/my/")
        assertNull(parser.loginForm(page))
        assertFalse(parser.isLoginPage(page))
    }

    @Test
    fun `identity extracts message session context without retaining credentials`() {
        val page = parser.document(
            """<html><body class="theme_boost" data-userid="481">
                <script>M.cfg = {"sesskey":"session-only", "userid":481};</script>
            </body></html>""",
            "https://example.edu/my/",
        )

        val identity = parser.identity(page, "Campus")

        assertEquals("session-only", identity.sesskey)
        assertEquals(481L, identity.userId)
        assertTrue(identity.features.contains(org.moodle.core.model.HtmlFeature.MessagesRead))
    }

    @Test
    fun `message sanitizer keeps safe links but removes images and executable markup`() {
        val clean = parser.sanitizeMessage(
            "<p>Hello <a href='https://example.edu/help'>help</a></p>" +
                "<img src='https://example.edu/tracker.png'><script>bad()</script>",
        )

        assertTrue(clean.contains("https://example.edu/help"))
        assertFalse(clean.contains("img"))
        assertFalse(clean.contains("script"))
    }
}
