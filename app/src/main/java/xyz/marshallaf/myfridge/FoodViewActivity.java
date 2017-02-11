package xyz.marshallaf.myfridge;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;

import xyz.marshallaf.myfridge.data.FoodContract;
import xyz.marshallaf.myfridge.data.FoodDbHelper;
import xyz.marshallaf.myfridge.data.UnitContract;

/**
 * Created by Andrew Marshall on 2/8/2017.
 */

public class FoodViewActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private final String LOG_TAG = FoodViewActivity.class.getName();

    private Uri mUri;

    private FoodDbHelper mDbHelper;

    private double mAbsoluteAmount;
    private double mAbsolutePrice = -1;
    private int mCurrentUnit;
    private int mCurrentUseUnit;

    private ArrayList<String> mUnitNameArray;
    private ArrayList<Double> mUnitConversionArray;

    private TextView mPriceTextView;
    private TextView mAmountTextView;

    private boolean mDidAmountChange = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);

        // get the passed Uri
        Uri uri = getIntent().getParcelableExtra(FoodContract.FoodEntry.FOOD_URI_KEY);
        if (uri != null) {
            mUri = uri;
            getSupportLoaderManager().initLoader(0, null, this);
        } else {
            // something has gone wrong, so just return to the food list
            Log.e(LOG_TAG, "No Uri passed to View Activity.");
            finish();
        }

        mAmountTextView = (TextView) findViewById(R.id.view_food_amount);
        mPriceTextView = (TextView) findViewById(R.id.view_food_price);

        // get db helper
        mDbHelper = new FoodDbHelper(this);

        findViewById(R.id.view_action_change_unit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showUnitSpinner();
            }
        });

        findViewById(R.id.action_use_food).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showUseDialog();
            }
        });
    }

    private void showUseDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.use_food_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);

        Spinner unitSpinner = (Spinner) view.findViewById(R.id.unit_spinner);

        // create array adapter for spinner
        ArrayAdapter unitSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, mUnitNameArray);

        // set dropdown style
        unitSpinnerAdapter.setDropDownViewResource(R.layout.spinner_dialog_item);

        // bind the adapter to the spinner
        unitSpinner.setAdapter(unitSpinnerAdapter);

        // set spinner position
        unitSpinner.setSelection(mCurrentUnit);
        mCurrentUseUnit = mCurrentUnit;

        // set the on item click listener
        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                Log.d(LOG_TAG, "Position: " + position + ", ID: " + l);
                mCurrentUseUnit = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mCurrentUseUnit = mCurrentUnit;
            }
        });

        final EditText amountInput = (EditText) view.findViewById(R.id.use_food_dialog_input);

        builder.setPositiveButton(R.string.action_use_food_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String amountString = amountInput.getText().toString();
                if (!TextUtils.isEmpty(amountString)) {
                    double amount = Double.parseDouble(amountString);
                    useFood(amount);
                }
                if (dialogInterface != null) {
                    dialogInterface.dismiss();
                }
            }
        });

        builder.setNegativeButton(R.string.action_use_food_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (dialogInterface != null) {
                    dialogInterface.dismiss();
                }
            }
        });

        builder.show();
    }

    private void useFood(double amount) {
        // convert amount used to absolute
        amount *= mUnitConversionArray.get(mCurrentUseUnit);

        // make sure there is enough food
        if (mAbsoluteAmount < amount) {
            Toast.makeText(this, R.string.alert_not_enough_food, Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount != 0) {
            mDidAmountChange = true;
        }

        // subtract from the total
        mAbsoluteAmount -= amount;

        // refresh views
        setAmountAndPrice();
    }

    private void showUnitSpinner() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Convert to");
        String[] units = new String[mUnitNameArray.size()];
        builder.setItems(mUnitNameArray.toArray(units), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mCurrentUnit = i;
                        setAmountAndPrice();
                        dialogInterface.dismiss();
                    }
                });
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit:
                // launch editor activity
                Intent intent = new Intent(this, EditorActivity.class);
                intent.putExtra(FoodContract.FoodEntry.FOOD_URI_KEY, mUri);
                startActivity(intent);
                return true;
            case android.R.id.home:
                // save amount to db
                if (mDidAmountChange) {
                    saveAmount();
                }
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_delete:
                // show delete prompt
                showDeleteDialog();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void saveAmount() {
        ContentValues values = new ContentValues();
        values.put(FoodContract.FoodEntry.COLUMN_AMOUNT, mAbsoluteAmount);
        getContentResolver().update(mUri, values, null, null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mDidAmountChange) {
            saveAmount();
        }
    }

    private void showDeleteDialog() {
        // build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Delete food item?");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (dialogInterface != null) dialogInterface.dismiss();
            }
        });
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                deleteItem();
                finish();
            }
        });

        // show the dialog
        builder.show();
    }

    private void deleteItem() {
        if (mUri != null) {
            getContentResolver().delete(mUri, null, null);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, mUri, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // check for data
        if (data.moveToFirst()) {
            // set name
            ((TextView) findViewById(R.id.view_food_name)).setText(data.getString(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_NAME)));

            // get unit code and string
            mCurrentUnit = data.getInt(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_UNIT));

            // get amount
            mAbsoluteAmount = data.getDouble(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_AMOUNT));

            // check and set price
            String priceString = data.getString(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_PRICE_PER));
            if (!TextUtils.isEmpty(priceString)) {
                mAbsolutePrice = Double.parseDouble(priceString);
            }

            unitSetup(mCurrentUnit);

            // check and set expiration
            String expString = data.getString(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_EXPIRATION));
            if (!TextUtils.isEmpty(expString)) {
                Calendar now = Calendar.getInstance();
                now.setTimeInMillis(Long.parseLong(expString));
                String dateString = (now.get(Calendar.MONTH)+1) + "/" + now.get(Calendar.DAY_OF_MONTH) + "/" + now.get(Calendar.YEAR);
                ((TextView) findViewById(R.id.view_food_expires)).setText(dateString);
            }

            // check and set image
            String photoPath = data.getString(data.getColumnIndex(FoodContract.FoodEntry.COLUMN_PHOTO));
            if (!TextUtils.isEmpty(photoPath)) {
                // TODO: fix this so it decodes after knowing the size and orientation needed, I think via a stream
                // decode image
                Bitmap bitmap = BitmapFactory.decodeFile(photoPath);

                // get image orientation
                try {
                    ExifInterface exif = new ExifInterface(photoPath);
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                    Matrix matrix = new Matrix();
                    switch (orientation) {
                        case 6:
                            matrix.postRotate(90);
                            break;
                        case 3:
                            matrix.postRotate(180);
                            break;
                        case 8:
                            matrix.postRotate(270);
                            break;
                    }
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "problem loading image", e);
                }

                ((ImageView) findViewById(R.id.view_food_image)).setImageBitmap(bitmap);
                (findViewById(R.id.view_text_layout)).setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }
        }
    }

    private void setAmountAndPrice() {
        // get unit code and string
        String unitString = mUnitNameArray.get(mCurrentUnit);

        // convert amount
        double amount = mAbsoluteAmount / mUnitConversionArray.get(mCurrentUnit);
        BigDecimal amountBd = new BigDecimal(amount).setScale(2, BigDecimal.ROUND_HALF_UP);
        String amountString = amountBd.toString();

        // set amount
        String fullAmountString = amountString + " " + unitString;
        mAmountTextView.setText(fullAmountString);

        // set price
        if (mAbsolutePrice != -1) {
            Log.d(LOG_TAG, "Absolute price: " + mAbsolutePrice);
            double price = mAbsolutePrice * mUnitConversionArray.get(mCurrentUnit);
            BigDecimal priceBd = new BigDecimal(price).setScale(2, BigDecimal.ROUND_HALF_UP);
            String priceString = priceBd.toString();
            String fullPriceString = "$" + priceString + " / " + unitString;
            mPriceTextView.setText(fullPriceString);
        }
    }

    private void unitSetup(int unit) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        // get unit type
        String[] columns = new String[] {UnitContract.UnitEntry.COLUMN_TYPE};
        String selection = UnitContract.UnitEntry._ID + "=?";
        String[] selectionArgs = new String[]{String.valueOf(unit)};
        Cursor cursor = db.query(UnitContract.UnitEntry.TABLE_NAME, columns, selection, selectionArgs, null, null, null);
        cursor.moveToFirst();
        int unitType = cursor.getInt(cursor.getColumnIndex(UnitContract.UnitEntry.COLUMN_TYPE));

        // get needed values
        columns = new String[] {UnitContract.UnitEntry._ID, UnitContract.UnitEntry.COLUMN_NAME, UnitContract.UnitEntry.COLUMN_CONVERT};
        selection = UnitContract.UnitEntry.COLUMN_TYPE + "=?";
        selectionArgs = new String[] { String.valueOf(unitType) };
        cursor = db.query(UnitContract.UnitEntry.TABLE_NAME, columns, selection, selectionArgs, null, null, UnitContract.UnitEntry._ID);
        mUnitNameArray = new ArrayList<>();
        mUnitConversionArray = new ArrayList<>();
        while (cursor.moveToNext()) {
            mUnitNameArray.add(cursor.getString(cursor.getColumnIndex(UnitContract.UnitEntry.COLUMN_NAME)));
            mUnitConversionArray.add(cursor.getDouble(cursor.getColumnIndex(UnitContract.UnitEntry.COLUMN_CONVERT)));
            if (cursor.getInt(cursor.getColumnIndex(UnitContract.UnitEntry._ID)) == unit)
                mCurrentUnit = cursor.getPosition();
        }

        // set text views
        setAmountAndPrice();

        cursor.close();
        db.close();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
}
