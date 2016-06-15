package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.service.StockTaskService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

  private static String LOG_TAG = Utils.class.getSimpleName();

  public static boolean showPercent = true;

  public static ArrayList quoteJsonToContentVals(String JSON, HashMap<String, String> histMap) throws JSONException {
    ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
    JSONObject jsonObject = null;
    JSONArray resultsArray = null;
    Log.i(LOG_TAG, "GET FB: " +JSON);
    try{
      jsonObject = new JSONObject(JSON);

      if (jsonObject != null && jsonObject.length() != 0){
        jsonObject = jsonObject.getJSONObject("query");
        String created = jsonObject.getString("created");
        int count = Integer.parseInt(jsonObject.getString("count"));
        if (count == 1){
          jsonObject = jsonObject.getJSONObject("results")
              .getJSONObject("quote");
          jsonObject.put("created", created);

          batchOperations.add(buildBatchOperation(jsonObject, histMap.get(jsonObject.getString("symbol"))));
        } else{
          resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

          if (resultsArray != null && resultsArray.length() != 0){
            for (int i = 0; i < resultsArray.length(); i++){
              jsonObject = resultsArray.getJSONObject(i);
              jsonObject.put("created", created);
              batchOperations.add(buildBatchOperation(jsonObject, histMap.get(jsonObject.getString("symbol"))));
            }
          }
        }
      }
      else{
          throw new JSONException("null or empty");
      }
    } catch (JSONException e){
      Log.e(LOG_TAG, "String to JSON failed: " + e);
        throw e;
    }
    catch(NullPointerException npex){
        throw npex;
    }
    return batchOperations;
  }


  public static LinkedList getNewHitoricalDataJSonObjects (String newHitoricalDataJSon) throws JSONException {

      LinkedList jObjects = new LinkedList();
      //Find the array quote of the object results of the object diagnostics of the object query
      //In that array, build several arrays from that, each having the records related to a particular symbol
      //Make sure to delete the fields we don't need to persist for each record.
      JSONObject historicalObjectsWrapper = new JSONObject(newHitoricalDataJSon);
      JSONArray quotesHistData = ((historicalObjectsWrapper.getJSONObject("query")).getJSONObject("results")).getJSONArray("quote");

      String currSymb = null;
      JSONArray currJSArray = new JSONArray();
      JSONObject currJSONObject = null;
      int dataCount = quotesHistData.length();
      for(int i =0; i< dataCount; i++){

          currJSONObject = quotesHistData.getJSONObject(i);
          //We delete the fields we don't need
          currJSONObject.remove("Open");
          currJSONObject.remove("High");
          currJSONObject.remove("Low");
          currJSONObject.remove("Volume");
          currJSONObject.remove("Adj_Close");

          if(!currJSONObject.getString("Symbol").equals(currSymb)){
              if(currJSArray.length() > 0)
                  jObjects.add(currJSArray);
              currJSArray = new JSONArray();
              currSymb = currJSONObject.getString("Symbol");
          }
          else if(i == dataCount-1){
              //Let's add the last data array
              jObjects.add(currJSArray);
          }

          currJSArray.put(currJSONObject);
          currJSONObject.remove("Symbol");

      }

      return jObjects;
  }

  //newHistoricalDataJSonObject is a JSONArray
  //hint: Use calendar to find the number of days between 2 dates
  public static String mergeHistoricalData(String oldHistoricalDataJSon, Object newHistoricalDataJSonObject, int historicalRange, boolean saveTimeRange, Context mContext) throws JSONException{

      String mergedHistoricalDataJSon = null;
      JSONArray oldJSA = null;
      JSONArray jsonArray = (JSONArray) newHistoricalDataJSonObject;


      if(oldHistoricalDataJSon != null && !oldHistoricalDataJSon.equals("DEFAULT")) {
          oldJSA = new JSONArray(oldHistoricalDataJSon);
          SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd");

          //Let's remove the records that are out of range from the old array
          boolean shouldMerge = false;
          try {
              Date latestDate = myFormat.parse(jsonArray.getJSONObject(0).getString("Date"));
              //long latestDateMillis = latestDate.getTime();
              shouldMerge = true;
              Date currDate = null;
              for (int i = 0; i < oldJSA.length(); i++) {
                  currDate = myFormat.parse(oldJSA.getJSONObject(i).getString("Date"));
                  if (getDifferenceDays(currDate, latestDate) > historicalRange) {
                      //We discard all records from this point
                      int j = 0;
                      JSONArray retainedJSOs = new JSONArray();

                      for (j = 0; j < i; j++) {
                          retainedJSOs.put(oldJSA.get(j));
                      }
                      oldJSA = retainedJSOs;

                      break;
                  }

                  //if (i == oldJSA.length()-1)
                  //    shouldMerge = false;
              }

          } catch (ParseException e) {
              e.printStackTrace();
          }

          //we insert the old records in the new array
          if (shouldMerge)
              for (int i = 0; i < oldJSA.length(); i++) {
                  jsonArray.put(oldJSA.get(i));
              }

          mergedHistoricalDataJSon = jsonArray.toString();
      }
      else{//This is likely the very first time when nothing was saved in the db
          mergedHistoricalDataJSon = jsonArray.toString();
      }

      if(saveTimeRange){

          Utils.setHistoricalStartDate(mContext, jsonArray.getJSONObject(jsonArray.length()-1).getString("Date"));
          //Utils.setHistoricalEndDate(mContext, jsonArray.getJSONObject(0).getString("Date"));
      }

      //find the range of days covered


      return mergedHistoricalDataJSon;
  }

  public static void extractGraphData(String histBirPriceJSON, LinkedList<String>labels, LinkedList<Float> values){


      try {
          JSONArray graphData = new JSONArray(histBirPriceJSON);

          /*
          if(graphData.length() > 0)
          {
              labels = new String[graphData.length()];
              values = new float[graphData.length()];
          }
          */

          SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd");
          SimpleDateFormat finalFormat = new SimpleDateFormat("yyyy-MMM-dd");
          Date d = null;

          for(int i=0; i< graphData.length(); i++){

              d = myFormat.parse(graphData.getJSONObject(i).getString("Date"));
              labels.add(finalFormat.format(d));
              values.add(Float.parseFloat(graphData.getJSONObject(i).getString("Close")));
          }

      } catch (JSONException e) {
          e.printStackTrace();
      } catch (ParseException e) {
          e.printStackTrace();
      }
  }


  public static long getDifferenceDays(Date d1, Date d2) {
    long diff = d2.getTime() - d1.getTime();
    long diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
    return diffDays;
  }

  public static String[] getNewHistoricalTimeBounds(Context context, long nextHistTimeTag, int histRange){


      String[] histTimeBounds = new String[2];

      Long currHistTimeTag = getHistoricalTimeTag(context);

      Date endDate = new Date(nextHistTimeTag);
      Date startDate = new Date(currHistTimeTag);

      if(getDifferenceDays(startDate, endDate) > histRange) {

          //The following decomposition is to avoid overflow
          long millisInDay = 3600 * 1000 * 24;
          long histRangeLong = histRange;
          long millisInHistRange = millisInDay * histRangeLong;
          long newCurrTimeTag = nextHistTimeTag - millisInHistRange;
          startDate = new Date(newCurrTimeTag);
      }


      Calendar cal = Calendar.getInstance();
      cal.setTime(endDate);
      int nextYear = cal.get(Calendar.YEAR);
      int nextMonth = cal.get(Calendar.MONTH)+1;
      int nextDays = cal.get(Calendar.DAY_OF_MONTH);

      histTimeBounds[1] = (nextMonth < 10)? ""+nextYear+"-0"+nextMonth+"-"+nextDays : ""+nextYear+"-"+nextMonth+"-"+nextDays;

      cal.setTime(startDate);
      int currYear = cal.get(Calendar.YEAR);
      int currMonth = cal.get(Calendar.MONTH)+1;
      int currDays = cal.get(Calendar.DAY_OF_MONTH);

      histTimeBounds[0] = (currMonth < 10)? ""+currYear+"-0"+currMonth+"-"+currDays : ""+currYear+"-"+currMonth+"-"+currDays;

      return histTimeBounds;
  }

  public static String truncateBidPrice(String bidPrice){
    bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
    return bidPrice;
  }

  public static String truncateChange(String change, boolean isPercentChange){
    String weight = change.substring(0,1);
    String ampersand = "";
    if (isPercentChange){
      ampersand = change.substring(change.length() - 1, change.length());
      change = change.substring(0, change.length() - 1);
    }
    change = change.substring(1, change.length());
    double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
    change = String.format("%.2f", round);
    StringBuffer changeBuffer = new StringBuffer(change);
    changeBuffer.insert(0, weight);
    changeBuffer.append(ampersand);
    change = changeBuffer.toString();
    return change;
  }

  public static ContentProviderOperation buildBatchOperation(JSONObject jsonObject, String currHist){
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
        QuoteProvider.Quotes.CONTENT_URI);
    try {


    if(jsonObject.isNull("Change")|| jsonObject.isNull("symbol")
            || jsonObject.isNull("Bid") || jsonObject.isNull("ChangeinPercent")
            || jsonObject.isNull("created")){
        throw new NullPointerException("a json value is unexpectedly null");
    }
    else {
        String change = jsonObject.getString("Change");
        builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
        builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(jsonObject.getString("Bid")));
        builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                jsonObject.getString("ChangeinPercent"), true));
        builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
        builder.withValue(QuoteColumns.ISCURRENT, 1);
        builder.withValue(QuoteColumns.CREATED, jsonObject.getString("created"));

        builder.withValue(QuoteColumns.HISTORICAL_BIDPRICE_JSON, currHist);

        if (change.charAt(0) == '-') {
            builder.withValue(QuoteColumns.ISUP, 0);
        } else {
            builder.withValue(QuoteColumns.ISUP, 1);
        }
    }
    } catch (JSONException e){
      e.printStackTrace();
    }
    return builder.build();
  }




  public static boolean isNetworkAvailable(Context mContext){

      ConnectivityManager cm =
              (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      return (activeNetwork != null &&
              activeNetwork.isConnectedOrConnecting());
  }

  public static void setLocationStatus(Context c, @StockTaskService.QuoteTransactionStatus
                                                    int quoteTransactionStatus){

      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
      SharedPreferences.Editor spe = sp.edit();
      spe.putInt(c.getString(R.string.quote_transaction_status_key), quoteTransactionStatus);
      spe.commit();
  }
    @SuppressWarnings("ResourceType") //To remove the compile error which complains that the return type should match one of the annotation values
  public static @StockTaskService.QuoteTransactionStatus int getLocationStatus(Context c){

      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);

      return sp.getInt(c.getString(R.string.quote_transaction_status_key), StockTaskService.QUOTE_TRANSACTION_STATUS_UNKNOWN);
  }

  public static void setHistoricalTimeTag(Context c, long historicalTimeTag){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putLong(c.getString(R.string.historical_time_tag_key), historicalTimeTag);
        spe.commit();
  }

  public static long getHistoricalTimeTag(Context c){

      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);

      return sp.getLong(c.getString(R.string.historical_time_tag_key), 0);
  }

    public static void setHistoricalStartDate(Context c, String historicalStartDate){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putString(c.getString(R.string.historical_start_date_key), historicalStartDate);
        spe.commit();
    }

    public static String getHistoricalStartDate(Context c){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);

        return sp.getString(c.getString(R.string.historical_start_date_key), "");
    }

    public static void setHistoricalEndDate(Context c, String historicalEndDate){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putString(c.getString(R.string.historical_end_date_key), historicalEndDate);
        spe.commit();
    }

    public static String getHistoricalEndDate(Context c){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);

        return sp.getString(c.getString(R.string.historical_end_date_key), "");
    }

    public static void clearSharedPrefVariables(Context c){

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.remove(c.getString(R.string.historical_time_tag_key));
        spe.remove(c.getString(R.string.historical_start_date_key));
        spe.remove(c.getString(R.string.historical_end_date_key));
        spe.commit();
    }

}
