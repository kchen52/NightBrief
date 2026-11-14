/*
MIT License

Copyright (c) 2020-2026 Paweł Kuna

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

package app.nightbrief.app.ui.icons.vendor.tabler

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TablerStarFilled: ImageVector
    get() {
        if (_TablerStarFilled != null) return _TablerStarFilled!!
        
        _TablerStarFilled = ImageVector.Builder(
            name = "star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(8.243f, 7.34f)
                lineToRelative(-6.38f, 0.925f)
                lineToRelative(-0.113f, 0.023f)
                arcToRelative(1f, 1f, 0f, false, false, -0.44f, 1.684f)
                lineToRelative(4.622f, 4.499f)
                lineToRelative(-1.09f, 6.355f)
                lineToRelative(-0.013f, 0.11f)
                arcToRelative(1f, 1f, 0f, false, false, 1.464f, 0.944f)
                lineToRelative(5.706f, -3f)
                lineToRelative(5.693f, 3f)
                lineToRelative(0.1f, 0.046f)
                arcToRelative(1f, 1f, 0f, false, false, 1.352f, -1.1f)
                lineToRelative(-1.091f, -6.355f)
                lineToRelative(4.624f, -4.5f)
                lineToRelative(0.078f, -0.085f)
                arcToRelative(1f, 1f, 0f, false, false, -0.633f, -1.62f)
                lineToRelative(-6.38f, -0.926f)
                lineToRelative(-2.852f, -5.78f)
                arcToRelative(1f, 1f, 0f, false, false, -1.794f, 0f)
                lineToRelative(-2.853f, 5.78f)
                close()
            }
        }.build()
        
        return _TablerStarFilled!!
    }

private var _TablerStarFilled: ImageVector? = null