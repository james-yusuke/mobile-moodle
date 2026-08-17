package org.moodle.data

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.moodle.core.network.AjaxRequest
import org.moodle.core.network.MoodleApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MoodleApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MoodleApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(MoodleApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `reads public mobile configuration`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """[{"error":false,"data":{"wwwroot":"https://example.edu","httpswwwroot":"https://example.edu","sitename":"Campus","enablemobilewebservice":0,"typeoflogin":1,"showloginform":1}}]""",
            ).build(),
        )
        val result = api.publicConfig(
            server.url("/lib/ajax/service-nologin.php").toString(),
            listOf(AjaxRequest(methodname = "tool_mobile_get_public_config")),
        ).single()
        assertFalse(result.error)
        assertEquals(0, result.data?.enablemobilewebservice)
        assertEquals("tool_mobile_get_public_config", server.takeRequest().body!!.utf8().let {
            Gson().fromJson(it, Array<AjaxRequest>::class.java).single().methodname
        })
    }

    @Test
    fun `posts credentials in form body rather than URL`() = runTest {
        server.enqueue(MockResponse.Builder().body("{\"error\":\"No\",\"errorcode\":\"invalidlogin\"}").build())
        api.loginToken(server.url("/login/token.php").toString(), "student", "secret")
        val request = server.takeRequest()
        assertFalse(request.url.toString().contains("student"))
        assertFalse(request.url.toString().contains("secret"))
        assertEquals("POST", request.method)
    }
}
