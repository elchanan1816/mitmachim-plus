package community.mitmachim.nativeapp

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.EOFException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.jsoup.Jsoup
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun isForumHost(host: String) = host == "mitmachim.top" || host.endsWith(".mitmachim.top")
fun isForumUrl(url: HttpUrl) = url.scheme == "https" && url.port == 443 && isForumHost(url.host) && url.username.isEmpty() && url.password.isEmpty()
fun isMediaUrl(url: HttpUrl): Boolean {
    if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
    val host = url.host.lowercase()
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.contains(':')) return false
    val octets = host.split('.').mapNotNull { it.toIntOrNull() }
    return !(octets.size == 4 && (octets[0] in listOf(0, 10, 127) || octets[0] >= 224 ||
        (octets[0] == 169 && octets[1] == 254) || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168)))
}

/** Only idempotent reads may be repeated after a transient transport/server failure. */
fun retryableReadFailure(error: IOException): Boolean = when (error) {
    is ForumHttpException -> error.status in listOf(502, 503, 504)
    is SSLException, is UnknownHostException -> false
    is SocketTimeoutException, is ConnectException, is EOFException -> true
    else -> false
}

/** Messages displayed to users never contain raw transport errors or server HTML. */
fun friendlyError(error: Throwable): String {
    if (error is CancellationException) throw error
    return when (error) {
        is ForumHttpException -> error.message ?: "לא ניתן להשלים את הפעולה כרגע. נסו שוב."
        is UnknownHostException -> "כתובת מתמחים טופ לא זוהתה ברשת הזו (קוד DNS). בדקו שהחיבור פעיל ונסו שוב."
        is ConnectException -> "לא נוצר חיבור לשרת הפורום (קוד CONNECT). נסו שוב; אם האתר נפתח בדפדפן, בדקו את הגדרות החיבור או הסינון של האפליקציה."
        is SocketTimeoutException -> "החיבור לפורום לא השיב בזמן (קוד TIMEOUT). נסו שוב בעוד רגע."
        is SSLException -> "לא ניתן ליצור חיבור מאובטח לפורום. בדקו את התאריך והשעה במכשיר; אם אתם גולשים דרך נטפרי, ייתכן שחיבור הסינון או תעודת האבטחה דורשים בדיקה."
        else -> error.message?.takeIf { it.any { c -> c in '\u0590'..'\u05ff' } && it.length < 450 }
            ?: "אירעה תקלה בחיבור לפורום. נסו שוב בעוד רגע."
    }
}

fun serverError(status: Int, raw: String, login: Boolean = false): String {
    val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
    val messages = when(parsed) {
        is JSONObject -> listOf(parsed.obj("status").optString("message"),parsed.obj("status").optString("code"),
            parsed.optString("message"),parsed.optString("error"),parsed.obj("response").optString("message"))
        is String -> listOf(parsed)
        else -> emptyList()
    }.filter { it.isNotBlank() && it != "null" }
    val source = messages.joinToString(" ").lowercase()
    val siteMessage = messages.map { Jsoup.parseBodyFragment(it).text().trim() }
        .firstOrNull { it.length in 6..180 && !it.contains("[[") && !it.contains("{") && it.lowercase() !in listOf("forbidden","bad request","unauthorized","not found","internal server error") }
    return when {
        status == 429 || "too-many" in source || "flood" in source -> "בוצעו פעולות רבות בזמן קצר. המתינו מעט ונסו שוב."
        "csrf" in source -> "החיבור לפורום התעדכן. נסו שוב את הפעולה."
        "2fa" in source || "two-factor" in source -> "החשבון דורש אימות נוסף. השלימו את הכניסה באתר הפורום."
        "banned" in source -> "הפעולה אינה זמינה לחשבון הזה. בדקו את מצב החשבון בפורום."
        login && (status == 400 || status == 401 || status == 403) -> "הכניסה לא הושלמה. בדקו את שם המשתמש והסיסמה ונסו שוב."
        status == 401 || "not-logged-in" in source -> "החיבור לחשבון פג. היכנסו שוב כדי להמשיך."
        "locked" in source || "read-only" in source -> "הדיון נעול, ולכן הפורום אינו מאפשר לבצע בו את הפעולה הזו."
        ("self" in source || "own post" in source || "own-post" in source) && ("vot" in source || "like" in source) -> "אי אפשר לעשות לייק או דיסלייק לפוסט שכתבת בעצמך."
        "already-voted" in source -> "ההצבעה כבר נקלטה בפורום. רעננו את הדיון."
        "reputation" in source -> "נדרש מוניטין נוסף בפורום כדי לבצע פעולה זו."
        "too-short" in source || "minimum" in source -> "התוכן קצר מדי לפי כללי הפורום. הוסיפו כמה מילים ונסו שוב."
        "too-long" in source || "maximum" in source -> "התוכן ארוך מהמותר בפורום. קצרו אותו ונסו שוב."
        status == 413 -> "הקובץ גדול מהמותר בפורום. בחרו קובץ קטן יותר."
        status == 404 -> "התוכן לא נמצא. ייתכן שהוסר או שהקישור אינו תקין."
        status >= 500 -> "מתמחים טופ אינו זמין כרגע. נסו שוב בעוד כמה רגעים."
        siteMessage != null && siteMessage.any { it in '\u0590'..'\u05ff' } -> siteMessage
        siteMessage != null -> "הפורום מסביר: $siteMessage"
        status == 403 || "privilege" in source || "not-allowed" in source -> "אין לחשבון הרשאה לביצוע הפעולה הזו בפורום."
        else -> "הפורום לא אישר את הפעולה. בדקו את התוכן ואת הרשאות החשבון ונסו שוב."
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}

data class PublishResult(val tid: Int = 0, val pid: Int = 0, val index: Int = 0, val queued: Boolean = false)
fun parsePublished(response: JSONObject, replyTid: Int = 0): PublishResult {
    if (response.flag("queued")) return PublishResult(tid = replyTid, queued = true)
    val tid = response.optInt("tid", replyTid)
    val pid = response.optInt("pid", response.optInt("mainPid"))
    if (tid <= 0 || (replyTid > 0 && pid <= 0)) throw IOException("לא התקבל אישור שליחה ברור. בדקו בדיון לפני שליחה חוזרת; הטיוטה נשמרה.")
    return PublishResult(tid, pid, if (response.has("index")) response.optInt("index") + 1 else 0)
}
