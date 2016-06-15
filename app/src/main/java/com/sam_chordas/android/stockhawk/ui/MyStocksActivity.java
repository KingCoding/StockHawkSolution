package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener{

  /**
   * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
   */

  /**
   * Used to store the last screen title. For use in {@link #restoreActionBar()}.
   */
  private CharSequence mTitle;
  private Intent mServiceIntent;
  private ItemTouchHelper mItemTouchHelper;
  private static final int CURSOR_LOADER_ID = 0;
  private QuoteCursorAdapter mCursorAdapter;
  private Context mContext;
  private Cursor mCursor;
  boolean isConnected;
  TextView emptyMessageTextView;
  RecyclerView recyclerView;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mContext = this;
    ConnectivityManager cm =
        (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    isConnected = activeNetwork != null &&
        activeNetwork.isConnectedOrConnecting();
    setContentView(R.layout.activity_my_stocks);
    // The intent service is for executing immediate pulls from the Yahoo API
    // GCMTaskService can only schedule tasks, they cannot execute immediately
    mServiceIntent = new Intent(this, StockIntentService.class);

    if (savedInstanceState == null){
      // Run the initialize task service so that some stocks appear upon an empty database
      mServiceIntent.putExtra("tag", "init");
      if (isConnected){
        startService(mServiceIntent);
      } else{
        networkToast();
      }

    }
    else{
        int selectedPosition = 0;
        boolean selectionPerformed = false;
        selectedPosition = savedInstanceState.getInt("selectedPosition");
        selectionPerformed = savedInstanceState.getBoolean("selectionPerformed");
        QuoteCursorAdapter.selectedPosition = selectedPosition;
        QuoteCursorAdapter.selectionPerformed = false;
    }
    recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    emptyMessageTextView = (TextView) findViewById(R.id.empty_message_textView);

    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

    mCursorAdapter = new QuoteCursorAdapter(this, null);
    //mCursorAdapter.setRecyclerView(recyclerView);
    final RecyclerView finalRecyclerViewReference = recyclerView;
    recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
            new RecyclerViewItemClickListener.OnItemClickListener() {
              @Override public void onItemClick(View v, int position) {
                //TODO:
                // do something on item click
                //send the intent to the graph Activity with the required parameters which will extract them
                //and send them to the service

                  //int adpPosition = finalRecyclerViewReference.getChildAdapterPosition(v);
                  Cursor c = mCursorAdapter.getCursor();
                  c.moveToFirst();
                  c.moveToPosition(finalRecyclerViewReference.getChildAdapterPosition(v));
                  String currSymbol = c.getString(c.getColumnIndex(QuoteColumns.SYMBOL));
                  Intent intent = new Intent(mContext, LineGraphActivity.class);
                  intent.putExtra(QuoteColumns.SYMBOL, currSymbol);
                  startActivity(intent);


              }
            }));
    recyclerView.setAdapter(mCursorAdapter);


    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.attachToRecyclerView(recyclerView);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (isConnected){
          new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
              .content(R.string.content_test)
              .inputType(InputType.TYPE_CLASS_TEXT)
              .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                @Override public void onInput(MaterialDialog dialog, CharSequence input) {
                  // On FAB click, receive user input. Make sure the stock doesn't already exist
                  // in the DB and proceed accordingly
                  Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                      new String[] { QuoteColumns.SYMBOL }, QuoteColumns.SYMBOL + "= ?",
                      new String[] { input.toString() }, null);
                  if (c.getCount() != 0) {
                    Toast toast =
                        Toast.makeText(MyStocksActivity.this, "This stock is already saved!",
                            Toast.LENGTH_LONG);
                      //we should allow the user to update a saved quote by searching for it.
                      //The selected quote should be preserved on orientation change
                      //Make the selected quote to be persisted even when changing the units
                      //Make it possible for users to navigate previous values of quotes (no)
                      //add an animation to trigger the navigation automatically (no)
                      //Managing a quote should be done through a panel at the bottom/top (no)
                    toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                    toast.show();
                    return;
                  } else {
                    // Add the stock to DB
                    mServiceIntent.putExtra("tag", "add");
                    mServiceIntent.putExtra("symbol", input.toString());
                    startService(mServiceIntent);
                  }
                }
              })
              .show();
        } else {
          networkToast();
        }

      }
    });

    ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
    mItemTouchHelper = new ItemTouchHelper(callback);
    mItemTouchHelper.attachToRecyclerView(recyclerView);

    mTitle = getTitle();
    //We want to make sure the periodic task attempts to run even when there isn't an active connection
    //Because we want it to start running whenever the connection is restored
    //So we remove the initialization of the task from the if block
    //if (isConnected){
      long period = 3600L;
      long flex = 10L; //should be set to couple of minutes because the periodic task might also attempt loading historical data
      String periodicTag = "periodic";

      // create a periodic task to pull stocks once every hour after the app has been opened. This
      // is so Widget data stays up to date.
      PeriodicTask periodicTask = new PeriodicTask.Builder()
          .setService(StockTaskService.class)
          .setPeriod(period)
          .setFlex(flex)
          .setTag(periodicTag)
          .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
          .setRequiresCharging(false)
          .build();
      // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
      // are updated.
      GcmNetworkManager.getInstance(this).schedule(periodicTask);
    //}

      
  }


  @Override
  public void onSaveInstanceState(Bundle instanceState){
      super.onSaveInstanceState(instanceState);
      instanceState.putInt("selectedPosition",QuoteCursorAdapter.selectedPosition);
      instanceState.putBoolean("selectionPerformed",QuoteCursorAdapter.selectionPerformed);
  }

  @Override
  public void onResume() {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    sp.registerOnSharedPreferenceChangeListener(this);
    super.onResume();
    getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
  }

  @Override
  public void onPause(){
      SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
      sp.unregisterOnSharedPreferenceChangeListener(this);
      super.onPause();
  }

  public void networkToast(){
    Toast.makeText(mContext, getString(R.string.network_toast), Toast.LENGTH_SHORT).show();
  }

  public void restoreActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      getMenuInflater().inflate(R.menu.my_stocks, menu);
      restoreActionBar();
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    if (id == R.id.action_change_units){
      // this is for changing stock changes from percent value to dollar value
      Utils.showPercent = !Utils.showPercent;
      this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
      Intent quoteUpdatedIntent = new Intent(StockTaskService.ACTION_QUOTE_UPDATED);
      mContext.sendBroadcast(quoteUpdatedIntent);
    }

    return super.onOptionsItemSelected(item);
  }




  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args){
    // This narrows the return to only the stocks that are most current.
    return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
        new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.CREATED, QuoteColumns.ISUP},
        QuoteColumns.ISCURRENT + " = ?",
        new String[]{"1"},
        null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data){
/*
      if(data!= null && data.getCount() == 0 //&& !isConnected
       ){
          //At this point, we are at the very first time of the app execution, with no connection
          //We should inform the user about the reason for the blank screen
          networkToast();
      }
*/
      if(data == null || (data!= null && data.getCount() == 0))
      {

          recyclerView.setVisibility(View.GONE);
          emptyMessageTextView.setVisibility(View.VISIBLE);
          emptyMessageTextView.setText(getString(R.string.no_quote_available)+getString(R.string.no_quote_saved));
          //Utils.clearSharedPrefVariables(mContext);
      }
      else{
          recyclerView.setVisibility(View.VISIBLE);
          emptyMessageTextView.setVisibility(View.GONE);
      }

      mCursorAdapter.swapCursor(data);
      mCursor = data;
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader){
    mCursorAdapter.swapCursor(null);
  }


  private void displayQuoteTransactionFeedBack(){

     @StockTaskService.QuoteTransactionStatus
              int qts = Utils.getLocationStatus(this);
              int feedBackId = -1;
      switch(qts){

          case StockTaskService.QUOTE_TRANSACTION_STATUS_SERVER_DOWN:
              feedBackId = R.string.server_down;
              break;
          case StockTaskService.QUOTE_TRANSACTION_STATUS_SERVER_INVALID:
              feedBackId = R.string.server_invalid;
              break;
          case StockTaskService.QUOTE_TRANSACTION_STATUS_ATTEMPTING_CONNECTION:
              feedBackId = R.string.attempting_Connection;
              break;
          case StockTaskService.QUOTE_TRANSACTION_STATUS_BAD_USER_INPUT:
              feedBackId = R.string.bad_user_input;
              break;
          case StockTaskService.QUOTE_TRANSACTION_STATUS_NO_NETWORK_CONNECTION:
              if(!Utils.isNetworkAvailable(this)){
                  feedBackId = R.string.no_network_connection;
              }
              break;
          case StockTaskService.QUOTE_TRANSACTION_STATUS_UNKNOWN:
              break;

          default: //OK

      }

      String feedBackMessage = "No problem encountered";
      if(feedBackId != -1) {
          feedBackMessage = getString(feedBackId);

          if (mCursorAdapter.getItemCount() == 0) {
              //Prepend the feedback message with "no quote available message" and set the text of the textView
              //TextView tv = (TextView) findViewById(R.id.empty_message_textView);
              emptyMessageTextView.setText(getString(R.string.no_quote_available) + feedBackMessage);
          } else {
              //Display a toast of the feedBack message
              Toast.makeText(this, feedBackMessage, Toast.LENGTH_SHORT).show();
          }
      }
  }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if(key.equals(getString(R.string.quote_transaction_status_key))){
            displayQuoteTransactionFeedBack();
        }
    }

}