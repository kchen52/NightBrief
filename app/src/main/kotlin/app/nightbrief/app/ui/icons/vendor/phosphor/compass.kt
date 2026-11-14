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

val PhosphorCompass: ImageVector
    get() {
        if (_PhosphorCompass != null) return _PhosphorCompass!!
        
        _PhosphorCompass = ImageVector.Builder(
            name = "compass",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(128f, 32f)
                arcToRelative(96f, 96f, 0f, true, false, 96f, 96f)
                arcTo(96f, 96f, 0f, false, false, 128f, 32f)
                close()
                moveToRelative(16f, 112f)
                lineTo(80f, 176f)
                lineToRelative(32f, -64f)
                lineToRelative(64f, -32f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(128f, 24f)
                arcTo(104f, 104f, 0f, true, false, 232f, 128f)
                arcTo(104.11f, 104.11f, 0f, false, false, 128f, 24f)
                close()
                moveToRelative(0f, 192f)
                arcToRelative(88f, 88f, 0f, true, true, 88f, -88f)
                arcTo(88.1f, 88.1f, 0f, false, true, 128f, 216f)
                close()
                moveTo(172.42f, 72.84f)
                lineToRelative(-64f, 32f)
                arcToRelative(8.05f, 8.05f, 0f, false, false, -3.58f, 3.58f)
                lineToRelative(-32f, 64f)
                arcTo(8f, 8f, 0f, false, false, 80f, 184f)
                arcToRelative(8.1f, 8.1f, 0f, false, false, 3.58f, -0.84f)
                lineToRelative(64f, -32f)
                arcToRelative(8.05f, 8.05f, 0f, false, false, 3.58f, -3.58f)
                lineToRelative(32f, -64f)
                arcToRelative(8f, 8f, 0f, false, false, -10.74f, -10.74f)
                close()
                moveTo(138f, 138f)
                lineTo(97.89f, 158.11f)
                lineTo(118f, 118f)
                lineToRelative(40.15f, -20.07f)
                close()
            }
        }.build()
        
        return _PhosphorCompass!!
    }

private var _PhosphorCompass: ImageVector? = null