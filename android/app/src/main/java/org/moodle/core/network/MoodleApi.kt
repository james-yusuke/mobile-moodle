package org.moodle.core.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface MoodleApi {
    @Headers("Cache-Control: no-store")
    @POST
    suspend fun publicConfig(
        @Url url: String,
        @Body requests: List<AjaxRequest>,
    ): List<AjaxResponse<PublicConfigDto>>

    @Headers("Cache-Control: no-store")
    @FormUrlEncoded
    @POST
    suspend fun loginToken(
        @Url url: String,
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("service") service: String = "moodle_mobile_app",
    ): LoginTokenDto

    @Headers("Cache-Control: no-store")
    @FormUrlEncoded
    @POST
    suspend fun restCall(
        @Url url: String,
        @FieldMap(encoded = false) fields: Map<String, String>,
    ): JsonElement

    @Headers("Cache-Control: no-store")
    @Multipart
    @POST
    suspend fun uploadFile(
        @Url url: String,
        @Part("token") token: RequestBody,
        @Part("filearea") fileArea: RequestBody,
        @Part("itemid") itemId: RequestBody,
        @Part file: MultipartBody.Part,
    ): JsonElement
}

data class AjaxRequest(
    val index: Int = 0,
    val methodname: String,
    val args: Map<String, String> = emptyMap(),
)

data class AjaxResponse<T>(
    val error: Boolean = false,
    val data: T? = null,
    val exception: MoodleExceptionDto? = null,
)

data class PublicConfigDto(
    val wwwroot: String = "",
    val httpswwwroot: String = "",
    val sitename: String = "",
    val enablemobilewebservice: Int = 0,
    val typeoflogin: Int = 1,
    val launchurl: String? = null,
    val showloginform: Int = 1,
)

data class LoginTokenDto(
    val token: String? = null,
    @SerializedName("privatetoken") val privateToken: String? = null,
    val error: String? = null,
    val errorcode: String? = null,
)

data class MoodleExceptionDto(
    val errorcode: String? = null,
    val message: String? = null,
    val debuginfo: String? = null,
)
