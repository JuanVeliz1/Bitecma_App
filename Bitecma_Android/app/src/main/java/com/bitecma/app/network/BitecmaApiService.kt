package com.bitecma.app.network

import com.bitecma.app.BuildConfig
import com.bitecma.app.data.AppState
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

data class ApiEnvelope<T>(
    val ok: Boolean? = false,
    val data: T? = null,
    val error: String? = null
)

data class PingResponse(
    val ok: Boolean? = false,
    val service: String? = null
)

data class AuthLoginRequest(
    val correo: String,
    val password: String
)

data class AuthUser(
    val uid: Int? = null,
    val id: Int? = null,
    val correo: String? = null,
    val email: String? = null,
    val nombre: String? = null,
    val numero: String? = null,
    val rol: String? = null,
    val activo: Boolean? = null,
    val avatar_url: String? = null
)

data class EvaluacionDto(
    val id: String,
    val region: Int? = null,
    val sector: String,
    val sectorAmerbId: Int? = null,
    val sectorAmerb: String? = null,
    val tipoOrg: String? = null,
    val opaId: Int? = null,
    val orgNombre: String? = null,
    val numSegEsba: Int? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val createdBy: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class AuthLoginResponse(
    val ok: Boolean? = false,
    val token: String? = null,
    val user: AuthUser? = null,
    val error: String? = null
)

data class RegionDto(
    val id: Int,
    val rom: String? = null,
    val nom: String? = null
)

data class BoteMaestroDto(
    val id: Int? = null,
    val region_rom: String? = null,
    val region: String? = null,
    val nombre: String? = null,
    val nrpa: String? = null,
    val nmatricula: String? = null,
    val caleta: String? = null
)

data class EspecieDto(
    val id: Int,
    val com: String,
    val sci: String? = null,
    val lp: Boolean? = null,
    val dens: Boolean? = null,
    val is_alga: Boolean? = null,
    val activo: Boolean? = null
)

data class SectorAmerbDto(
    val id: Int,
    @SerializedName(value = "nombre", alternate = ["nombreamerb"])
    val nombre: String,
    @SerializedName(value = "region", alternate = ["region_id", "regionId"])
    val region: Int? = null,
    val comuna: String? = null
)

data class CaletaDto(
    val id: Int? = null,
    val nombre: String,
    @SerializedName(value = "region", alternate = ["region_id", "regionId"])
    val region: Int? = null,
    @SerializedName(value = "sectorAmerbId", alternate = ["sector_amerb_id", "sectorAmerb_id", "sectorId"])
    val sectorAmerbId: Int? = null
)

data class OpaDto(
    val id: Int,
    val nombre: String,
    val nombrecorto: String? = null,
    @SerializedName(value = "region", alternate = ["region_id", "regionId"])
    val region: Int? = null,
    val comuna: String? = null
)

interface BitecmaApiService {
    @GET("ping")
    suspend fun ping(): Response<PingResponse>

    @GET("operaciones")
    suspend fun getOperaciones(): Response<ApiEnvelope<List<OperacionDto>>>

    @GET("operaciones/{id}")
    suspend fun getOperacion(@Path("id") id: String): Response<ApiEnvelope<OperacionDto>>

    @POST("operaciones")
    suspend fun crearOperacion(@Body operacion: OperacionUpsertRequest): Response<ApiEnvelope<OperacionDto>>

    @PUT("operaciones/{id}")
    suspend fun actualizarOperacion(@Path("id") id: String, @Body operacion: OperacionUpsertRequest): Response<ApiEnvelope<OperacionDto>>

    @retrofit2.http.DELETE("operaciones/{id}")
    suspend fun eliminarOperacion(@Path("id") id: String): Response<ApiEnvelope<Boolean>>

    @POST("auth/login")
    suspend fun login(@Body request: AuthLoginRequest): Response<AuthLoginResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<AuthLoginResponse>

    @GET("usuarios")
    suspend fun getUsuarios(): Response<ApiEnvelope<List<AuthUser>>>

    @POST("usuarios")
    suspend fun crearUsuario(@Body usuario: AuthUser): Response<ApiEnvelope<AuthUser>>

    @PUT("usuarios/{id}")
    suspend fun actualizarUsuario(@Path("id") id: Int, @Body usuario: AuthUser): Response<ApiEnvelope<AuthUser>>

    @retrofit2.http.DELETE("usuarios/{id}")
    suspend fun eliminarUsuario(@Path("id") id: Int): Response<ApiEnvelope<Boolean>>

    @GET("evaluaciones")
    suspend fun getEvaluaciones(): Response<ApiEnvelope<List<EvaluacionDto>>>

    @GET("evaluaciones/{id}")
    suspend fun getEvaluacion(@Path("id") id: String): Response<ApiEnvelope<EvaluacionDto>>

    @POST("evaluaciones")
    suspend fun crearEvaluacion(@Body evaluacion: EvaluacionDto): Response<ApiEnvelope<EvaluacionDto>>

    @PUT("evaluaciones/{id}")
    suspend fun actualizarEvaluacion(@Path("id") id: String, @Body evaluacion: EvaluacionDto): Response<ApiEnvelope<EvaluacionDto>>

    @retrofit2.http.DELETE("evaluaciones/{id}")
    suspend fun eliminarEvaluacion(@Path("id") id: String): Response<ApiEnvelope<Boolean>>

    @GET("regiones")
    suspend fun getRegiones(): Response<ApiEnvelope<List<RegionDto>>>

    @GET("botes")
    suspend fun getBotes(): Response<ApiEnvelope<List<BoteMaestroDto>>>

    @GET("especies")
    suspend fun getEspecies(): Response<ApiEnvelope<List<EspecieDto>>>

    @GET("sectores")
    suspend fun getSectoresAmerb(): Response<ApiEnvelope<List<SectorAmerbDto>>>

    @GET("caletas")
    suspend fun getCaletas(): Response<ApiEnvelope<List<CaletaDto>>>

    @GET("organizaciones")
    suspend fun getOpas(): Response<ApiEnvelope<List<OpaDto>>>

    @GET("files")
    suspend fun getFiles(@Query("opId") opId: String? = null): Response<ApiEnvelope<List<FileMetaDto>>>

    @GET("files/{id}")
    suspend fun getFile(@Path("id") id: String): Response<ApiEnvelope<FileContentDto>>

    @POST("files")
    suspend fun uploadTextFile(@Body req: UploadTextFileRequest): Response<ApiEnvelope<FileMetaDto>>
}

data class OperacionDto(
    val id: String,
    val sector: String,
    val region: Int? = null,
    val sectorAmerbId: Int? = null,
    val sectorAmerb: String? = null,
    val tipoOrg: String? = null,
    val opaId: Int? = null,
    val org: String? = null,
    val numSeg: Int? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val botes: List<OperacionBoteDto>? = null
)

data class LpSampleDto(
    val l: Double? = null,
    val p: Double? = null,
    val d: Double? = null
)

data class DensidadUnidadDto(
    val num: Int? = null,
    val tipo: String? = null,
    val area: Double? = null,
    val fecha: String? = null,
    val sustrato: String? = null,
    val cubierta: String? = null,
    val especieId: Int? = null,
    val coordX: Double? = null,
    val coordY: Double? = null,
    val coordLong: Double? = null,
    val coordLat: Double? = null,
    val datum: String? = null,
    val counts: Map<String, Int>? = null
)

data class OperacionBoteDto(
    val id: String? = null,
    val zona: Int? = null,
    val nombre: String? = null,
    val buzo: String? = null,
    val densTipo: String? = null,
    val submareal: Int? = null,
    val boteMaestroId: Int? = null,
    val lpMuestras: Map<String, Map<String, List<LpSampleDto>>>? = null,
    val transectos: List<DensidadUnidadDto>? = null
)

data class OperacionUpsertRequest(
    val id: String,
    val region: Int? = null,
    val sector: String,
    val sectorAmerbId: Int? = null,
    val sectorAmerb: String? = null,
    val tipoOrg: String? = null,
    val opaId: Int? = null,
    val org: String? = null,
    val numSeg: Int? = null,
    val fechaInicio: String? = null,
    val fechaFin: String? = null,
    val botes: List<OperacionBoteDto>? = null
)

data class FileMetaDto(
    val id: String,
    val name: String,
    val mime: String,
    val size: Int? = null,
    val opId: String? = null,
    val createdAt: String? = null
)

data class FileContentDto(
    val id: String,
    val name: String,
    val mime: String,
    val text: String
)

data class UploadTextFileRequest(
    val name: String,
    val opId: String? = null,
    val text: String
)

private class SafeIntDeserializer : JsonDeserializer<Int?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int? {
        if (json == null || json.isJsonNull) return null
        val primitive = json.asJsonPrimitive ?: return null
        return when {
            primitive.isNumber -> runCatching { primitive.asInt }.getOrNull()
            primitive.isString -> primitive.asString.trim().toIntOrNull()
            else -> null
        }
    }
}

private class SafeDoubleDeserializer : JsonDeserializer<Double?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Double? {
        if (json == null || json.isJsonNull) return null
        val primitive = json.asJsonPrimitive ?: return null
        return when {
            primitive.isNumber -> runCatching { primitive.asDouble }.getOrNull()
            primitive.isString -> primitive.asString.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }
}

object RetrofitClient {
    private const val DEFAULT_TIMEOUT_SECONDS = 20L

    val apiService: BitecmaApiService by lazy {
        val authInterceptor = Interceptor { chain ->
            val token = AppState.authToken
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logger)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val gson = GsonBuilder()
            .registerTypeAdapter(Int::class.javaObjectType, SafeIntDeserializer())
            .registerTypeAdapter(Double::class.javaObjectType, SafeDoubleDeserializer())
            .create()

        retrofit2.Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(gson))
            .build()
            .create(BitecmaApiService::class.java)
    }
}
