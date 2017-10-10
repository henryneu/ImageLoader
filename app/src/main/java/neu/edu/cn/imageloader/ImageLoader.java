package neu.edu.cn.imageloader;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by neuHenry on 2017/10/10.
 */

public class ImageLoader {

    private static final long DISK_CATCH_SIZE = 1024 * 1024 * 50;

    private Context mContext;

    private ImageResizer mImageResizer = new ImageResizer();

    private LruCache<String, Bitmap> mMemoryCache;

    private DiskLruCache mDiskLruCache;

    private boolean mIsDiskLruCacheCreated = false;

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
                mDiskLruCache = DiskLruCache.open(diskCacheDir, getAppVersionCode(mContext), 1, DISK_CATCH_SIZE);
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
        try {
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
