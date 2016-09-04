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
import android.widget.TableLayout;
import android.widget.TextView;

public class DataTable extends ActionBarActivity {

    SQLiteDatabase database;
    DatabaseHelper helper;
    TextView output, label;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_table);

        output = (TextView)findViewById(R.id.output);
        label = (TextView)findViewById(R.id.label);
        output.setText("");

        Intent intent = getIntent();
        String name = intent.getStringExtra("NAME");

        label.setText(name);

        helper = new DatabaseHelper(this, infoActivity.DATABASE, null, 1);
        database = helper.getReadableDatabase();

        Cursor cursor = database.query(name, new String[]{DatabaseHelper.LEVEL,
                        DatabaseHelper.TIME},
                null, null,
                null, null, null) ;

        String level, time;

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    time = cursor.getString(cursor.getColumnIndex(DatabaseHelper.TIME));
                    level = cursor.getString(cursor.getColumnIndex(DatabaseHelper.LEVEL));
                    String str = time + " - " + level + "\n";
                    output.append(str);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
    }

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
