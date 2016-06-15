package com.sam_chordas.android.stockhawk.ui;


import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.db.chart.Tools;
import com.db.chart.model.LineSet;
import com.db.chart.view.AxisController;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockTaskService;

import java.util.LinkedList;

public class LineGraphActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int CURSOR_LOADER_ID = 0;
    private Cursor mCursor;
    private LineChartView lineChartView;
    private View lineChartViewGroup;
    private TextView emptyTextView;
    private String currSymbol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_line_graph);

        lineChartView = (LineChartView)findViewById(R.id.linechart);
        lineChartViewGroup = (View)findViewById(R.id.linechart_viewGroup);
        emptyTextView = (TextView) findViewById(R.id.linechart_empty_message_textView);
        //Read the current symbol from the intent
        currSymbol = getIntent().getStringExtra(QuoteColumns.SYMBOL);

        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_line_graph, menu);
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

        return super.onOptionsItemSelected(item);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        // This narrows the return to only the stocks that are most current.

        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.HISTORICAL_BIDPRICE_JSON},
                QuoteColumns.SYMBOL + " = ?" + " and " + QuoteColumns.ISCURRENT + " = ?",
                new String[]{currSymbol, "1"},
                null);

        //return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        //...
        mCursor = data;
        boolean historyReaday = true;
        if(!(data == null || (data!= null && data.getCount() == 0))) {

            lineChartViewGroup.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);


            LinkedList<String> labs = new LinkedList<String>();
            LinkedList<Float> vals = new LinkedList<Float>();

            if(mCursor.moveToFirst() && mCursor.getString(mCursor.getColumnIndex(QuoteColumns.SYMBOL))!=null) {
                String symbol = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.SYMBOL));
                String histBidPriceJSON = mCursor.getString(mCursor.getColumnIndex(QuoteColumns.HISTORICAL_BIDPRICE_JSON));

                //Call Utils method to build labels and values using the histBidPriceJSON
                //...
                Utils.extractGraphData(histBidPriceJSON, labs, vals);

                //if(vals.size() > 0 && labs.size() > 0)
                String[] labels = new String[labs.size()];
                float[] values = new float[labs.size()];
                for(int i=0; i<labs.size(); i++){
                    labels[i] = labs.get(i);
                    values[i] = vals.get(i);
                }

                if (labs != null && vals != null && vals.size() > 0 && labs.size() > 0) {
                    //Set the header textViews
                    TextView lineChartTitleTextView = (TextView) findViewById(R.id.linechart_title_textView);
                    lineChartTitleTextView.setText(getString(R.string.linechart_title_prepend) + currSymbol);
                    TextView lineChartHistoryRangeTextView = (TextView) findViewById(R.id.linechart_history_textView);
                    lineChartHistoryRangeTextView.setText("From " + labels[labels.length - 1] + " To " + labels[0]);



                    //Format the labels for display
                    int step = 4;

                    for (int i = 0; i < labels.length; i++) {

                        String monthLabel = labels[i].split("-", 2)[1];

                        if ((i % step) != 0)
                            labels[i] = ""; //monthLabel.split("-", 2)[1];
                        else
                            labels[i] = monthLabel;
                    }

                    //Revert the arrays

                    for(int i=0; i<= (values.length/2); i++){

                        float tmpVal = values[i];
                        values[i] = values[values.length-1-i];
                        values[values.length-1-i] = tmpVal;

                        String tmpLab = labels[i];
                        labels[i] = labels[values.length-1-i];
                        labels[values.length-1-i] = tmpLab;
                    }

                    //Find the maximum and minimum values
                    float minVal = values[0],maxVal = values[0];

                    for(float val: values)
                    {
                        if(val > maxVal)
                            maxVal = val;
                        if(val< minVal)
                            minVal = val;
                    }

                    minVal = Math.max(0f, minVal - 4f);
                    maxVal = maxVal + 4f;

                    LineSet stockDataSet = new LineSet(labels, values);
                    stockDataSet.setColor(Color.parseColor("#0000FF"))
                    .setThickness(3)
                    .setDotsColor(Color.parseColor("#0B0B3B"));

                    Paint p = new Paint();
                    p.setColor(Color.parseColor("#0080FF"));

                    lineChartView.setYLabels(AxisController.LabelPosition.OUTSIDE)
                    .setYAxis(false)
                    .setLabelsColor(Color.WHITE)
                    .setBorderSpacing(Tools.fromDpToPx(12))
                    .setGrid(ChartView.GridType.FULL, 10, 10,p)
                    .setAxisBorderValues(Math.round(minVal), Math.round(maxVal))
                    .addData(stockDataSet);

                    //lineChartView.addData(stockDataSet);

                    lineChartView.show();




                }
                else{
                    historyReaday = false;
                }
            }
            else{
                historyReaday = false;
            }
        }
        else{
            historyReaday = false;
        }

        if(!historyReaday){

            lineChartViewGroup.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
            emptyTextView.setText(getString(R.string.no_history_data_available) + getString(R.string.historical_data_not_yet_loaded));
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

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

            if (mCursor.getCount() == 0) {
                //Prepend the feedback message with "no quote available message" and set the text of the textView
                //TextView tv = (TextView) findViewById(R.id.empty_message_textView);
                emptyTextView.setText(getString(R.string.no_history_data_available) + feedBackMessage);
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
