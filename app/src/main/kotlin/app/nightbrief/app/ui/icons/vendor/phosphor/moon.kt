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

val PhosphorMoon: ImageVector
    get() {
        if (_PhosphorMoon != null) return _PhosphorMoon!!
        
        _PhosphorMoon = ImageVector.Builder(
            name = "moon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(227.89f, 147.89f)
                arcTo(96f, 96f, 0f, true, true, 108.11f, 28.11f)
                arcTo(96.09f, 96.09f, 0f, false, false, 227.89f, 147.89f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(233.54f, 142.23f)
                arcToRelative(8f, 8f, 0f, false, false, -8f, -2f)
                arcToRelative(88.08f, 88.08f, 0f, false, true, -109.8f, -109.8f)
                arcToRelative(8f, 8f, 0f, false, false, -10f, -10f)
                arcToRelative(104.84f, 104.84f, 0f, false, false, -52.91f, 37f)
                arcTo(104f, 104f, 0f, false, false, 136f, 224f)
                arcToRelative(103.09f, 103.09f, 0f, false, false, 62.52f, -20.88f)
                arcToRelative(104.84f, 104.84f, 0f, false, false, 37f, -52.91f)
                arcTo(8f, 8f, 0f, false, false, 233.54f, 142.23f)
                close()
                moveTo(188.9f, 190.34f)
                arcTo(88f, 88f, 0f, false, true, 65.66f, 67.11f)
                arcToRelative(89f, 89f, 0f, false, true, 31.4f, -26f)
                arcTo(106f, 106f, 0f, false, false, 96f, 56f)
                arcTo(104.11f, 104.11f, 0f, false, false, 200f, 160f)
                arcToRelative(106f, 106f, 0f, false, false, 14.92f, -1.06f)
                arcTo(89f, 89f, 0f, false, true, 188.9f, 190.34f)
                close()
            }
        }.build()
        
        return _PhosphorMoon!!
    }

private var _PhosphorMoon: ImageVector? = null