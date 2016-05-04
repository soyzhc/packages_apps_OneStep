package com.smartisanos.sidebar.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;

public class RecentFileManager implements IClear{

    private volatile static RecentFileManager sInstance;
    public synchronized static RecentFileManager getInstance(Context context){
        if(sInstance == null){
            synchronized(RecentFileManager.class){
                if(sInstance == null){
                    sInstance = new RecentFileManager(context);
                }
            }
        }
        return sInstance;
    }

    private static final String[] thumbCols = new String[] {
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns._ID};

    private static final String DATABASE_NAME = "recent_file";

    private Context mContext;
    private Handler mHandler;
    private List<FileInfo> mList = new ArrayList<FileInfo>();
    private List<RecentUpdateListener> mListeners = new ArrayList<RecentUpdateListener>();

    private ClearDatabaseHelper mDatabaseHelper;
    private RecentFileManager(Context context) {
        mContext = context;
        mHandler = new Handler();
        mDatabaseHelper = new ClearDatabaseHelper(mContext, DATABASE_NAME);
        updateFileList();
        mContext.getContentResolver().registerContentObserver(MediaStore.Files.getContentUri("external"),
                true, new FileObserver(mHandler));
    }

    public List<FileInfo> getFileList(){
        synchronized(RecentFileManager.class){
            return mList;
        }
    }

    private void updateFileList() {
        synchronized (RecentFileManager.class) {
            mList.clear();
            Set<Integer> useless = mDatabaseHelper.getSet();
            Cursor cursor = mContext.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"), thumbCols,
                    null, null, null);
            while (cursor.moveToNext()) {
                FileInfo info = new FileInfo();
                info.filePath = cursor.getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA));
                info.mimeType = cursor.getString(cursor
                                .getColumnIndexOrThrow(MediaStore.Images.ImageColumns.MIME_TYPE));
                info.id = cursor.getInt(cursor
                                .getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID));
                if (info.valid() && !useless.contains(info.id)) {
                    mList.add(info);
                }
            }
            Collections.reverse(mList);
        }
    }

    public void addListener(RecentUpdateListener listener){
        mListeners.add(listener);
    }

    public void removeListener(RecentUpdateListener listener){
        mListeners.remove(listener);
    }

    private void notifyListener(){
        for(RecentUpdateListener lis : mListeners){
            lis.onUpdate();
        }
    }

    private class FileObserver extends ContentObserver{
        public FileObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateFileList();
            notifyListener();
        }
    }

    @Override
    public void clear() {
        synchronized (RecentFileManager.class) {
            for(FileInfo fi : mList){
                mDatabaseHelper.addUselessId(fi.id);
            }
            updateFileList();
            notifyListener();
        }
    }
}