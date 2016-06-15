package com.sam_chordas.android.stockhawk.service;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService{

    public static final String ACTION_QUOTE_UPDATED = "com.sam_chordas.android.stockhawk.app.ACTION_QUOTE_UPDATED";
    //Constants used for historical data loading
    private static final int MAX_QUOTE_BACH = 12;
    private static final int HISTORICAL_DAY_RANGE = 32;

    private boolean likelyLoadingHistoricalData = false;

    private String LOG_TAG = StockTaskService.class.getSimpleName();

  private OkHttpClient client = new OkHttpClient();
  private Context mContext;
  private StringBuilder mStoredSymbols = new StringBuilder();
  private boolean isUpdate;
  private HistoricalDataLoader historicalDataLoader;

  public StockTaskService(){}

  public StockTaskService(Context context){
    mContext = context;
  }

  synchronized String fetchData(String url) throws IOException{
    Request request = new Request.Builder()
        .url(url)
        .build();

    Response response = client.newCall(request).execute();

    int code = response.code();
    int quoteStatus = -1;
    if(code>=200 && code <300){ // SUCCESS
        quoteStatus = QUOTE_TRANSACTION_STATUS_OK;
    }
    else if(code>=400 && code <500){ // Client error
        quoteStatus = QUOTE_TRANSACTION_STATUS_SERVER_DOWN;
    }
    else if(code>=500 && code <600){ //Server error
        quoteStatus = QUOTE_TRANSACTION_STATUS_SERVER_DOWN;
    }
    return response.body().string();
  }

  private String fetchHistoricalData(String url){

      return null;
  }


  @Override
  public int onRunTask(TaskParams params){
    Cursor initQueryCursor = null;
    if (mContext == null){
      mContext = this;
    }
    //Boolean loadHistorical = false;
    String[] quotesSymbols = null;
    StringBuilder urlStringBuilder = null;
    if(!params.getTag().equals("historical")) {
        urlStringBuilder = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");

            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
                    + "in (", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    if (params.getTag().equals("init") || params.getTag().equals("periodic")){
      isUpdate = true;
      initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
          new String[] { "Distinct " + QuoteColumns.SYMBOL }, null,
          null, null);
      if (initQueryCursor == null || initQueryCursor.getCount() == 0){
        // Init task. Populates DB with quotes for the symbols seen below
        try {
          urlStringBuilder.append(
              URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
          quotesSymbols = new String[4];
          quotesSymbols[0] = "YHOO";
          quotesSymbols[1] = "AAPL";
          quotesSymbols[2] = "GOOG";
          quotesSymbols[3] = "MSFT";

        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      } else if (initQueryCursor != null){
        DatabaseUtils.dumpCursor(initQueryCursor);
        initQueryCursor.moveToFirst();
        //quotesSymbols = new String[initQueryCursor.getCount()];
        String currSymbol = null;
        for (int i = 0; i < initQueryCursor.getCount(); i++){
          currSymbol = initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"));
          //quotesSymbols[i] = currSymbol;
          mStoredSymbols.append("\"" + currSymbol+"\",");
          initQueryCursor.moveToNext();
        }
        mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
        try {
          urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      }
    } else if (params.getTag().equals("add")){
      isUpdate = false;
      // get symbol from params.getExtra and build query
      String stockInput = params.getExtras().getString("symbol");
      quotesSymbols = new String[1];
      quotesSymbols[0] = stockInput;
      try {
        urlStringBuilder.append(URLEncoder.encode("\""+stockInput+"\")", "UTF-8"));
      } catch (UnsupportedEncodingException e){
        e.printStackTrace();
      }
    }

    // finalize the URL for the API query.
    urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
        + "org%2Falltableswithkeys&callback=");

    String urlString;
    String getResponse;
    int result = GcmNetworkManager.RESULT_FAILURE;
    //Set the locationStatus to its default; unknown
    Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_UNKNOWN);

    if (urlStringBuilder != null){
      urlString = urlStringBuilder.toString();
      try{

        //if(params.getTag().equals("init")) //For the first time, let the user know we are attempting to connect to the server
            //postToast("Attempting to connect to server");
        getResponse = fetchData(urlString);
        if(getResponse == null) {
            Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_DOWN);
            return GcmNetworkManager.RESULT_FAILURE;
        }
        result = GcmNetworkManager.RESULT_SUCCESS;
        try {
            ContentValues contentValues = new ContentValues();

            //Let's retrieve the old historical data list before updating the isCurrent field to 0.
            initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                new String[] { "Distinct " + QuoteColumns.SYMBOL, QuoteColumns.HISTORICAL_BIDPRICE_JSON },
                QuoteColumns.ISCURRENT+" =?", new String[]{ "1"}, null);

            HashMap<String, String> histMap = new HashMap<String, String>();

            if(!params.getTag().equals("add") && initQueryCursor!= null && initQueryCursor.getCount() > 0){
                //initQueryCursor.moveToFirst();
                String currHist ="";
                String currSymbol = "";
                for(;initQueryCursor.moveToNext();){
                    currHist = initQueryCursor.getString(initQueryCursor.getColumnIndex(QuoteColumns.HISTORICAL_BIDPRICE_JSON));
                    currSymbol = initQueryCursor.getString(initQueryCursor.getColumnIndex(QuoteColumns.SYMBOL));
                    if(currHist == null)
                        currHist = "DEFAULT";
                    histMap.put(currSymbol,currHist);
                }
            }
            else if(params.getTag().equals("add"))
            {
                histMap.put(params.getExtras().getString("symbol"),"DEFAULT");
            }
            else{ //We are at the beginning
                histMap.put("YHOO", "DEFAULT");
                histMap.put("GOOG", "DEFAULT");
                histMap.put("AAPL", "DEFAULT");
                histMap.put("MSFT", "DEFAULT");
            }

            // update ISCURRENT to 0 (false) so new data is current
            if (isUpdate){
            contentValues.put(QuoteColumns.ISCURRENT, 0);
            mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                null, null);
            }

            ArrayList contentVals = null;
            try{

              contentVals = Utils.quoteJsonToContentVals(getResponse, histMap);
            }catch(NullPointerException exp)
            { //At this point, there is a problem with the response. Probably due to a bad symbol
              if(mContext != null) {
                  final String toastText = mContext.getString(R.string.possible_bad_symbol_toast);
                  //Toast.makeText(mContext.getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();

                  postToast(toastText);
              }
              if(mContext != null)
              Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_BAD_USER_INPUT);
              return GcmNetworkManager.RESULT_FAILURE;
            }
             catch(JSONException jexp){
              if(mContext != null)
              Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_INVALID);
              return GcmNetworkManager.RESULT_FAILURE;
            }


          mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
              contentVals);

          //Delete all the previous data
          mContext.getContentResolver().delete(QuoteProvider.Quotes.CONTENT_URI, QuoteColumns.ISCURRENT+" =?", new String[]{"0"});

        }catch (RemoteException | OperationApplicationException e){
          Log.e(LOG_TAG, "Error applying batch insert", e);
          return GcmNetworkManager.RESULT_FAILURE;
        }
      } catch (IOException e){
        if(mContext != null) {

            if(!Utils.isNetworkAvailable(mContext))
                Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_NO_NETWORK_CONNECTION); //So that the activity can detect the status no network available
            else
                Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_DOWN);
        }
        /*
        if(mContext != null) {
            String connectionErrorToastText = mContext.getString(R.string.possible_bad_connection_toast);
            postToast(connectionErrorToastText);
        }
          */
        e.printStackTrace();
        return GcmNetworkManager.RESULT_FAILURE;
      }
    }


      if(!params.getTag().equals("historical"))
          Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_OK);

    //At this point, the symbols are assumed to be updated (since we didn't return)
    //So we have to notify the widget provider

      Intent quoteUpdatedIntent = new Intent(ACTION_QUOTE_UPDATED);
      mContext.sendBroadcast(quoteUpdatedIntent);
/*
  we won't need the historicalDataLoader. Things will be done in a loop of a local method
  //Attempt to instantiate HistoricalDataLoader
  if(){ //a day has passed since the last download of historical data and historicalDataLoader is not null
      historicalDataLoader = new HistoricalDataLoader();
      historicalDataLoader.currentDataTag = params.getTag(); //The first quote field will be set below

  }
      */


    long historicalTimeStamp = getNewHistoricalTimeStamp();
      //if(false)
      if(params.getTag().equals("add") || (historicalTimeStamp > 0
              /*&& !params.getTag().equals("add")*/ && !likelyLoadingHistoricalData))
      //a day has passed since the last download of historical data and historicalDataLoader is not null
      {

          likelyLoadingHistoricalData = true;

          //Derive the start date and the end date for the historical data to be loaded
          //...
          String startDate = "";
          String endDate = "";
          if(!params.getTag().equals("add")){
              String[] histTimeBounds = Utils.getNewHistoricalTimeBounds(mContext, historicalTimeStamp, HISTORICAL_DAY_RANGE);
              startDate = histTimeBounds[0];
              endDate = histTimeBounds[1];
          }else{
              startDate = Utils.getHistoricalStartDate(mContext);
              endDate = Utils.getHistoricalEndDate(mContext);

              if("".equals(startDate) || "".equals(endDate)){
                  String[] histTimeBounds = Utils.getNewHistoricalTimeBounds(mContext, historicalTimeStamp, HISTORICAL_DAY_RANGE);
                  startDate = histTimeBounds[0];
                  endDate = histTimeBounds[1];
              }
              
          }


          //Kick off the historical data loading
          //...
          String privilegedSymbol = null;
          if(quotesSymbols != null && quotesSymbols.length > 0)
              privilegedSymbol = quotesSymbols[0];
          if(params.getTag().equals("historical")) {

              privilegedSymbol = params.getExtras().getString("symbol");
          }

          if(initQueryCursor == null && quotesSymbols == null)
          {//We load the symbols from database


              initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                      new String[] { "Distinct " + QuoteColumns.SYMBOL }, null,
                      null, null);
          }
          //Declare a linkedList used to store sequences of symbols for querying historical data
          //...
          LinkedList<String> quotesSequences = new LinkedList<String>();

          //Populate the list declare above and make sure the sequence of the privileged symbol is at position 0
          int countSequence = MAX_QUOTE_BACH;
          boolean privilegedQuoteFound = false;
          if(quotesSymbols != null){

              //mStoredSymbols.setLength(0);
              for(int i=0; i< quotesSymbols.length; ){

                  mStoredSymbols.setLength(0);
                  countSequence = MAX_QUOTE_BACH;
                  while (countSequence > 0 && i < quotesSymbols.length){

                      if (privilegedSymbol != null && privilegedSymbol.equals(quotesSymbols[i]))
                          privilegedQuoteFound = true;
                      mStoredSymbols.append("\"" + quotesSymbols[i] + "\",");
                      countSequence --;
                      i++;
                  }

                  if(mStoredSymbols.length() > 0)
                    mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");

                  if(privilegedQuoteFound)
                    quotesSequences.add(0,mStoredSymbols.toString());
                  else
                    quotesSequences.add(mStoredSymbols.toString());

                  privilegedQuoteFound = false;
              }
          }
          else if (initQueryCursor != null) {

              DatabaseUtils.dumpCursor(initQueryCursor);
              initQueryCursor.moveToFirst();
              /*
              for (int i = 0; i < initQueryCursor.getCount(); i++) {
                  mStoredSymbols.append("\"" +
                          initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                  initQueryCursor.moveToNext();
              }
              */
              int i = 0;
              int symbCount = initQueryCursor.getCount();
              String curSymb = null;
              privilegedQuoteFound = false;

              do{
                  mStoredSymbols.setLength(0);
                  countSequence = MAX_QUOTE_BACH;

                  while (countSequence > 0 && i < symbCount){
                      curSymb = initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"));
                      if (privilegedSymbol!= null && privilegedSymbol.equals(curSymb)) {
                          privilegedQuoteFound = true;
                          mStoredSymbols.insert(0,"\"" + curSymb + "\",");
                      }
                      else
                        mStoredSymbols.append("\"" + curSymb + "\",");

                      countSequence --;
                      i++;
                      initQueryCursor.moveToNext();
                  }

                  if(mStoredSymbols.length() != 0)
                    mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");

                  if(privilegedQuoteFound)
                      quotesSequences.add(0,mStoredSymbols.toString());
                  else
                      quotesSequences.add(mStoredSymbols.toString());

                  privilegedQuoteFound = false;

              }while(initQueryCursor.moveToNext());

          }


          //https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.historicaldata%20where%20symbol%20in%20(%22YHOO%22%2C%22AAPL%22%2C%22GOOG%22%2C%22MSFT%22%2C%22AB%22%2C%22ABC%22%2C%22PFK%22%2C%22IBM%22%2C%22KO%22)%20and%20startDate%20%3D%20%222016-01-01%22%20and%20endDate%20%3D%20%222016-04-01%22&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=
          //Iterate the symbolSequence list and load historical data for each sequence

          ContentValues contentValues = new ContentValues();
          // update ISCURRENT to 0 (false) so new data is current
              //contentValues.put(QuoteColumns.ISCURRENT, 0);
              //mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
              //        null, null);

          boolean shouldUpdateTimeTag = false;
          String symbolSequence = null;

          for(int it=0; it < quotesSequences.size(); it++) {

              symbolSequence = quotesSequences.get(it);
              urlStringBuilder.setLength(0);

              try {
                  // Base URL for the Yahoo query
                  urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");

                  urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol " + "in (", "UTF-8"));

              } catch (UnsupportedEncodingException e) {
                  e.printStackTrace();
              }
              //https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.historicaldata%20where%20symbol%20in%20(%22YHOO%22%2C%22AAPL%22%2C%22MSFT%22%2C%22GOOG%22)%20and%20startDate%20%3D%20%222016-04-30%22%20and%20endDate%20%3D%20%222016-06-01%22&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=
              //https://query.yahooapis.com/v1/public/yql?q=select+*+from+yahoo.finance.historicaldata+where+symbol+in+%28%22YHOO%22%2C%22AAPL%22%2C%22GOOG%22%2C%22MSFT%22%29+and+startDate+%3D+%222016-04-30%22+and+endDate+%3D+%222016-06-01%22&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=
              try {
                  urlStringBuilder.append(URLEncoder.encode(symbolSequence, "UTF-8"));
                  String dateRange = " and startDate = "+"\""+startDate+"\"" + " and endDate = "+"\""+endDate+"\"";

                  urlStringBuilder.append(URLEncoder.encode(dateRange, "UTF-8"));
              } catch (UnsupportedEncodingException e) {
                  e.printStackTrace();
              }

              urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                      + "org%2Falltableswithkeys&callback=");

              urlString = urlStringBuilder.toString();

              if(urlString != null) {
                  try {

                      getResponse = fetchData(urlString);
                      if (getResponse == null) {

                          if(params.getTag().equals("historical"))
                            Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_DOWN);
                          //return GcmNetworkManager.RESULT_FAILURE;
                      }
                      result = GcmNetworkManager.RESULT_SUCCESS;
                      try {

                          ////////////////////////////////

                              ArrayList contentVals = null;
                              try {

                                  //read json strings one by one in a loop. Build the new Json string and write it to the db
                                  //if this is for the privilege symbol and the tag is historical, notify with QUOTE__TRANSACTION_STATUS_OK

                                  LinkedList newHitoricalDataJSonObjects = Utils.getNewHitoricalDataJSonObjects(getResponse);

                                  String[] currSymbols = symbolSequence.split("\""); //The strings at odd positions will be the symbols
                                  ArrayList<ContentProviderOperation> batchOperations = new ArrayList<ContentProviderOperation>();
                                  boolean saveTimeRange = false;
                                  for(int i=1; i< currSymbols.length; i+=2){

                                      Object currNewHistoricalJObject = newHitoricalDataJSonObjects.removeFirst();

                                      //read the old historical data json string for the current symbol (may be null)
                                      String oldHistoricalJSon = null;
                                      Integer currId = 0;
                                      //...
                                      initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                              new String[] { "Distinct " + QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.HISTORICAL_BIDPRICE_JSON },
                                              QuoteColumns.SYMBOL+" =? "+ " and "+QuoteColumns.ISCURRENT+" =?", new String[]{currSymbols[i], "1"}, null);

                                      initQueryCursor.moveToFirst();
                                      oldHistoricalJSon = initQueryCursor.getString(initQueryCursor.getColumnIndex(QuoteColumns.HISTORICAL_BIDPRICE_JSON));
                                      currId = initQueryCursor.getInt(initQueryCursor.getColumnIndex(QuoteColumns._ID));
                                      initQueryCursor.close();
                                      //boolean saveTimeRange;
                                      if(i+2 >= currSymbols.length)
                                          saveTimeRange = true;
                                      String mergedHistoricalData = Utils.mergeHistoricalData(oldHistoricalJSon, currNewHistoricalJObject, HISTORICAL_DAY_RANGE, saveTimeRange, mContext);

                                      //Save the merged historical data specifying that the field is up to date (isCurrent == 1)
                                      //...
                                      ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(
                                              QuoteProvider.Quotes.CONTENT_URI);
                                      builder.withSelection(QuoteColumns._ID + "=?", new String[]{"" + currId});
                                      builder.withValue(QuoteColumns.HISTORICAL_BIDPRICE_JSON, mergedHistoricalData);
                                      builder.withValue(QuoteColumns.ISCURRENT, 1);
                                      batchOperations.add(builder.build());
                                      //contentValues = new ContentValues();
                                      //contentValues.put(QuoteColumns.HISTORICAL_BIDPRICE_JSON, mergedHistoricalData);
                                      //contentValues.put(QuoteColumns.ISCURRENT, 1);
                                      //mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                                      //        QuoteColumns.SYMBOL+" =?", new String[]{currSymbols[i]});

                                      //If this is the very first symbol, it's the privileged one and we must notify the Activity if the tag is "historical"
                                      if(i==1 && currSymbols[i].equals(privilegedSymbol) && params.getTag().equals("historical")){

                                              Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_OK);
                                      }

                                      if((it == (quotesSequences.size() - 1) && i+2 >= currSymbols.length && mergedHistoricalData!= null
                                              && !params.getTag().equals("add"))
                                              || Utils.getHistoricalTimeTag(mContext) == 0)
                                      {
                                          shouldUpdateTimeTag = true;
                                      }
                                  }

                                  ContentProviderResult[] cPRs = mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                                          batchOperations);


                                  int i = cPRs.length;
                                  //contentVals = Utils.historicalJsonToContentVals(getResponse);
                              } catch (NullPointerException exp) { //At this point, there is a problem with the response. Probably due to a bad symbol
                                  if (mContext != null) {
                                      final String toastText = mContext.getString(R.string.possible_bad_symbol_toast);
                                      //Toast.makeText(mContext.getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();

                                      postToast(toastText);
                                  }
                                  if (mContext != null && params.getTag().equals("historical"))
                                      Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_BAD_USER_INPUT);
                                  //return GcmNetworkManager.RESULT_FAILURE;
                              } catch (JSONException jexp) {
                                  if (mContext != null && params.getTag().equals("historical"))
                                      Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_INVALID);
                                  //return GcmNetworkManager.RESULT_FAILURE;
                              }

                          if(contentVals != null)
                              mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                                      contentVals);



                        /////////////////////////////
                      } catch (RemoteException | OperationApplicationException e) {
                          Log.e(LOG_TAG, "Error applying batch insert", e);
                          //return GcmNetworkManager.RESULT_FAILURE;
                      }
                  } catch (IOException e) {
                      if (mContext != null && params.getTag().equals("historical")) {

                          if (!Utils.isNetworkAvailable(mContext))
                              Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_UNKNOWN); //So that the activity can detect the status no network available
                          else
                              Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_SERVER_DOWN);
                      }

        /*
        if(mContext != null) {
            String connectionErrorToastText = mContext.getString(R.string.possible_bad_connection_toast);
            postToast(connectionErrorToastText);
        }
          */
                      e.printStackTrace();
                      //return GcmNetworkManager.RESULT_FAILURE;
                  }
              }

          }


          likelyLoadingHistoricalData = false;

          if(shouldUpdateTimeTag){
              //persist the time tag received at the beginning in the sharedPreferences
              //...
              Utils.setHistoricalTimeTag(mContext, historicalTimeStamp);
              //Utils.setHistoricalStartDate(mContext, startDate);
              Utils.setHistoricalEndDate(mContext, endDate);
          }
      }
      else if(params.getTag().equals("historical")){

          //Notify with QUOTE_TRANSACTION_STATUS_OK
      }


      //if(!params.getTag().equals("historical"))
      //  Utils.setLocationStatus(mContext, QUOTE_TRANSACTION_STATUS_OK);
    return result;
  }


  //Returns System.currentTimeMillis() if a day has passed since the last saving of the historical time stamp
  //Otherwise returns -1
  private long getNewHistoricalTimeStamp(){

      long dayMillis = 3600*1000*24; //Number of milliseconds in a day
      long currMillis = System.currentTimeMillis();
      long cm = currMillis;
      long prevMillis = Utils.getHistoricalTimeTag(mContext);
      long millisAdded = (prevMillis + dayMillis);

      if(currMillis > millisAdded){
          return currMillis;
      }

      return -1;
  }

  private void postToast(final String toastMessage){

      Handler handler = new Handler(Looper.getMainLooper());

      handler.post(new Runnable() {

          @Override
          public void run() {
              Toast.makeText(mContext.getApplicationContext(),toastMessage,Toast.LENGTH_SHORT).show();
          }
      });
  }


  @Retention(RetentionPolicy.SOURCE)
  @IntDef({QUOTE_TRANSACTION_STATUS_UNKNOWN,QUOTE_TRANSACTION_STATUS_ATTEMPTING_CONNECTION, QUOTE_TRANSACTION_STATUS_OK
           ,QUOTE_TRANSACTION_STATUS_SERVER_DOWN, QUOTE_TRANSACTION_STATUS_SERVER_INVALID, QUOTE_TRANSACTION_STATUS_UNKNOWN
           ,QUOTE_TRANSACTION_STATUS_BAD_USER_INPUT})
  public @interface QuoteTransactionStatus{}

  public static final int QUOTE_TRANSACTION_STATUS_UNKNOWN = 0;
  public static final int QUOTE_TRANSACTION_STATUS_ATTEMPTING_CONNECTION = 1;
  public static final int QUOTE_TRANSACTION_STATUS_OK = 2;
  public static final int QUOTE_TRANSACTION_STATUS_BAD_USER_INPUT = 3;
  public static final int QUOTE_TRANSACTION_STATUS_SERVER_DOWN = 4;
  public static final int QUOTE_TRANSACTION_STATUS_SERVER_INVALID = 5;
  public static final int QUOTE_TRANSACTION_STATUS_NO_NETWORK_CONNECTION = 6;

}
