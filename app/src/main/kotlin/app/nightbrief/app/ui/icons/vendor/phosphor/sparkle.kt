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

val PhosphorSparkle: ImageVector
    get() {
        if (_PhosphorSparkle != null) return _PhosphorSparkle!!
        
        _PhosphorSparkle = ImageVector.Builder(
            name = "sparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(194.82f, 151.43f)
                lineToRelative(-55.09f, 20.3f)
                lineToRelative(-20.3f, 55.09f)
                arcToRelative(7.92f, 7.92f, 0f, false, true, -14.86f, 0f)
                lineToRelative(-20.3f, -55.09f)
                lineToRelative(-55.09f, -20.3f)
                arcToRelative(7.92f, 7.92f, 0f, false, true, 0f, -14.86f)
                lineToRelative(55.09f, -20.3f)
                lineToRelative(20.3f, -55.09f)
                arcToRelative(7.92f, 7.92f, 0f, false, true, 14.86f, 0f)
                lineToRelative(20.3f, 55.09f)
                lineToRelative(55.09f, 20.3f)
                arcTo(7.92f, 7.92f, 0f, false, true, 194.82f, 151.43f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(197.58f, 129.06f)
                lineTo(146f, 110f)
                lineToRelative(-19f, -51.62f)
                arcToRelative(15.92f, 15.92f, 0f, false, false, -29.88f, 0f)
                lineTo(78f, 110f)
                lineToRelative(-51.62f, 19f)
                arcToRelative(15.92f, 15.92f, 0f, false, false, 0f, 29.88f)
                lineTo(78f, 178f)
                lineToRelative(19f, 51.62f)
                arcToRelative(15.92f, 15.92f, 0f, false, false, 29.88f, 0f)
                lineTo(146f, 178f)
                lineToRelative(51.62f, -19f)
                arcToRelative(15.92f, 15.92f, 0f, false, false, 0f, -29.88f)
                close()
                moveTo(137f, 164.22f)
                arcToRelative(8f, 8f, 0f, false, false, -4.74f, 4.74f)
                lineTo(112f, 223.85f)
                lineTo(91.78f, 169f)
                arcTo(8f, 8f, 0f, false, false, 87f, 164.22f)
                lineTo(32.15f, 144f)
                lineTo(87f, 123.78f)
                arcTo(8f, 8f, 0f, false, false, 91.78f, 119f)
                lineTo(112f, 64.15f)
                lineTo(132.22f, 119f)
                arcToRelative(8f, 8f, 0f, false, false, 4.74f, 4.74f)
                lineTo(191.85f, 144f)
                close()
                moveTo(144f, 40f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, -8f)
                horizontalLineToRelative(16f)
                verticalLineTo(16f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                verticalLineTo(32f)
                horizontalLineToRelative(16f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                horizontalLineTo(184f)
                verticalLineTo(64f)
                arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                verticalLineTo(48f)
                horizontalLineTo(152f)
                arcTo(8f, 8f, 0f, false, true, 144f, 40f)
                close()
                moveTo(248f, 88f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(8f)
                arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                verticalLineTo(96f)
                horizontalLineToRelative(-8f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                horizontalLineToRelative(8f)
                verticalLineTo(72f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(8f)
                arcTo(8f, 8f, 0f, false, true, 248f, 88f)
                close()
            }
        }.build()
        
        return _PhosphorSparkle!!
    }

private var _PhosphorSparkle: ImageVector? = null