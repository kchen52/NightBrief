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

val PhosphorCamera: ImageVector
    get() {
        if (_PhosphorCamera != null) return _PhosphorCamera!!
        
        _PhosphorCamera = ImageVector.Builder(
            name = "camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(208f, 64f)
                horizontalLineTo(176f)
                lineTo(160f, 40f)
                horizontalLineTo(96f)
                lineTo(80f, 64f)
                horizontalLineTo(48f)
                arcTo(16f, 16f, 0f, false, false, 32f, 80f)
                verticalLineTo(192f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, 16f)
                horizontalLineTo(208f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                verticalLineTo(80f)
                arcTo(16f, 16f, 0f, false, false, 208f, 64f)
                close()
                moveTo(128f, 168f)
                arcToRelative(36f, 36f, 0f, true, true, 36f, -36f)
                arcTo(36f, 36f, 0f, false, true, 128f, 168f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(208f, 56f)
                horizontalLineTo(180.28f)
                lineTo(166.65f, 35.56f)
                arcTo(8f, 8f, 0f, false, false, 160f, 32f)
                horizontalLineTo(96f)
                arcToRelative(8f, 8f, 0f, false, false, -6.65f, 3.56f)
                lineTo(75.71f, 56f)
                horizontalLineTo(48f)
                arcTo(24f, 24f, 0f, false, false, 24f, 80f)
                verticalLineTo(192f)
                arcToRelative(24f, 24f, 0f, false, false, 24f, 24f)
                horizontalLineTo(208f)
                arcToRelative(24f, 24f, 0f, false, false, 24f, -24f)
                verticalLineTo(80f)
                arcTo(24f, 24f, 0f, false, false, 208f, 56f)
                close()
                moveToRelative(8f, 136f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                verticalLineTo(80f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, -8f)
                horizontalLineTo(80f)
                arcToRelative(8f, 8f, 0f, false, false, 6.66f, -3.56f)
                lineTo(100.28f, 48f)
                horizontalLineToRelative(55.43f)
                lineToRelative(13.63f, 20.44f)
                arcTo(8f, 8f, 0f, false, false, 176f, 72f)
                horizontalLineToRelative(32f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, 8f)
                close()
                moveTo(128f, 88f)
                arcToRelative(44f, 44f, 0f, true, false, 44f, 44f)
                arcTo(44.05f, 44.05f, 0f, false, false, 128f, 88f)
                close()
                moveToRelative(0f, 72f)
                arcToRelative(28f, 28f, 0f, true, true, 28f, -28f)
                arcTo(28f, 28f, 0f, false, true, 128f, 160f)
                close()
            }
        }.build()
        
        return _PhosphorCamera!!
    }

private var _PhosphorCamera: ImageVector? = null