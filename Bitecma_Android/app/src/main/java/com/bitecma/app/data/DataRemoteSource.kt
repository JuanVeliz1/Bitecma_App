package com.bitecma.app.data

import com.bitecma.app.network.ApiEnvelope
import com.bitecma.app.network.AuthLoginRequest
import com.bitecma.app.network.AuthLoginResponse
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.FileContentDto
import com.bitecma.app.network.FileMetaDto
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionUpsertRequest
import com.bitecma.app.network.PingResponse
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.network.AuthUser
import com.bitecma.app.network.UploadTextFileRequest
import retrofit2.Response

internal data class RemoteEnvelopeResult<T>(
    val ok: Boolean,
    val data: T? = null,
    val code: Int? = null,
    val error: String? = null,
)

internal data class RemoteLoginResult(
    val ok: Boolean,
    val token: String? = null,
    val user: AuthUser? = null,
    val code: Int? = null,
    val error: String? = null,
)

internal object DataRemoteSource {
    private suspend fun <T> callEnvelope(
        request: suspend () -> Response<ApiEnvelope<T>>,
    ): RemoteEnvelopeResult<T> {
        val response = runCatching { request() }.getOrNull()
            ?: return RemoteEnvelopeResult(ok = false)
        val body = response.body()
        return RemoteEnvelopeResult(
            ok = response.isSuccessful && body?.ok == true,
            data = body?.data,
            code = response.code(),
            error = body?.error,
        )
    }

    suspend fun actualizarOperacion(id: String, req: OperacionUpsertRequest): RemoteEnvelopeResult<OperacionDto> =
        callEnvelope { RetrofitClient.apiService.actualizarOperacion(id, req) }

    suspend fun crearOperacion(req: OperacionUpsertRequest): RemoteEnvelopeResult<OperacionDto> =
        callEnvelope { RetrofitClient.apiService.crearOperacion(req) }

    suspend fun getOperaciones(): RemoteEnvelopeResult<List<OperacionDto>> =
        callEnvelope { RetrofitClient.apiService.getOperaciones() }

    suspend fun getOperacion(opId: String): RemoteEnvelopeResult<OperacionDto> =
        callEnvelope { RetrofitClient.apiService.getOperacion(opId) }

    suspend fun getRegiones(): RemoteEnvelopeResult<List<RegionDto>> =
        callEnvelope { RetrofitClient.apiService.getRegiones() }

    suspend fun getEspecies(): RemoteEnvelopeResult<List<EspecieDto>> =
        callEnvelope { RetrofitClient.apiService.getEspecies() }

    suspend fun uploadTextFile(req: UploadTextFileRequest): RemoteEnvelopeResult<FileMetaDto> =
        callEnvelope { RetrofitClient.apiService.uploadTextFile(req) }

    suspend fun getFiles(opId: String?): RemoteEnvelopeResult<List<FileMetaDto>> =
        callEnvelope { RetrofitClient.apiService.getFiles(opId) }

    suspend fun getFile(fileId: String): RemoteEnvelopeResult<FileContentDto> =
        callEnvelope { RetrofitClient.apiService.getFile(fileId) }

    suspend fun ping(): Boolean {
        val response = runCatching { RetrofitClient.apiService.ping() }.getOrNull() ?: return false
        return response.isSuccessful && response.body()?.ok == true
    }

    suspend fun login(email: String, password: String): RemoteLoginResult {
        val response = runCatching {
            RetrofitClient.apiService.login(AuthLoginRequest(correo = email, password = password))
        }.getOrNull() ?: return RemoteLoginResult(ok = false)
        val body = response.body()
        return RemoteLoginResult(
            ok = response.isSuccessful && body?.ok == true && !body.token.isNullOrBlank(),
            token = body?.token,
            user = body?.user,
            code = response.code(),
            error = body?.error,
        )
    }

    suspend fun eliminarOperacion(opId: String): RemoteEnvelopeResult<Boolean> =
        callEnvelope { RetrofitClient.apiService.eliminarOperacion(opId) }
}
