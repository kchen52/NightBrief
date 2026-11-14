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

val PhosphorCrosshair: ImageVector
    get() {
        if (_PhosphorCrosshair != null) return _PhosphorCrosshair!!
        
        _PhosphorCrosshair = ImageVector.Builder(
            name = "crosshair",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(160f, 128f)
                arcToRelative(32f, 32f, 0f, true, true, -32f, -32f)
                arcTo(32f, 32f, 0f, false, true, 160f, 128f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(232f, 120f)
                horizontalLineToRelative(-8.34f)
                arcTo(96.14f, 96.14f, 0f, false, false, 136f, 32.34f)
                verticalLineTo(24f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineToRelative(8.34f)
                arcTo(96.14f, 96.14f, 0f, false, false, 32.34f, 120f)
                horizontalLineTo(24f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(8.34f)
                arcTo(96.14f, 96.14f, 0f, false, false, 120f, 223.66f)
                verticalLineTo(232f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineToRelative(-8.34f)
                arcTo(96.14f, 96.14f, 0f, false, false, 223.66f, 136f)
                horizontalLineTo(232f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveToRelative(-96f, 87.6f)
                verticalLineTo(200f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineToRelative(7.6f)
                arcTo(80.15f, 80.15f, 0f, false, true, 48.4f, 136f)
                horizontalLineTo(56f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                horizontalLineTo(48.4f)
                arcTo(80.15f, 80.15f, 0f, false, true, 120f, 48.4f)
                verticalLineTo(56f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(48.4f)
                arcTo(80.15f, 80.15f, 0f, false, true, 207.6f, 120f)
                horizontalLineTo(200f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(7.6f)
                arcTo(80.15f, 80.15f, 0f, false, true, 136f, 207.6f)
                close()
                moveTo(128f, 88f)
                arcToRelative(40f, 40f, 0f, true, false, 40f, 40f)
                arcTo(40f, 40f, 0f, false, false, 128f, 88f)
                close()
                moveToRelative(0f, 64f)
                arcToRelative(24f, 24f, 0f, true, true, 24f, -24f)
                arcTo(24f, 24f, 0f, false, true, 128f, 152f)
                close()
            }
        }.build()
        
        return _PhosphorCrosshair!!
    }

private var _PhosphorCrosshair: ImageVector? = null