package org.moodle.core.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import org.moodle.core.model.HtmlFeature
import org.moodle.core.model.HtmlThemeFamily
import org.moodle.core.model.MoodleAssignment
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleFile
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.MoodleModuleContent
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.MoodleSection
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

data class HtmlLoginForm(val actionUrl: String, val hiddenFields: Map<String, String>)

data class HtmlSiteIdentity(
    val siteName: String,
    val fullName: String?,
    val moodleVersion: String?,
    val themeFamily: HtmlThemeFamily,
    val sesskey: String?,
    val userId: Long?,
    val features: Set<HtmlFeature>,
)

@Singleton
class MoodleHtmlParser @Inject constructor() {
    private val safeHtml = Safelist.relaxed()
        .addTags("section", "article", "figure", "figcaption")
        .addAttributes("a", "title")
        .addAttributes("img", "alt", "title")
        .addProtocols("a", "href", "https")
        .addProtocols("img", "src", "https")
    private val safeMessageHtml = Safelist.none()
        .addTags("p", "br", "strong", "em", "b", "i", "code", "a")
        .addAttributes("a", "href", "title")
        .addProtocols("a", "href", "https")

    fun document(html: String, url: String): Document = Jsoup.parse(html, url)

    fun loginForm(document: Document): HtmlLoginForm? {
        val form = document.selectFirst("form#login, form.login-form, form[action*=login]") ?: return null
        if (form.selectFirst("input[type=password][name=password]") == null) return null
        val action = form.absUrl("action").ifBlank { document.location() }
        val hidden = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }
        return HtmlLoginForm(action, hidden)
    }

    fun isLoginPage(document: Document): Boolean = loginForm(document) != null ||
        document.body().id() == "page-login-index"

    fun identity(document: Document, fallbackSiteName: String): HtmlSiteIdentity {
        val bodyClasses = document.body().classNames()
        val modern = bodyClasses.any { it.contains("boost", true) } ||
            document.selectFirst("[data-region=drawer], [data-region=moremenu]") != null
        val legacy = document.selectFirst("#page-navbar, .block_navigation") != null
        val family = when {
            modern -> HtmlThemeFamily.Modern
            legacy -> HtmlThemeFamily.Legacy
            else -> HtmlThemeFamily.StructuralFallback
        }
        val siteName = document.selectFirst(".navbar-brand, .site-name, header .logo")?.text()?.trim()
            .orEmpty().ifBlank { document.title().substringBefore("|").substringBefore(":").trim() }
            .ifBlank { fallbackSiteName }
        val fullName = document.selectFirst(".usertext, [data-region=user-menu] .usertext, .usermenu .userbutton")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val scripts = document.select("script").joinToString("\n") { it.data() + it.html() }
        val sesskey = listOf(
            Regex("[\\\"']?sesskey[\\\"']?\\s*[:=]\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE),
            Regex("M\\.cfg\\.sesskey\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { it.find(scripts)?.groupValues?.getOrNull(1) }
        val userId = listOf(
            Regex("[\\\"']?userid[\\\"']?\\s*[:=]\\s*[\\\"']?(\\d+)", RegexOption.IGNORE_CASE),
            Regex("M\\.cfg\\.userid\\s*=\\s*[\\\"']?(\\d+)", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { it.find(scripts)?.groupValues?.getOrNull(1)?.toLongOrNull() }
            ?: document.selectFirst("[data-userid]")?.attr("data-userid")?.toLongOrNull()
        val version = Regex("Moodle(?:\\s+|/)([0-9]+(?:\\.[0-9]+){1,2})", RegexOption.IGNORE_CASE)
            .find(document.text() + scripts)?.groupValues?.getOrNull(1)
        return HtmlSiteIdentity(
            siteName,
            fullName,
            version,
            family,
            sesskey,
            userId,
            setOf(
                HtmlFeature.Courses,
                HtmlFeature.Contents,
                HtmlFeature.AssignmentsRead,
                HtmlFeature.Grades,
                HtmlFeature.Calendar,
                HtmlFeature.Notifications,
                HtmlFeature.Files,
                HtmlFeature.MessagesRead,
                HtmlFeature.MessagesSearch,
                HtmlFeature.MessagesSend,
                HtmlFeature.MessagesMarkRead,
            ),
        )
    }

    fun courses(document: Document): List<MoodleCourse> {
        val rows = linkedMapOf<Long, MoodleCourse>()
        document.select("a[href*=/course/view.php]").forEach { anchor ->
            val url = anchor.absUrl("href")
            val id = queryLong(url, "id") ?: return@forEach
            if (id <= 1) return@forEach
            val title = anchor.attr("data-course-name").ifBlank { anchor.attr("title") }.ifBlank { anchor.text() }.trim()
            if (title.isBlank()) return@forEach
            val current = rows[id]
            if (current == null || title.length > current.fullName.length) {
                rows[id] = MoodleCourse(id, title, title, "", null, null)
            }
        }
        return rows.values.toList()
    }

    fun courseSections(document: Document, courseId: Long): List<MoodleSection> {
        val sectionElements = document.select("[data-sectionid], li.section, .course-section")
            .filter { element -> element.selectFirst("a[href*=/mod/][href*=view.php]") != null || element.hasAttr("data-sectionid") }
        val result = sectionElements.mapIndexed { index, section ->
            val id = section.attr("data-sectionid").toLongOrNull()
                ?: Regex("(?:section-|section-)(\\d+)").find(section.id())?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: stableId("$courseId:section:$index")
            val name = section.selectFirst(".sectionname, .section-title, h2, h3, h4")?.text()?.trim()
                .orEmpty().ifBlank { "Section ${index + 1}" }
            val summary = section.selectFirst(".summary, [data-region=section-content]")?.html().orEmpty()
            MoodleSection(id, courseId, name, sanitize(summary), index, modules(section))
        }
        if (result.isNotEmpty()) return result.distinctBy { it.id }
        val modules = modules(document)
        return if (modules.isEmpty()) emptyList() else listOf(
            MoodleSection(stableId("$courseId:section:0"), courseId, document.selectFirst("h1")?.text() ?: "Course", "", 0, modules),
        )
    }

    fun assignments(document: Document, courseId: Long): List<MoodleAssignment> = modules(document)
        .filter { it.moduleType == "assign" }
        .map { module ->
            MoodleAssignment(
                id = module.instanceId ?: module.id,
                courseId = courseId,
                courseModuleId = module.id,
                name = module.name,
                introHtml = module.descriptionHtml,
                dueDate = null,
                cutoffDate = null,
                allowsOnlineText = false,
                allowsFiles = false,
                requiresSubmitButton = false,
            )
        }

    fun grades(document: Document, courseId: Long): List<MoodleGrade> = document.select("table tbody tr, .gradeitem")
        .mapIndexedNotNull { index, row ->
            val name = row.selectFirst(".itemname, th, [data-region=grade-item-name]")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val cells = row.select("td").map { it.text().trim() }
            val grade = row.selectFirst(".grade, .column-grade, [data-region=grade]")?.text()?.trim()
                ?: cells.getOrNull(0).orEmpty()
            val range = row.selectFirst(".range, .column-range")?.text()?.trim().orEmpty()
            val percentage = row.selectFirst(".percentage, .column-percentage")?.text()?.trim().orEmpty()
            val id = row.attr("data-itemid").toLongOrNull() ?: stableId("$courseId:grade:$index:$name")
            MoodleGrade(courseId, id, name, grade, range, percentage)
        }

    fun events(document: Document): List<MoodleCalendarEvent> = document
        .select("[data-event-id], [data-eventid], .calendar_event, .event")
        .mapIndexedNotNull { index, item ->
            val name = item.selectFirst(".name, .eventname, h3, h4, a")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val url = item.selectFirst("a[href]")?.absUrl("href")?.takeIf { it.isNotBlank() }
            val timestamp = item.attr("data-timestamp").toLongOrNull()
                ?: item.selectFirst("time[data-timestamp]")?.attr("data-timestamp")?.toLongOrNull()
                ?: 0L
            val id = item.attr("data-event-id").ifBlank { item.attr("data-eventid") }.toLongOrNull()
                ?: queryLong(url.orEmpty(), "eventid")
                ?: stableId("event:$index:$name:$timestamp:${url.orEmpty()}")
            val courseId = queryLong(url.orEmpty(), "course") ?: queryLong(url.orEmpty(), "courseid")
            val description = item.selectFirst(".description, [data-region=event-description]")?.html().orEmpty()
            MoodleCalendarEvent(id, name, sanitize(description), timestamp, courseId, url)
        }.distinctBy { it.id }

    fun notifications(document: Document): List<MoodleNotification> = document
        .select("[data-notification-id], [data-region=notification-content], .notification")
        .mapIndexedNotNull { index, item ->
            val subject = item.selectFirst(".subject, [data-region=notification-subject], h3, h4")?.text()?.trim()
                ?: item.text().trim().take(160)
            if (subject.isBlank()) return@mapIndexedNotNull null
            val url = item.selectFirst("a[href]")?.absUrl("href")?.takeIf { it.isNotBlank() }
            val created = item.attr("data-timestamp").toLongOrNull()
                ?: item.selectFirst("time[data-timestamp]")?.attr("data-timestamp")?.toLongOrNull()
                ?: 0L
            val id = item.attr("data-notification-id").toLongOrNull()
                ?: stableId("notification:$index:$subject:$created:${url.orEmpty()}")
            MoodleNotification(id, subject, sanitize(item.html()), created, item.hasClass("read"), url)
        }.distinctBy { it.id }

    fun moduleContent(document: Document, fallbackTitle: String, originalUrl: String): MoodleModuleContent {
        val main = document.selectFirst("[role=main], #region-main, #page-content") ?: document.body()
        val title = document.selectFirst("[role=main] h1, #region-main h1, .page-header-headings h1, h1")
            ?.text()?.trim().orEmpty().ifBlank { fallbackTitle }
        val content = main.selectFirst(
            ".activity-description, .resourcecontent, .foldertree, .generalbox, .box:not(.activity-navigation)",
        ) ?: main
        val files = main.select("a[href*=pluginfile.php], a[href*='forcedownload=1']")
            .mapNotNull { anchor ->
                val url = anchor.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MoodleFile(anchor.text().trim().ifBlank { url.substringAfterLast('/') }, url, null, null)
            }
            .distinctBy { it.url }
        return MoodleModuleContent(title, sanitize(content.html()), files, originalUrl)
    }

    fun sanitize(html: String): String = Jsoup.clean(html, "", safeHtml, Document.OutputSettings().prettyPrint(false))

    fun sanitizeMessage(html: String): String =
        Jsoup.clean(html, "", safeMessageHtml, Document.OutputSettings().prettyPrint(false))

    private fun modules(root: Element): List<MoodleModule> {
        val result = linkedMapOf<Long, MoodleModule>()
        root.select("a[href*=/mod/][href*=view.php]").forEach { anchor ->
            val url = anchor.absUrl("href")
            val id = queryLong(url, "id") ?: return@forEach
            val type = Regex("/mod/([^/]+)/view\\.php").find(url)?.groupValues?.getOrNull(1) ?: return@forEach
            val container = anchor.closest("li.activity, .activity, [data-for=cmitem]") ?: anchor.parent()
            val name = anchor.selectFirst(".instancename")?.ownText()?.trim().orEmpty()
                .ifBlank { anchor.attr("title") }.ifBlank { anchor.text() }.trim()
            if (name.isBlank()) return@forEach
            val description = container?.selectFirst(".contentafterlink, .description")?.html().orEmpty()
            val files = container?.select("a[href*=pluginfile.php]")?.map { fileAnchor ->
                MoodleFile(fileAnchor.text().trim().ifBlank { "file" }, fileAnchor.absUrl("href"), null, null)
            }.orEmpty()
            result[id] = MoodleModule(id, null, name, type, sanitize(description), url, files)
        }
        return result.values.toList()
    }

    private fun queryLong(url: String, name: String): Long? = runCatching {
        URI(url).rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
            val pair = part.split('=', limit = 2)
            pair.takeIf { it[0] == name }?.getOrNull(1)?.toLongOrNull()
        }
    }.getOrNull()

    private fun stableId(value: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        var result = 0L
        repeat(8) { result = (result shl 8) or (bytes[it].toLong() and 0xff) }
        return result.absoluteValue.takeIf { it > 0 } ?: 1L
    }
}
