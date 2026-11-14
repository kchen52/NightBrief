/*
MIT License

Copyright (c) 2020 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package app.nightbrief.app.ui.icons.vendor.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PhosphorDrop: ImageVector
    get() {
        if (_PhosphorDrop != null) return _PhosphorDrop!!
        
        _PhosphorDrop = ImageVector.Builder(
            name = "drop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(208f, 144f)
                arcToRelative(80f, 80f, 0f, false, true, -160f, 0f)
                curveToRelative(0f, -72f, 80f, -128f, 80f, -128f)
                reflectiveCurveTo(208f, 72f, 208f, 144f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(174f, 47.75f)
                arcToRelative(254.19f, 254.19f, 0f, false, false, -41.45f, -38.3f)
                arcToRelative(8f, 8f, 0f, false, false, -9.18f, 0f)
                arcTo(254.19f, 254.19f, 0f, false, false, 82f, 47.75f)
                curveTo(54.51f, 79.32f, 40f, 112.6f, 40f, 144f)
                arcToRelative(88f, 88f, 0f, false, false, 176f, 0f)
                curveTo(216f, 112.6f, 201.49f, 79.32f, 174f, 47.75f)
                close()
                moveTo(128f, 216f)
                arcToRelative(72.08f, 72.08f, 0f, false, true, -72f, -72f)
                curveToRelative(0f, -57.23f, 55.47f, -105f, 72f, -118f)
                curveToRelative(16.53f, 13f, 72f, 60.75f, 72f, 118f)
                arcTo(72.08f, 72.08f, 0f, false, true, 128f, 216f)
                close()
                moveToRelative(55.89f, -62.66f)
                arcToRelative(57.6f, 57.6f, 0f, false, true, -46.56f, 46.55f)
                arcTo(8.75f, 8.75f, 0f, false, true, 136f, 200f)
                arcToRelative(8f, 8f, 0f, false, true, -1.32f, -15.89f)
                curveToRelative(16.57f, -2.79f, 30.63f, -16.85f, 33.44f, -33.45f)
                arcToRelative(8f, 8f, 0f, false, true, 15.78f, 2.68f)
                close()
            }
        }.build()
        
        return _PhosphorDrop!!
    }

private var _PhosphorDrop: ImageVector? = null