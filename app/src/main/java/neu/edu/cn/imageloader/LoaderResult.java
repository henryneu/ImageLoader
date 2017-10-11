package neu.edu.cn.imageloader;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Created by neuHenry on 2017/10/11.
 */

public class LoaderResult {
    public ImageView imageView;
    public String url;
    public Bitmap bitmap;

    public LoaderResult(ImageView imageView, String url, Bitmap bitmap) {
        this.imageView = imageView;
        this.url = url;
        this.bitmap = bitmap;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }
}
