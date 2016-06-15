package com.sam_chordas.android.stockhawk.widget;

import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;

/**
 * Created by owner on 11/05/2016.
 */
public class QuoteWidgetRemoteViewsService extends RemoteViewsService {


    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {

        StockRemoteViewsFactory  stockRemoteViewsFactory = new StockRemoteViewsFactory();
        stockRemoteViewsFactory.packageName = intent.getStringExtra(QuoteWidgetProvider.CONTEXT_PACKAGE_NAME);
        return stockRemoteViewsFactory;
    }

    private class StockRemoteViewsFactory implements RemoteViewsFactory{

        private Cursor stockData = null;
        private String packageName;
        @Override
        public void onCreate() {

        }

        @Override
        public void onDataSetChanged() {

            //Let's close the previous cursor
            if(stockData!= null)
                stockData.close();

            //We want to make sure that the privileges of the calling thread's process do not
            //cause the interruption of the execution (if they are not satisfying).
            //So we clear the process id for the calling thread so that it can be treated
            //As a thread of our process which has the right permissions

            final long identity = Binder.clearCallingIdentity();

            stockData = getContentResolver().query(
                    QuoteProvider.Quotes.CONTENT_URI,
                    new String[] {QuoteColumns._ID,QuoteColumns.SYMBOL,QuoteColumns.BIDPRICE,
                            QuoteColumns.PERCENT_CHANGE,QuoteColumns.CHANGE,QuoteColumns.ISUP},
                    QuoteColumns.ISCURRENT + " = ?",new String[]{"1"},null);

            Binder.restoreCallingIdentity(identity);
        }

        @Override
        public void onDestroy() {

        }

        @Override
        public int getCount() {

            if(stockData == null)
                return 0;
            else
                return stockData.getCount();
        }

        @Override
        public RemoteViews getViewAt(int position) {

            if(stockData == null || position < 0 || position >= stockData.getCount()||
                    !stockData.moveToPosition(position))
                return null;

            RemoteViews stockViews = new RemoteViews(packageName, R.layout.widget_collection_item);

            stockViews.setTextViewText(R.id.stock_symbol, stockData.getString(stockData.getColumnIndex(QuoteColumns.SYMBOL)));

            if(Utils.showPercent)
                stockViews.setTextViewText(R.id.change, stockData.getString(stockData.getColumnIndex(QuoteColumns.PERCENT_CHANGE)));
            else
                stockViews.setTextViewText(R.id.change, stockData.getString(stockData.getColumnIndex(QuoteColumns.CHANGE)));

            if(stockData.getInt(stockData.getColumnIndex(QuoteColumns.ISUP))== 1)
                stockViews.setInt(R.id.change, getResources().getString(R.string.background_resource_setter), R.drawable.percent_change_pill_green);
            else
                stockViews.setInt(R.id.change, getResources().getString(R.string.background_resource_setter), R.drawable.percent_change_pill_red);
            

            final Intent fillIntent = new Intent();
            fillIntent.putExtra(QuoteColumns.SYMBOL, stockData.getString(stockData.getColumnIndex(QuoteColumns.SYMBOL)));
            stockViews.setOnClickFillInIntent(R.id.widget_list_item, fillIntent);

            return stockViews;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {

            return 1;
        }

        @Override
        public long getItemId(int position) {

            if(stockData!=null){
                if(stockData.moveToPosition(position)){
                    return stockData.getLong(0); // the id of the record is at column 0
                }
            }
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}

