package com.sam_chordas.android.stockhawk.rest;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.sam_chordas.android.stockhawk.touch_helper.ItemTouchHelperAdapter;
import com.sam_chordas.android.stockhawk.touch_helper.ItemTouchHelperViewHolder;

import java.util.LinkedList;

/**
 * Created by sam_chordas on 10/6/15.
 *  Credit to skyfishjy gist:
 *    https://gist.github.com/skyfishjy/443b7448f59be978bc59
 * for the code structure
 */
public class QuoteCursorAdapter extends CursorRecyclerViewAdapter<QuoteCursorAdapter.ViewHolder>
    implements ItemTouchHelperAdapter{

  private static Context mContext;
  private static Typeface robotoLight;
  private static RecyclerView recyclerView;
  private int firstItemViewPosition = 0;
  private int boundPosition = -1;
  public static int selectedPosition = -1;
  public static boolean selectionPerformed = false;
  private static ViewHolder toBeCleared;
  //private final OnStartDragListener mDragListener;
  private boolean isPercent;

  private int evenIndicator;

  private LinkedList<ViewHolder> viewHolders;

  public QuoteCursorAdapter(Context context, Cursor cursor){
    super(context, cursor);
    //mDragListener = dragListener;
    mContext = context;
    viewHolders = new LinkedList<ViewHolder>();
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
    robotoLight = Typeface.createFromAsset(mContext.getAssets(), "fonts/Roboto-Light.ttf");
    View itemView = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.list_item_quote, parent, false);
    ViewHolder vh = new ViewHolder(itemView);

    //Set the background colors appropriately

    //int backColor = (evenIndicator%2 == 0) ? Color.parseColor("#086A87"):Color.parseColor("#29088A");
    //evenIndicator = (evenIndicator+1)%2;
    ViewGroup vg = (ViewGroup)itemView.findViewById(R.id.list_item_wrapper);
    vh.parents.add(vg);
    vg = (ViewGroup)itemView.findViewById(R.id.list_item_main_data);
    vh.parents.add(vg);
    vg = (ViewGroup)itemView.findViewById(R.id.list_item_numbers);
    vh.parents.add(vg);
    //vh.setBackColors(backColor);
    if(!viewHolders.contains(vh))
        viewHolders.add(vh);
    return vh;
  }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {

        boundPosition = position;

        /*
        int currFirstIemViewPosition = recyclerView.getChildLayoutPosition(recyclerView.getChildAt(0));
        View adjacent = null;

        if(currFirstIemViewPosition < firstItemViewPosition)
        { //A scroll towards the top

        }
        else{

        }
        */
        super.onBindViewHolder(viewHolder, position);

    }


  @Override
  public void onBindViewHolder(final ViewHolder viewHolder, final Cursor cursor){
    viewHolder.symbol.setText(cursor.getString(cursor.getColumnIndex("symbol")));
    viewHolder.bidPrice.setText(cursor.getString(cursor.getColumnIndex("bid_price")));
    viewHolder.created.setText(cursor.getString(cursor.getColumnIndex(QuoteColumns.CREATED)));
    int sdk = Build.VERSION.SDK_INT;
    if (cursor.getInt(cursor.getColumnIndex("is_up")) == 1){
      if (sdk < Build.VERSION_CODES.JELLY_BEAN){
        viewHolder.change.setBackgroundDrawable(
            mContext.getResources().getDrawable(R.drawable.percent_change_pill_green));
      }else {
        viewHolder.change.setBackground(
            mContext.getResources().getDrawable(R.drawable.percent_change_pill_green));
      }
    } else{
      if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
        viewHolder.change.setBackgroundDrawable(
            mContext.getResources().getDrawable(R.drawable.percent_change_pill_red));
      } else{
        viewHolder.change.setBackground(
            mContext.getResources().getDrawable(R.drawable.percent_change_pill_red));
      }
    }
    if (Utils.showPercent){
      viewHolder.change.setText(cursor.getString(cursor.getColumnIndex("percent_change")));
    } else{
      viewHolder.change.setText(cursor.getString(cursor.getColumnIndex("change")));
    }
    /*
    int currFirstIemViewPosition = recyclerView.getChildLayoutPosition(recyclerView.getChildAt(0));

    if(firstItemViewPosition == currFirstIemViewPosition){
        viewHolder.parityPositionIndicator = evenIndicator;
        evenIndicator = (evenIndicator+1)%2;
        viewHolder.setBackColors();
        firstItemViewPosition = currFirstIemViewPosition;
    }
    else if(currFirstIemViewPosition != -1){
        View adjacentChild = null;
        ViewHolder adjacentViewHolder = null;
        if(currFirstIemViewPosition < firstItemViewPosition){
        //A scroll towards the top (exposing item view above) was made
        //We must consider that the new item view is adjacent to the first item view
            adjacentChild = recyclerView.getChildAt(0);
            adjacentViewHolder = getAdjacentViewHolder(adjacentChild);
            viewHolder.parityPositionIndicator = (adjacentViewHolder.parityPositionIndicator + 1) % 2;
        }
        else{
            adjacentChild = recyclerView.getChildAt(recyclerView.getChildCount()-1);
            adjacentViewHolder = getAdjacentViewHolder(adjacentChild);
            viewHolder.parityPositionIndicator = (adjacentViewHolder.parityPositionIndicator + 1) % 2;
        }
        viewHolder.setBackColors();
        firstItemViewPosition = currFirstIemViewPosition;
        /*
        firstItemViewPosition = currFirstIemViewPosition;
        int numViewHolders = recyclerView.getChildCount();

            for (int i = 0; i < numViewHolders; i++) {
                View currChild = (((ViewGroup) (recyclerView)).getChildAt(i));
                View adjacentChild = null;
                if (currChild == viewHolder.itemView) {
                    adjacentChild = getVisibleAdjacentChild(i);
                    ViewHolder adjacentViewHolder = getAdjacentViewHolder(adjacentChild);
                    viewHolder.parityPositionIndicator = (adjacentViewHolder.parityPositionIndicator + 1) % 2;
                    break;
                }
            }
        */
      /*
    }
    else if(currFirstIemViewPosition == -1){
        viewHolder.parityPositionIndicator = evenIndicator;
        evenIndicator = (evenIndicator+1)%2;
        viewHolder.setBackColors();
    }
    */
    viewHolder.parityPositionIndicator = boundPosition%2;
    //boundPosition = -1;
      //int backColor = (evenIndicator%2 == 0) ? Color.parseColor("#086A87"):Color.parseColor("#29088A");
      //evenIndicator = (evenIndicator+1)%2;
      viewHolder.setBackColors();

      if(!selectionPerformed && selectedPosition != -1){
          if(boundPosition == selectedPosition) {
              viewHolder.onItemSelected();
              //viewHolder.itemView.performClick();
              //recyclerView.call
              toBeCleared = viewHolder;
              selectionPerformed = true;
          }

      }
      else if(selectedPosition == -1){
          selectionPerformed = true;
      }
  }

  private ViewHolder getAdjacentViewHolder(View adjacentView){
      ViewHolder adjViewHolder = null;
      for(ViewHolder viewHolder : viewHolders)
          if(viewHolder.itemView == adjacentView)
              adjViewHolder = viewHolder;

      return adjViewHolder;
  }

  private View getVisibleAdjacentChild(int currChildIndex){

      View adjacentChild = null;

      adjacentChild = (((ViewGroup)(recyclerView)).getChildAt(currChildIndex + 1));

      if(adjacentChild != null){

          if(adjacentChild.getTop() >= 0 || adjacentChild.getBottom() <= recyclerView.getBottom())
              ;
          else
              adjacentChild = null;
      }

      if(adjacentChild == null){
          adjacentChild = (((ViewGroup)(recyclerView)).getChildAt(currChildIndex - 1));

          if(adjacentChild != null){

              if(adjacentChild.getTop() >= 0 || adjacentChild.getBottom() <= recyclerView.getBottom())
                  ;
              else
                  adjacentChild = null;
          }
      }

      return adjacentChild;
  }

  @Override public void onItemDismiss(int position) {
    Cursor c = getCursor();
    c.moveToPosition(position);
    String symbol = c.getString(c.getColumnIndex(QuoteColumns.SYMBOL));
    mContext.getContentResolver().delete(QuoteProvider.Quotes.withSymbol(symbol), null, null);
    notifyItemRemoved(position);

    if(position == 0 ) {

        Cursor initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                new String[] { "Distinct " + QuoteColumns.SYMBOL},
                QuoteColumns.ISCURRENT+" =?", new String[]{ "1"}, null);
        if(initQueryCursor != null && initQueryCursor.getCount()==0)
            Utils.clearSharedPrefVariables(mContext);

    }
    /*
    for(int i=0; i< viewHolders.size(); i++){
        ViewHolder vh = viewHolders.get(i);
            if(vh!=null){
                int backColor = (evenIndicator%2 == 0) ? Color.parseColor("#086A87"):Color.parseColor("#29088A");
                evenIndicator = (evenIndicator+1)%2;
                vh.setBackColors(backColor);
        }
    }
      */

    //evenIndicator = (evenIndicator + 1)%2;
    //notifyDataSetChanged();
    //int ic = getItemCount();
    //notifyItemRangeChanged(0, ic);

      Intent quoteUpdatedIntent = new Intent(StockTaskService.ACTION_QUOTE_UPDATED);
      mContext.sendBroadcast(quoteUpdatedIntent);

  }

  @Override public int getItemCount() {
    return super.getItemCount();
  }

  private static void setBackColor(int color, View view){


      view.setBackgroundColor(color);
      //view.invalidate();
      //int sdk = Build.VERSION.SDK_INT;

/*
     if(isBackgroundElseIsFont) {
         if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
             view.setBackgroundDrawable(
                     mContext.getResources().getDrawable(color));
         } else {
             view.setBackground(
                     mContext.getResources().getDrawable(color));
         }
     }else{

     }
      */
  }

  public void setRecyclerView(RecyclerView recyclerView){

      this.recyclerView = recyclerView;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder
      implements ItemTouchHelperViewHolder, View.OnClickListener{

    public LinkedList<ViewGroup> parents;
    public int parityPositionIndicator;
    private boolean selected;
    public final View itemView;
    public final TextView symbol;
    public final TextView bidPrice;
    public final TextView change;
    public final TextView created;
    public ViewHolder(View itemView){
      super(itemView);
      this.itemView = itemView;
      parents = new LinkedList<ViewGroup>();
      symbol = (TextView) itemView.findViewById(R.id.stock_symbol);
      symbol.setTypeface(robotoLight);
      bidPrice = (TextView) itemView.findViewById(R.id.bid_price);
      change = (TextView) itemView.findViewById(R.id.change);
      created = (TextView) itemView.findViewById(R.id.created);
    }

    @Override
    public void onItemSelected(){
      //if(!selected) {
          itemView.setBackgroundColor(Color.LTGRAY);
        /*
          selected = true;
          if (selectionPerformed) {
              selectedPosition = recyclerView.getChildLayoutPosition(itemView);
          }
          if (toBeCleared != null) {
              toBeCleared.onItemClear();
              toBeCleared = null;
          }
        */
      //}
    }

    @Override
    public void onItemClear(){
      int backColor = (parityPositionIndicator%2 == 0) ? Color.parseColor("#086A87"):Color.parseColor("#29088A");
      itemView.setBackgroundColor(backColor);
      selectedPosition = -1;
      selected = false;
    }

    @Override
    public void onClick(View v) {

    }

    public void setBackColors() {
        if(!selected) {
            int backColor = (parityPositionIndicator % 2 == 0) ? Color.parseColor("#086A87") : Color.parseColor("#29088A");
            for (ViewGroup vg : parents)
                setBackColor(backColor, vg);

            setBackColor(backColor, symbol);
            setBackColor(backColor, bidPrice);
            setBackColor(backColor, created);
        }
    }
  }

}
