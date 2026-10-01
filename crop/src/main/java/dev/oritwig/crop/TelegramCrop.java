/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */


// Adapted 2026-10-01: isolated PhotoViewer.createCroppedBitmap; only model/logging names changed.
package dev.oritwig.crop;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

public final class TelegramCrop {
    private TelegramCrop() {}
    public static Bitmap createCroppedBitmap(Bitmap bitmap, CropState cropState, int[] extraTransform, boolean mirror) {
        try {
            int tr = (cropState.transformRotation + (extraTransform != null ? extraTransform[0] : 0)) % 360;
            int inv = extraTransform != null && extraTransform.length > 1 ? extraTransform[1] : 0;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int fw = w, rotatedW = w;
            int fh = h, rotatedH = h;
            if (tr == 90 || tr == 270) {
                int temp = fw;
                fw = rotatedW = fh;
                fh = rotatedH = temp;
            }
            fw *= cropState.cropPw;
            fh *= cropState.cropPh;
            Bitmap canvasBitmap = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
            Matrix matrix = new Matrix();
            matrix.postTranslate(-w / 2, -h / 2);
            if (mirror && cropState.mirrored) {
                if (tr == 90 || tr == 270) {
                    matrix.postScale(1, -1);
                } else {
                    matrix.postScale(-1, 1);
                }
            }
            if (inv == 1) {
                matrix.postScale(-1, 1);
            } else if (inv == 2) {
                matrix.postScale(1, -1);
            }
            matrix.postRotate(cropState.cropRotate + tr);
            matrix.postTranslate(cropState.cropPx * rotatedW, cropState.cropPy * rotatedH);
            matrix.postScale(cropState.cropScale, cropState.cropScale);
            matrix.postTranslate(fw / 2, fh / 2);
            Canvas canvas = new Canvas(canvasBitmap);
            canvas.drawBitmap(bitmap, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
            return canvasBitmap;
        } catch (Throwable e) {
            android.util.Log.e("TelegramCrop", "Crop render failed", e);
        }
        return null;
    }
}
