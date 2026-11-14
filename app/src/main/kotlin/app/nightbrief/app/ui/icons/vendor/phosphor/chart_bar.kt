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

val PhosphorChartBar: ImageVector
    get() {
        if (_PhosphorChartBar != null) return _PhosphorChartBar!!
        
        _PhosphorChartBar = ImageVector.Builder(
            name = "chart-bar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(208f, 40f)
                verticalLineTo(208f)
                horizontalLineTo(152f)
                verticalLineTo(40f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(224f, 200f)
                horizontalLineToRelative(-8f)
                verticalLineTo(40f)
                arcToRelative(8f, 8f, 0f, false, false, -8f, -8f)
                horizontalLineTo(152f)
                arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                verticalLineTo(80f)
                horizontalLineTo(96f)
                arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                verticalLineToRelative(40f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                verticalLineToRelative(64f)
                horizontalLineTo(32f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineTo(224f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveTo(160f, 48f)
                horizontalLineToRelative(40f)
                verticalLineTo(200f)
                horizontalLineTo(160f)
                close()
                moveTo(104f, 96f)
                horizontalLineToRelative(40f)
                verticalLineTo(200f)
                horizontalLineTo(104f)
                close()
                moveTo(56f, 144f)
                horizontalLineTo(88f)
                verticalLineToRelative(56f)
                horizontalLineTo(56f)
                close()
            }
        }.build()
        
        return _PhosphorChartBar!!
    }

private var _PhosphorChartBar: ImageVector? = null