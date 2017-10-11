package neu.edu.cn.imageloader;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neuHenry on 2017/10/10.
 */

public class ImageLoader {

    private static final String TAG = "ImageLoader";

    private static final long DISK_CATCH_SIZE = 1024 * 1024 * 50;

    private static final int TAG_KEY_URL = R.id.imageloader_url;

    private static final int MESSAGE_POST_RESULT = 200;

    private Context mContext;

    private ImageResizer mImageResizer = new ImageResizer();

    private LruCache<String, Bitmap> mMemoryCache;

    private DiskLruCache mDiskLruCache;

    private boolean mIsDiskLruCacheCreated = false;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            Bitmap bitmap = result.bitmap;
            String url = (String) imageView.getTag(TAG_KEY_URL);
            if (url.equals(result.url)) {
                imageView.setImageBitmap(bitmap);
            } else {
                Log.w(TAG, "set image bitmap,but url has changed, ignored!");
            }
        }
    };

    private static final ThreadFactory mThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CPU_COUNT + 1, CPU_COUNT * 2 + 1, 10L,
            TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(), mThreadFactory);

    public ImageLoader(Context context) {
        mContext = context;
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        // 创建内存缓存，大小为当前进程的可用内存的1/8
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
        // 获取缓存文件的缓存路径
        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (getUsableSpace(diskCacheDir) > DISK_CATCH_SIZE) {
            try {
                // 创建磁盘缓存，缓存大小为50M
                mDiskLruCache = DiskLruCache.open(diskCacheDir, getAppVersionCode(mContext), 1, DISK_CATCH_SIZE);
                // 磁盘缓存已创建 true
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    /**
     * @param url 图片的url地址
     * @param imageView 显示加载到的图片的View
     * @param reqWidth ImageView所需的宽度值
     * @param reqHeight ImageView所需的高度值
     */
    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        String key = hashKeyForDisk(url);
        imageView.setTag(TAG_KEY_URL, url);
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, url, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        // 首先在内存缓存中查找，若找到返回
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null) {
            return bitmap;
        }
        // 然后在磁盘缓存中查找，若找到返回
        bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
        if (bitmap != null) {
            return bitmap;
        }
        // 最后去网络中拉取
        bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
        if (bitmap == null && !mIsDiskLruCacheCreated) {
            Log.w(TAG, "encounter error, DiskLruCache is not created.");
            bitmap = downLoadBitmapFromUrl(url);
        }
        return bitmap;
    }

    private Bitmap downLoadBitmapFromUrl(String urlStr) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        BufferedInputStream bis = null;
        try {
            final URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(connection.getInputStream(), 8 * 1024);
            bitmap = BitmapFactory.decodeStream(bis);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error in downloadBitmap:" + e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit network from UI Thread!");
        }
        String key = hashKeyForDisk(url);
        try {
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
            if (editor != null) {
                OutputStream outputStream = editor.newOutputStream(0);
                if (downLoadUrlToStream(url, outputStream)) {
                    editor.commit();
                } else {
                    editor.abort();
                }
                mDiskLruCache.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hashKeyForDisk(url);
        try {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if (snapshot != null) {
                FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(0);
                FileDescriptor fileDescriptor = fileInputStream.getFD();
                bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
                if (bitmap != null) {
                    addBitmapToMemoryCache(key, bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String url) {
        final String key = hashKeyForDisk(url);
        Bitmap bitmap = getBitmapFromMemoryCache(key);
        return bitmap;
    }

    /**
     * @param context 上下文环境
     * @return 创建一个新的ImageLoader实例
     */
    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    /**
     * @param urlString 下载地址
     * @param outputStream 写入到本地的管道流
     * @return
     */
    public boolean downLoadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection connection = null;
        BufferedOutputStream bos = null;
        BufferedInputStream bis = null;
        try {
            final URL uri = new URL(urlString);
            connection = (HttpURLConnection) uri.openConnection();
            bis = new BufferedInputStream(connection.getInputStream(), 8 * 1024);
            bos = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = bis.read()) != -1) {
                bos.write(b);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (bos != null) {
                    bos.close();
                }
                if (bis != null) {
                    bis.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * @param key 待编码的字符串
     * @return 经MD5编码后的字符串
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * @param mContext 上下文环境
     * @return 当前应用程序的版本号
     */
    public int getAppVersionCode(Context mContext) {
        try {
            PackageInfo info = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private long getUsableSpace(File filePath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return filePath.getUsableSpace();
        }
        final StatFs statfs = new StatFs(filePath.getPath());
        return (long) statfs.getBlockSize() * (long) statfs.getAvailableBlocks();
    }

    /**
     * @param mContext   传递的上下文环境
     * @param uniqueName 缓存文件名
     * @return 获取到的缓存路径
     */
    public File getDiskCacheDir(Context mContext, String uniqueName) {
        final String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = mContext.getExternalCacheDir().getPath();
        } else {
            cachePath = mContext.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }
}
