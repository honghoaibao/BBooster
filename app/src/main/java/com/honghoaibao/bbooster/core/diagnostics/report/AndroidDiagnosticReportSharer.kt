package com.honghoaibao.bbooster.core.diagnostics.report

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.honghoaibao.bbooster.R
import java.io.File

/** Shares one generated report through Android's system Sharesheet. */
object AndroidDiagnosticReportSharer {

    fun share(
        activity: Activity,
        file: File,
        format: DiagnosticReportFormat
    ) {
        require(file.isFile) { "The diagnostic report file does not exist." }

        val contentUri = FileProvider.getUriForFile(
            activity,
            activity.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.report_share_subject))
            clipData = ClipData.newUri(
                activity.contentResolver,
                file.name,
                contentUri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        activity.startActivity(
            Intent.createChooser(
                sendIntent,
                activity.getString(R.string.report_share_chooser)
            )
        )
    }

    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".diagnostic-files"
}
