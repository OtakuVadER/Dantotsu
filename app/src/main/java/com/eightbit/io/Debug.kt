/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
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
package com.eightbit.io

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Build
import ani.dantotsu.BuildConfig
import ani.dantotsu.R

@Suppress("unused")
object Debug {
    private val manufacturer: String by lazy {
        try {
            @SuppressLint("PrivateApi")
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val name = get.invoke(c, "ro.product.manufacturer") as String
            name.ifEmpty { "Unknown" }
        } catch (e: Exception) {
            Build.MANUFACTURER
        }
    }

    private val separator = System.getProperty("line.separator") ?: "\n"

    fun getExceptionCause(e: Exception): String? {
        val description =  e.message ?: e.cause?.toString()
        return if (null != description && description.contains(" : "))
            description.substring(description.indexOf(":") + 2)
        else description
    }

    fun getExceptionClass(e: Exception) : String {
        return e.cause?.javaClass?.name ?: e.javaClass.name
    }

    fun hasException(e: Exception, className: String, methodName: String): Boolean {
        return !e.stackTrace.isNullOrEmpty() && e.stackTrace.any {
            it.className.endsWith(className) && it.methodName == methodName
        }
    }

    private const val ISSUE_URL = ("https://github.com/RepoDevil/Dantotsu/issues/new?"
            + "labels=logcat&title=[Bug]%3A+")

    private fun getDeviceProfile(): StringBuilder {
        val log = StringBuilder(separator)
        log.append(BuildConfig.COMMIT)
        log.append(separator)
        log.append(manufacturer)
        log.append(" ")
        var codeName = "UNKNOWN"
        for (field in Build.VERSION_CODES::class.java.fields) {
            try {
                if (field.getInt(Build.VERSION_CODES::class.java) == Build.VERSION.SDK_INT)
                    codeName = field.name
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        log.append(codeName)
        log.append(" (")
        log.append(Build.VERSION.RELEASE)
        log.append(") - ").append(Memory.getDeviceRAM()).append(" RAM")
        return log
    }

    private fun setEmailParams(action: String, subject: String, text: String): Intent {
        return Intent(action).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("tagmo.git@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun submitLogcat(context: Context, logText: String) {
        val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
        try {
            val emailIntent = setEmailParams(Intent.ACTION_SENDTO, subject, logText)
            context.startActivity(Intent.createChooser(
                emailIntent, subject
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (anf: ActivityNotFoundException) {
            try {
                val emailIntent = setEmailParams(Intent.ACTION_SEND, subject, logText)
                context.startActivity(Intent.createChooser(
                    emailIntent, subject
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ex: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUE_URL)))
                } catch (ignored: Exception) { }
            }
        }
    }

    private fun setClipboard(context: Context, subject: String, logText: String) {
        with (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager) {
            setPrimaryClip(ClipData.newPlainText(subject, logText))
        }
    }

    @JvmStatic
    fun processException(context: Context, exception: String?, clipboard: Boolean) {
        val log = getDeviceProfile()
        log.append(separator).append(separator).append(exception)
        val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
        setClipboard(context, subject, log.toString())
        when {
            clipboard -> {}
            else -> { submitLogcat(context, log.toString()) }
        }
    }

    @JvmStatic
    fun clipException(context: Context, exception: String?) {
        processException(context, exception, true)
    }

    @JvmStatic
    fun sendException(context: Context, exception: String?) {
        processException(context, exception, false)
    }
}