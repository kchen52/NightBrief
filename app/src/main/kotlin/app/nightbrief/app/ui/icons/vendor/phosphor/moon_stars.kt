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

val PhosphorMoonStars: ImageVector
    get() {
        if (_PhosphorMoonStars != null) return _PhosphorMoonStars!!
        
        _PhosphorMoonStars = ImageVector.Builder(
            name = "moon-stars",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(210.69f, 158.18f)
                arcTo(88f, 88f, 0f, true, true, 97.82f, 45.31f)
                arcTo(96.08f, 96.08f, 0f, false, false, 192f, 160f)
                arcTo(96.78f, 96.78f, 0f, false, false, 210.69f, 158.18f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(240f, 96f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                horizontalLineTo(216f)
                verticalLineToRelative(16f)
                arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                verticalLineTo(104f)
                horizontalLineTo(184f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                horizontalLineToRelative(16f)
                verticalLineTo(72f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                verticalLineTo(88f)
                horizontalLineToRelative(16f)
                arcTo(8f, 8f, 0f, false, true, 240f, 96f)
                close()
                moveTo(144f, 56f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(8f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(56f)
                horizontalLineToRelative(8f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                horizontalLineToRelative(-8f)
                verticalLineTo(32f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-8f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                close()
                moveToRelative(72.77f, 97f)
                arcToRelative(8f, 8f, 0f, false, true, 1.43f, 8f)
                arcTo(96f, 96f, 0f, true, true, 95.07f, 37.8f)
                arcToRelative(8f, 8f, 0f, false, true, 10.6f, 9.06f)
                arcTo(88.07f, 88.07f, 0f, false, false, 209.14f, 150.33f)
                arcTo(8f, 8f, 0f, false, true, 216.77f, 153f)
                close()
                moveToRelative(-19.39f, 14.88f)
                curveToRelative(-1.79f, 0.09f, -3.59f, 0.14f, -5.38f, 0.14f)
                arcTo(104.11f, 104.11f, 0f, false, true, 88f, 64f)
                curveToRelative(0f, -1.79f, 0f, -3.59f, 0.14f, -5.38f)
                arcTo(80f, 80f, 0f, true, false, 197.38f, 167.86f)
                close()
            }
        }.build()
        
        return _PhosphorMoonStars!!
    }

private var _PhosphorMoonStars: ImageVector? = null