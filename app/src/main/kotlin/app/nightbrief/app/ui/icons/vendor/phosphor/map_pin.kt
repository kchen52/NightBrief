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

val PhosphorMapPin: ImageVector
    get() {
        if (_PhosphorMapPin != null) return _PhosphorMapPin!!
        
        _PhosphorMapPin = ImageVector.Builder(
            name = "map-pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(128f, 24f)
                arcToRelative(80f, 80f, 0f, false, false, -80f, 80f)
                curveToRelative(0f, 72f, 80f, 128f, 80f, 128f)
                reflectiveCurveToRelative(80f, -56f, 80f, -128f)
                arcTo(80f, 80f, 0f, false, false, 128f, 24f)
                close()
                moveToRelative(0f, 112f)
                arcToRelative(32f, 32f, 0f, true, true, 32f, -32f)
                arcTo(32f, 32f, 0f, false, true, 128f, 136f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(128f, 64f)
                arcToRelative(40f, 40f, 0f, true, false, 40f, 40f)
                arcTo(40f, 40f, 0f, false, false, 128f, 64f)
                close()
                moveToRelative(0f, 64f)
                arcToRelative(24f, 24f, 0f, true, true, 24f, -24f)
                arcTo(24f, 24f, 0f, false, true, 128f, 128f)
                close()
                moveToRelative(0f, -112f)
                arcToRelative(88.1f, 88.1f, 0f, false, false, -88f, 88f)
                curveToRelative(0f, 31.4f, 14.51f, 64.68f, 42f, 96.25f)
                arcToRelative(254.19f, 254.19f, 0f, false, false, 41.45f, 38.3f)
                arcToRelative(8f, 8f, 0f, false, false, 9.18f, 0f)
                arcTo(254.19f, 254.19f, 0f, false, false, 174f, 200.25f)
                curveToRelative(27.45f, -31.57f, 42f, -64.85f, 42f, -96.25f)
                arcTo(88.1f, 88.1f, 0f, false, false, 128f, 16f)
                close()
                moveToRelative(0f, 206f)
                curveToRelative(-16.53f, -13f, -72f, -60.75f, -72f, -118f)
                arcToRelative(72f, 72f, 0f, false, true, 144f, 0f)
                curveTo(200f, 161.23f, 144.53f, 209f, 128f, 222f)
                close()
            }
        }.build()
        
        return _PhosphorMapPin!!
    }

private var _PhosphorMapPin: ImageVector? = null