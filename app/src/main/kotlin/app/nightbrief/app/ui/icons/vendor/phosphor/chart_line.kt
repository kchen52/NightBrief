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

val PhosphorChartLine: ImageVector
    get() {
        if (_PhosphorChartLine != null) return _PhosphorChartLine!!
        
        _PhosphorChartLine = ImageVector.Builder(
            name = "chart-line",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(224f, 64f)
                verticalLineTo(208f)
                horizontalLineTo(32f)
                verticalLineTo(48f)
                horizontalLineTo(208f)
                arcTo(16f, 16f, 0f, false, true, 224f, 64f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(232f, 208f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                horizontalLineTo(32f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                verticalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                verticalLineToRelative(94.37f)
                lineTo(90.73f, 98f)
                arcToRelative(8f, 8f, 0f, false, true, 10.07f, -0.38f)
                lineToRelative(58.81f, 44.11f)
                lineTo(218.73f, 90f)
                arcToRelative(8f, 8f, 0f, true, true, 10.54f, 12f)
                lineToRelative(-64f, 56f)
                arcToRelative(8f, 8f, 0f, false, true, -10.07f, 0.38f)
                lineTo(96.39f, 114.29f)
                lineTo(40f, 163.63f)
                verticalLineTo(200f)
                horizontalLineTo(224f)
                arcTo(8f, 8f, 0f, false, true, 232f, 208f)
                close()
            }
        }.build()
        
        return _PhosphorChartLine!!
    }

private var _PhosphorChartLine: ImageVector? = null