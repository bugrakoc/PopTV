package pm.koc.poptv.data.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Failure reasons shown to the user (mapped to string resources in the UI layer). */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidUrl : AppError("invalid url")
    class Unauthorized(val code: Int) : AppError("http $code")
    class NotFound : AppError("http 404")
    class Http(val code: Int) : AppError("http $code")
    class NoHost(cause: Throwable? = null) : AppError("unknown host", cause)
    class Timeout(cause: Throwable? = null) : AppError("timeout", cause)
    class Ssl(cause: Throwable? = null) : AppError("ssl", cause)
    class Network(cause: Throwable? = null) : AppError("network", cause)
    class EmptyPlaylist : AppError("empty playlist")
    class InvalidPlaylist(cause: Throwable? = null) : AppError("invalid playlist", cause)
    /** Xtream login answered but refused the account; [status] is the panel's status ("Expired", ...) if any. */
    class AccountRejected(val status: String?) : AppError("account rejected: $status")
    class InvalidGuide : AppError("invalid guide")
    class Storage(cause: Throwable? = null) : AppError("storage", cause)
    class Unknown(cause: Throwable? = null) : AppError(cause?.javaClass?.simpleName, cause)

    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is AppError -> t
            is UnknownHostException -> NoHost(t)
            is SocketTimeoutException -> Timeout(t)
            is SSLException -> Ssl(t)
            is ConnectException -> Network(t)
            is IOException -> Network(t)
            is android.database.sqlite.SQLiteException -> Storage(t)
            else -> Unknown(t)
        }

        fun fromHttpCode(code: Int): AppError = when (code) {
            401, 403 -> Unauthorized(code)
            404 -> NotFound()
            else -> Http(code)
        }
    }
}
