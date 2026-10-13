package kz.terensai.rhythm.data

import android.content.Context
import java.util.UUID

/** Application-scoped random ID, intentionally unrelated to hardware identifiers. */
object LocalInstallIdentity {
    fun get(context: Context): String {
        val settings = context.applicationContext.getSharedPreferences("paritm_install_identity", Context.MODE_PRIVATE)
        val old = settings.getString("installId", null)
        if (!old.isNullOrBlank()) return old
        val created = UUID.randomUUID().toString()
        check(settings.edit().putString("installId", created).commit()) {
            "Не удалось сохранить локальную идентичность"
        }
        return created
    }
}
