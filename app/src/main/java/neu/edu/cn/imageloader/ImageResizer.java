package neu.edu.cn.imageloader;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by neuHenry on 2017/10/10.
 */

public class ImageResizer {

    public ImageResizer() {
    }

    /**
     * @param res 待加载的资源文件
     * @param resId 待加载的资源文件id
     * @param reqWidth ImageView所需的宽度值
     * @param reqHeight ImageView所需的高度值
     * @return 加载出的所需宽高值的Bitmap对象
     */
    public Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        // 根据采样率的规则计算出采样率
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * @param fd 待加载的文件描述符
     * @param reqWidth ImageView所需的宽度值
     * @param reqHeight ImageView所需的高度值
     * @return 加载出的所需宽高值的Bitmap对象
     */
    public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    /**
     * @param options BitmapFactory.Options用来对图片进行采样缩放
     * @param reqWidth ImageView所需的宽度值
     * @param reqHeight ImageView所需的高度值
     * @return 计算得到的采样率
     */
    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }
        // 获取图片的原始宽高信息
        int inSampleSize = 1;
        final int width = options.outWidth;
        final int height = options.outHeight;
        // 如果原始宽高大于View所指定的宽高值，则需计算采样率
        if (width > reqWidth || height > reqHeight) {
            final int halfWidth = width / 2;
            final int halfHeight = height / 2;
            while ((halfWidth / inSampleSize) >= reqWidth && (halfHeight / inSampleSize) >= reqHeight) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
