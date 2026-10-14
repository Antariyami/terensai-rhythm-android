package kz.terensai.rhythm.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Small reports go as Telegram text. Large collections go as UTF-8 .txt
 * attachments, all-time history is an offline HTML attachment. No server,
 * database permission, or bulky PDF/HTML library is needed.
 */
object TelegramShare {
    fun share(context:Context,period:TelegramReportPeriod,report:TelegramExport) {
        if(period!=TelegramReportPeriod.ALL && report.text.length<=3500){
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,report.text)
            }
            context.startActivity(Intent.createChooser(intent,"Отправить отчёт"))
            return
        }
        val isHtml=period==TelegramReportPeriod.ALL
        val folder=File(context.cacheDir,"reports").also{it.mkdirs()}
        val name=if(isHtml)"paritm_archive.html" else "paritm_report.txt"
        val file=File(folder,name)
        file.writeText(if(isHtml)TelegramPeriodReports.html(report) else report.text,Charsets.UTF_8)
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file)
        val intent=Intent(Intent.ACTION_SEND).apply {
            type=if(isHtml)"text/html" else "text/plain"
            putExtra(Intent.EXTRA_STREAM,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=android.content.ClipData.newUri(context.contentResolver,name,uri)
        }
        context.startActivity(Intent.createChooser(intent,"Поделиться файлом отчёта"))
    }
}
