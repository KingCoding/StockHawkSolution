package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.ui.LineGraphActivity;
import com.sam_chordas.android.stockhawk.ui.MyStocksActivity;
import com.sam_chordas.android.stockhawk.widget.QuoteWidgetProvider;
import com.sam_chordas.android.stockhawk.widget.QuoteWidgetRemoteViewsService;

/**
 * Created by sam_chordas on 10/1/15.
 */
public class StockIntentService extends IntentService {

  public StockIntentService(){
    super(StockIntentService.class.getName());
  }

  public StockIntentService(String name) {
    super(name);
  }

  @Override protected void onHandleIntent(Intent intent) {

    if(intent.getStringExtra(QuoteWidgetProvider.CONTEXT_PACKAGE_NAME) != null){
        handleWidgetIntent(intent);
    }
    else {
        Log.d(StockIntentService.class.getSimpleName(), "Stock Intent Service");
        StockTaskService stockTaskService = new StockTaskService(this);
        Bundle args = new Bundle();
        if (intent.getStringExtra("tag").equals("add") || intent.getStringExtra("tag").equals("historical")) {
            args.putString("symbol", intent.getStringExtra("symbol"));
        }
        // We can call OnRunTask from the intent service to force it to run immediately instead of
        // scheduling a task.
        stockTaskService.onRunTask(new TaskParams(intent.getStringExtra("tag"), args));
    }
  }

  //Method to handle widget related intents.
  //A new service could have been created to handle widget related intents
  //But for sake of simplicity, things can easily be done here

  public void handleWidgetIntent(Intent intent){


      /*
      for(int appWidgetId : appWidgetIds){

          RemoteViews views = null;

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
              //We can set the layout in function of the current size of the widget
              AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);

          }
          else{
              //We need to set a static layout
              views = new RemoteViews(context.getPackageName() , R.layout.widget_collection);
              appWidgetManager.updateAppWidget(appWidgetId,views);
          }
      }
      */

      //Use service context to get AppWidgetManager and also to set pending intents for remoteViews

      String origin = intent.getStringExtra(QuoteWidgetProvider.INTENT_PROVIDER_ORIGIN);
      String packageName = intent.getStringExtra(QuoteWidgetProvider.CONTEXT_PACKAGE_NAME);

      AppWidgetManager appWidgetManager = null;
      ComponentName stockHawkCompName = null;
      int[] allWidgetIds = {intent.getIntExtra(QuoteWidgetProvider.APP_WIDGET_ID, -1)};
      appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());

     if(!QuoteWidgetProvider.ORIGIN_ON_APP_WIDGET_OPTIONS_CHANGED.equals(origin)) {
         stockHawkCompName = new ComponentName(packageName, QuoteWidgetProvider.class.getName());
         allWidgetIds = appWidgetManager.getAppWidgetIds(stockHawkCompName);
     }


      /*
      if(QuoteWidgetProvider.ORIGIN_ON_APP_WIDGET_OPTIONS_CHANGED.equals(origin)){

      }
      else
      */
      if(QuoteWidgetProvider.ORIGIN_ON_RECEIVE.equals(origin)){ // intent is from onReceive; we notify that the dataSet has changed

          for(int currWId : allWidgetIds)
            appWidgetManager.notifyAppWidgetViewDataChanged(currWId, R.id.widget_list);

      }
      else{ // This intent is from onUpdate or onAppWidgetOptionsChanged. We have to make the initial initialization of remote views


          RemoteViews views = null;
          for(int appWidgetId : allWidgetIds) {
              /*
              int minWidth = intent.getIntExtra(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1);
              int minHeight = intent.getIntExtra(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,-1);
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                      ((minWidth >110&&minHeight>=110) || (minWidth >=110&&minHeight>110)))
              {//This is after a manual resize of the widget
                  //We can set the layout in function of the current size of the widget
                  //Bundle appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId);

                  views = new RemoteViews(packageName, R.layout.widget_collection);
                  Intent intentToRemoteViewsService = new Intent(this, QuoteWidgetRemoteViewsService.class);
                  intentToRemoteViewsService.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                  views.setRemoteAdapter(R.id.widget_list, intentToRemoteViewsService);
              } else
              {
              */
                  views = new RemoteViews(packageName, R.layout.widget_collection);
                  //Set the intent for the header
                  Intent headerIntent = new Intent(this.getApplicationContext(), MyStocksActivity.class);
                  PendingIntent pendingIntent = PendingIntent.getActivity(this.getApplicationContext(),0,headerIntent,0);
                  views.setOnClickPendingIntent(R.id.widget, pendingIntent);

                  //We need to set a static layout whether the widget is periodically updated or manually resize

                  //Intent intentToRemoteViewsService = new Intent(this, QuoteWidgetRemoteViewsService.class);
                  //PendingIntent pendingIntent = PendingIntent.getActivity();
                  //views.setOnClickPendingIntent();
                  Intent clickIntentTemplate = new Intent(this.getApplicationContext(), LineGraphActivity.class);
                  PendingIntent clickPendingIntentTemplate = TaskStackBuilder.create(this.getApplicationContext())
                      .addNextIntentWithParentStack(clickIntentTemplate)
                      .getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
                  views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntentTemplate);

                  Intent intentToRemoteViewsService = new Intent(this.getApplicationContext(), QuoteWidgetRemoteViewsService.class);
                  intentToRemoteViewsService.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                  intentToRemoteViewsService.putExtra(QuoteWidgetProvider.CONTEXT_PACKAGE_NAME, packageName);
                  views.setRemoteAdapter(R.id.widget_list, intentToRemoteViewsService);
                  //intentToRemoteViewsService.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                  //views.setRemoteAdapter(R.id.widget_list, intentToRemoteViewsService);
              //}
              appWidgetManager.updateAppWidget(appWidgetId, views);
          }
      }

  }

}

