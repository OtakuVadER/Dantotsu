package ani.dantotsu.widgets.resumable

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import ani.dantotsu.MainActivity
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.util.BitmapUtil
import ani.dantotsu.util.BitmapUtil.Companion.convertDrawableToBitmap
import ani.dantotsu.widgets.WidgetSizeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [ResumableWidgetConfigureActivity]
 */
class ResumableWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val rv = updateAppWidget(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, rv)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra("appWidgetId", -1)
        val views = RemoteViews(context.packageName, R.layout.resumable_widget).apply {
            if (VIEWFLIPPER_NEXT == intent.action) {
                showNext(R.id.widgetViewFlipper)
            }
            if (VIEWFLIPPER_PREV == intent.action) {
                showPrevious(R.id.widgetViewFlipper)
            }
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (context != null && appWidgetManager != null) {
            val views = updateAppWidget(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        var widgetItems = mutableListOf<WidgetItem>()
        private var refreshing = false

        private suspend fun getContinueItems(type: String) {
            Anilist.query.continueMedia(type).forEach {
                widgetItems.add(
                    WidgetItem(
                        it.userPreferredName,
                        it.cover ?: "",
                        it.id
                    )
                )
            }
        }

        fun fillWidgetItems(prefs: SharedPreferences) : MutableList<WidgetItem> {
            refreshing = true
            runBlocking(Dispatchers.IO) {
                when (prefs.getInt(PREF_WIDGET_TYPE, 0)) {
                    ResumableType.CONTINUE_ANIME.ordinal ->  getContinueItems("ANIME")
                    ResumableType.CONTINUE_MANGA.ordinal -> getContinueItems("MANGA")
                    else -> {
                        getContinueItems("ANIME")
                        getContinueItems("MANGA")
                    }
                }

                refreshing = false
            }
            return widgetItems
        }

        private fun downloadImageAsBitmap(imageUrl: String): Bitmap? {
            var bitmap: Bitmap? = null

            runBlocking(Dispatchers.IO) {
                var inputStream: InputStream? = null
                var urlConnection: HttpURLConnection? = null
                try {
                    val url = URL(imageUrl)
                    urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.requestMethod = "GET"
                    urlConnection.connect()

                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        inputStream = urlConnection.inputStream
                        bitmap = BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    inputStream?.close()
                    urlConnection?.disconnect()
                }
            }
            return bitmap?.let { BitmapUtil.roundCorners(it) }
        }

        private fun getPendingSelfIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, ResumableWidget::class.java)
            intent.setAction(action).putExtra("appWidgetId", appWidgetId)
            return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        private fun RemoteViews.setLocalAdapter(context: Context, appWidgetId: Int, view: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val titleTextColor = prefs.getInt(PREF_TITLE_TEXT_COLOR, Color.WHITE)
            val intent = Intent(context, ResumableRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val builder = RemoteViews.RemoteCollectionItems.Builder()
                widgetItems.clear()
                fillWidgetItems(prefs).forEach {item ->
                    val rv = RemoteViews(context.packageName, R.layout.item_resumable_widget).apply {
                        setTextViewText(R.id.text_show_title, item.title)
                        setTextColor(R.id.text_show_title, titleTextColor)
                        val bitmap = downloadImageAsBitmap(item.image)
                        setImageViewBitmap(R.id.image_show_icon, bitmap)
                        val fillInIntent = Intent().apply {
                            putExtra("mediaId", item.id)
                            putExtra("continue", true)
                        }
                        setOnClickFillInIntent(R.id.image_show_icon, fillInIntent)
                    }
                    builder.addItem(item.id.toLong(), rv)
                }
                setRemoteAdapter(view, builder.setViewTypeCount(1).build())
            } else {
                setRemoteAdapter(view, intent)
            }

        }

        fun updateAppWidget(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val backgroundColor =
                prefs.getInt(PREF_BACKGROUND_COLOR, ContextCompat.getColor(context, R.color.theme))
            val backgroundFade = prefs.getInt(PREF_BACKGROUND_FADE, Color.GRAY)
            val titleTextColor = prefs.getInt(PREF_TITLE_TEXT_COLOR, Color.WHITE)

            val gradientDrawable = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.gradient_background,
                null
            ) as GradientDrawable
            gradientDrawable.colors = intArrayOf(backgroundColor, backgroundFade)
            val widgetSizeProvider = WidgetSizeProvider(context)
            var (width, height) = widgetSizeProvider.getWidgetsSize(appWidgetId)
            if (width > 0 && height > 0) {
                gradientDrawable.cornerRadius = 50f
            } else {
                width = 300
                height = 300
            }

            val intentTemplate = Intent(context, MainActivity::class.java)
            intentTemplate.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intentTemplate.putExtra("fromWidget", true)

            val views = RemoteViews(context.packageName, R.layout.resumable_widget).apply {

                setImageViewBitmap(R.id.backgroundView, convertDrawableToBitmap(gradientDrawable, width, height))
                setTextColor(R.id.text_show_title, titleTextColor)
                setTextColor(R.id.widgetTitle, titleTextColor)

                setEmptyView(R.id.widgetViewFlipper, R.id.empty_view)

                if (prefs.getBoolean(PREF_USE_STACKVIEW, false)) {
                    setViewVisibility(R.id.widgetViewFlipper, View.GONE)
                    setViewVisibility(R.id.widgetStackView, View.VISIBLE)
                    setViewVisibility(R.id.leftFlipper, View.GONE)
                    setViewVisibility(R.id.rightFlipper, View.GONE)
                    setPendingIntentTemplate(
                        R.id.widgetStackView,
                        PendingIntent.getActivity(
                            context,
                            0,
                            intentTemplate,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    )
                    setLocalAdapter(context, appWidgetId, R.id.widgetStackView)
                } else {
                    setViewVisibility(R.id.widgetStackView, View.GONE)
                    setViewVisibility(R.id.widgetViewFlipper, View.VISIBLE)
                    setViewVisibility(R.id.leftFlipper, View.VISIBLE)
                    setViewVisibility(R.id.rightFlipper, View.VISIBLE)
                    widgetItems.clear()
                    setPendingIntentTemplate(
                        R.id.widgetViewFlipper,
                        PendingIntent.getActivity(
                            context,
                            0,
                            intentTemplate,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    )
                    setLocalAdapter(context, appWidgetId, R.id.widgetViewFlipper)

                    setOnClickPendingIntent(R.id.leftFlipper, getPendingSelfIntent(context, appWidgetId, VIEWFLIPPER_PREV))
                    setOnClickPendingIntent(R.id.rightFlipper, getPendingSelfIntent(context, appWidgetId, VIEWFLIPPER_NEXT))
                }
                setViewVisibility(R.id.empty_view, View.GONE)

                setOnClickPendingIntent(
                    R.id.widgetTitle,
                    PendingIntent.getActivity(
                        context,
                        1,
                        Intent(context, ResumableWidgetConfigureActivity::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            return views
        }

        const val PREFS_NAME = "ani.dantotsu.widgets.ResumableWidget"
        const val PREF_BACKGROUND_COLOR = "background_color"
        const val PREF_BACKGROUND_FADE = "background_fade"
        const val PREF_TITLE_TEXT_COLOR = "title_text_color"
        const val PREF_WIDGET_TYPE = "widget_type"
        const val PREF_USE_STACKVIEW = "use_stackview"

        const val VIEWFLIPPER_NEXT = "viewflipper_next"
        const val VIEWFLIPPER_PREV = "viewflipper_prev"
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = ResumableWidget.updateAppWidget(context, appWidgetId)
    appWidgetManager.updateAppWidget(appWidgetId, views)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetViewFlipper)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetStackView)
}
