package com.cardio.program;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class databaseList extends ActionBarActivity {

    ListView dataList;
    DatabaseHelper databaseHelper;
    SQLiteDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);

        dataList = (ListView)findViewById(R.id.listView2);

        databaseHelper = new DatabaseHelper(this, infoActivity.DATABASE, null, 1);
        database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        int j = cursor.getCount();
        ArrayList list = new ArrayList();
        String name;

        if (cursor != null) {
            if (cursor.moveToPosition(2)) {
                do {
                    for (String cn : cursor.getColumnNames()) {
                        name = cursor.getString(cursor.getColumnIndex(cn));
                        if ((name != "sqlite_sequence")&&(name != "android_metadata"))
                        {
                            list.add(name);
                        }
                    }
                    //Log.d(LOG_TAG, str);

                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        else Toast.makeText(this, "Пусто", Toast.LENGTH_LONG);

        final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        dataList.setAdapter(adapter);
        dataList.setOnItemClickListener(dataClick);
    }

    private AdapterView.OnItemClickListener dataClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            String name = ((TextView)view).getText().toString();

            Intent i = new Intent(databaseList.this, DataTable.class);

            i.putExtra("NAME", name);
            startActivity(i);
        }
    };

    public class DatabaseHelper extends SQLiteOpenHelper implements BaseColumns {

        public static final String DATABASE_NAME = "cardio_data.db";
        public static final String DATABASE_TABLE = "cardio_table";
        public static final int DATABASE_VERSION = 1;
        public static final String KEY_ID = "_id";
        public static final String LEVEL = "level";
        public static final String TIME = "time";
        private static final String DATABASE_CREATE_SCRIPT = "create table "
                + DATABASE_TABLE + " (" + KEY_ID
                + " integer primary key autoincrement, " + LEVEL
                + " integer, " + TIME + " time); ";

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                              int version) {
            super(context, name, factory, version);
        }

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                              int version, DatabaseErrorHandler errorHandler) {
            super(context, name, factory, version, errorHandler);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE_SCRIPT);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Удаляем старую таблицу и создаём новую
            db.execSQL("DROP TABLE IF IT EXISTS " + DATABASE_TABLE);
            // Создаём новую таблицу
            onCreate(db);
        }
    }
}
