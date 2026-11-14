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

val PhosphorCloud: ImageVector
    get() {
        if (_PhosphorCloud != null) return _PhosphorCloud!!
        
        _PhosphorCloud = ImageVector.Builder(
            name = "cloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(240f, 128f)
                arcToRelative(80f, 80f, 0f, false, true, -80f, 80f)
                horizontalLineTo(72f)
                arcTo(56f, 56f, 0f, true, true, 85.92f, 97.74f)
                lineToRelative(0f, 0.1f)
                arcTo(80f, 80f, 0f, false, true, 240f, 128f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(160f, 40f)
                arcTo(88.09f, 88.09f, 0f, false, false, 81.29f, 88.67f)
                arcTo(64f, 64f, 0f, true, false, 72f, 216f)
                horizontalLineToRelative(88f)
                arcToRelative(88f, 88f, 0f, false, false, 0f, -176f)
                close()
                moveToRelative(0f, 160f)
                horizontalLineTo(72f)
                arcToRelative(48f, 48f, 0f, false, true, 0f, -96f)
                curveToRelative(1.1f, 0f, 2.2f, 0f, 3.29f, 0.11f)
                arcTo(88f, 88f, 0f, false, false, 72f, 128f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                arcToRelative(72f, 72f, 0f, true, true, 72f, 72f)
                close()
            }
        }.build()
        
        return _PhosphorCloud!!
    }

private var _PhosphorCloud: ImageVector? = null