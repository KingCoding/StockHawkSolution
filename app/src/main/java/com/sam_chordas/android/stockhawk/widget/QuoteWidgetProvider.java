package com.sam_chordas.android.stockhawk.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;

/**
 * Created by owner on 11/05/2016.
 */
public class QuoteWidgetProvider extends AppWidgetProvider {

    public static final String CONTEXT_PACKAGE_NAME = "CONTEXT_PACKAGE_NAME";
    public static final String INTENT_PROVIDER_ORIGIN = "INTENT_PROVIDER_ORIGIN";
    public static final String ORIGIN_ON_APP_WIDGET_OPTIONS_CHANGED = "ORIGIN_ON_APP_WIDGET_OPTIONS_CHANGED";
    public static final String ORIGIN_ON_RECEIVE = "ORIGIN_ON_RECEIVE";
    public static final String APP_WIDGET_ID = "APP_WIDGET_ID";
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        Intent intent = new Intent(context, StockIntentService.class);
        intent.putExtra(CONTEXT_PACKAGE_NAME, context.getPackageName());
        context.startService(intent);
    }


    @Override
    public void onReceive(Context context, Intent intent) {

        super.onReceive(context,intent);

        if(StockTaskService.ACTION_QUOTE_UPDATED.equals(intent.getAction())){
            Intent intentToSend = new Intent(context, StockIntentService.class);
            intentToSend.putExtra(CONTEXT_PACKAGE_NAME, context.getPackageName());
            intentToSend.putExtra(INTENT_PROVIDER_ORIGIN,ORIGIN_ON_RECEIVE);
            context.startService(intentToSend);
        }

    }

    /*
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                                          Bundle newOptions){
        Intent intentToSend = new Intent(context, StockTaskService.class);
        intentToSend.putExtra(INTENT_PROVIDER_ORIGIN,ORIGIN_ON_APP_WIDGET_OPTIONS_CHANGED);
        intentToSend.putExtra(CONTEXT_PACKAGE_NAME, context.getPackageName());
        intentToSend.putExtra(APP_WIDGET_ID, appWidgetId);
        intentToSend.putExtra(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        intentToSend.putExtra(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        context.startService(intentToSend);
    }

    */

}






