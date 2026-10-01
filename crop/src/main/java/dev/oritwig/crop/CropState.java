/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */


// Adapted 2026-10-01: lift state from MediaController; remove protocol serialization.
package dev.oritwig.crop;
import android.graphics.Matrix;

public class CropState {

        public float cropPx;
        public float cropPy;
        public float cropScale = 1;
        public float cropRotate;
        public float cropPw = 1;
        public float cropPh = 1;
        public int transformWidth;
        public int transformHeight;
        public int transformRotation;
        public boolean mirrored;

        public float stateScale;
        public float scale;
        public Matrix matrix;
        public int width;
        public int height;
        public boolean freeform;
        public float lockedAspectRatio;
        public int orientation;

        public Matrix useMatrix;

        public boolean initied;

        @Override
        public CropState clone() {
            CropState cloned = new CropState();

            cloned.cropPx = this.cropPx;
            cloned.cropPy = this.cropPy;
            cloned.cropScale = this.cropScale;
            cloned.cropRotate = this.cropRotate;
            cloned.cropPw = this.cropPw;
            cloned.cropPh = this.cropPh;
            cloned.transformWidth = this.transformWidth;
            cloned.transformHeight = this.transformHeight;
            cloned.transformRotation = this.transformRotation;
            cloned.mirrored = this.mirrored;

            cloned.stateScale = this.stateScale;
            cloned.scale = this.scale;
            cloned.matrix = this.matrix;
            cloned.width = this.width;
            cloned.height = this.height;
            cloned.freeform = this.freeform;
            cloned.lockedAspectRatio = this.lockedAspectRatio;
            cloned.orientation = this.orientation;

            cloned.initied = this.initied;
            cloned.useMatrix = this.useMatrix;
            return cloned;
        }

        public boolean isEmpty() {
            return (matrix == null || matrix.isIdentity()) && (useMatrix == null || useMatrix.isIdentity()) && cropPw == 1 && cropPh == 1 &&
                    cropScale == 1 && cropRotate == 0 && transformWidth == 0 && transformHeight == 0 &&
                    transformRotation == 0 && !mirrored && stateScale == 0 && scale == 0 && width == 0 && height == 0 && !freeform && lockedAspectRatio == 0;

        }
    }
