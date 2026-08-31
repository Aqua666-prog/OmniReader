package app.omnireader.android.core.log

import android.util.Log

/** Logs subsystem state only. Never pass book text, notes, URIs, or user content. */
object AppLog {
    fun scanner(message: String, error: Throwable? = null) = write("Scanner", message, error)
    fun database(message: String, error: Throwable? = null) = write("Database", message, error)
    fun reader(message: String, error: Throwable? = null) = write("Reader", message, error)
    fun archive(message: String, error: Throwable? = null) = write("Archive", message, error)
    fun metadata(message: String, error: Throwable? = null) = write("Metadata", message, error)

    private fun write(area: String, message: String, error: Throwable?) {
        if (error == null) Log.i("OmniReader/$area", message) else Log.w("OmniReader/$area", message, error)
    }
}
