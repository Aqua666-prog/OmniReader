package com.t8rin.tiff_coder

import android.graphics.Bitmap
import org.beyka.tiffbitmapfactory.TiffBitmapFactory
import java.io.File

object TiffCoder {

    fun pageCount(file: File): Int {
        val options = TiffBitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inDirectoryNumber = 0
        }

        TiffBitmapFactory.decodeFile(file, options)

        return options.outDirectoryCount.coerceAtLeast(1)
    }

    fun decode(
        file: File,
        page: Int,
        sampleSize: Int = 1
    ): Bitmap? {
        val options = TiffBitmapFactory.Options().apply {
            inDirectoryNumber = page
            inSampleSize = sampleSize.coerceAtLeast(1)
            inUseOrientationTag = true
        }

        return TiffBitmapFactory.decodeFile(file, options)
    }
}
