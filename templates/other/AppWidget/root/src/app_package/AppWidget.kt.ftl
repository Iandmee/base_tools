package ${packageName}

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

/**
 * Implementation of App Widget functionality.
<#if configurable>
 * App Widget Configuration implemented in [${className}ConfigureActivity]
</#if>
 */
class ${className} : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

<#if configurable>
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            deleteTitlePref(context, appWidgetId)
        }
    }
</#if>
    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
<#if configurable>
    val widgetText = loadTitlePref(context, appWidgetId)
<#else>
    val widgetText = context.getString(R.string.appwidget_text)
</#if>
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.${class_name})
    views.setTextViewText(R.id.appwidget_text, widgetText)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}