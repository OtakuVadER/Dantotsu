/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package ani.dantotsu.update

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import android.widget.Toast
import ani.dantotsu.BuildConfig
import ani.dantotsu.widgets.resumable.ResumableWidget
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import java.io.File
import java.net.URISyntaxException

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        intent.setPackage(context.packageName)
        intent.flags = 0
        intent.data = null
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            updateWidgets(context)
        } else if (BuildConfig.BUILD_TYPE.contentEquals("matagi")) {
            when (intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)?.let {
                        try {
                            startLauncherActivity(context, Intent.parseUri(
                                it.toUri(0),
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                                    Intent.URI_ALLOW_UNSAFE else 0
                            ))
                        } catch (ignored: URISyntaxException) { }
                    }
                }

                PackageInstaller.STATUS_FAILURE_ABORTED -> { }
                PackageInstaller.STATUS_SUCCESS -> {
                    deletePackages(
                        Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ))
                    deletePackages(File(Environment.DIRECTORY_DOWNLOADS))
                }
                else -> {
                    val error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    if (error?.contains("Session was abandoned") != true)
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deletePackages(directory: File) {
        directory.listFiles()?.filter { file ->
            file.name.startsWith("Dantotsu-") && file.extension == "apk"
        }?.forEach { it.delete() }
    }

    private fun updateWidgets(context: Context) {
        val resumableWidget = Intent(context, ResumableWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids: IntArray = AppWidgetManager.getInstance(context.applicationContext)
                .getAppWidgetIds(this.component)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(resumableWidget)
    }

    private fun startLauncherActivity(context: Context, intent: Intent?) {
        context.startActivity(intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}