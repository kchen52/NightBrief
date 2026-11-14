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

val PhosphorCalendar: ImageVector
    get() {
        if (_PhosphorCalendar != null) return _PhosphorCalendar!!
        
        _PhosphorCalendar = ImageVector.Builder(
            name = "calendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(216f, 48f)
                verticalLineTo(88f)
                horizontalLineTo(40f)
                verticalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, -8f)
                horizontalLineTo(208f)
                arcTo(8f, 8f, 0f, false, true, 216f, 48f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(208f, 32f)
                horizontalLineTo(184f)
                verticalLineTo(24f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineToRelative(8f)
                horizontalLineTo(88f)
                verticalLineTo(24f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineToRelative(8f)
                horizontalLineTo(48f)
                arcTo(16f, 16f, 0f, false, false, 32f, 48f)
                verticalLineTo(208f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, 16f)
                horizontalLineTo(208f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                verticalLineTo(48f)
                arcTo(16f, 16f, 0f, false, false, 208f, 32f)
                close()
                moveTo(72f, 48f)
                verticalLineToRelative(8f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(48f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(8f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(48f)
                horizontalLineToRelative(24f)
                verticalLineTo(80f)
                horizontalLineTo(48f)
                verticalLineTo(48f)
                close()
                moveTo(208f, 208f)
                horizontalLineTo(48f)
                verticalLineTo(96f)
                horizontalLineTo(208f)
                verticalLineTo(208f)
                close()
                moveToRelative(-96f, -88f)
                verticalLineToRelative(64f)
                arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                verticalLineTo(132.94f)
                lineToRelative(-4.42f, 2.22f)
                arcToRelative(8f, 8f, 0f, false, true, -7.16f, -14.32f)
                lineToRelative(16f, -8f)
                arcTo(8f, 8f, 0f, false, true, 112f, 120f)
                close()
                moveToRelative(59.16f, 30.45f)
                lineTo(152f, 176f)
                horizontalLineToRelative(16f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                horizontalLineTo(136f)
                arcToRelative(8f, 8f, 0f, false, true, -6.4f, -12.8f)
                lineToRelative(28.78f, -38.37f)
                arcTo(8f, 8f, 0f, true, false, 145.07f, 132f)
                arcToRelative(8f, 8f, 0f, true, true, -13.85f, -8f)
                arcTo(24f, 24f, 0f, false, true, 176f, 136f)
                arcTo(23.76f, 23.76f, 0f, false, true, 171.16f, 150.45f)
                close()
            }
        }.build()
        
        return _PhosphorCalendar!!
    }

private var _PhosphorCalendar: ImageVector? = null