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

val PhosphorMountains: ImageVector
    get() {
        if (_PhosphorMountains != null) return _PhosphorMountains!!
        
        _PhosphorMountains = ImageVector.Builder(
            name = "mountains",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(144f, 52f)
                arcToRelative(20f, 20f, 0f, true, true, 20f, 20f)
                arcTo(20f, 20f, 0f, false, true, 144f, 52f)
                close()
                moveToRelative(49.44f, 55.92f)
                arcToRelative(8f, 8f, 0f, false, false, -13.77f, 0f)
                lineToRelative(-33f, 55.75f)
                lineToRelative(-21f, -35.67f)
                horizontalLineTo(50.35f)
                lineTo(8f, 200f)
                horizontalLineTo(248f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(164f, 80f)
                arcToRelative(28f, 28f, 0f, true, false, -28f, -28f)
                arcTo(28f, 28f, 0f, false, false, 164f, 80f)
                close()
                moveToRelative(0f, -40f)
                arcToRelative(12f, 12f, 0f, true, true, -12f, 12f)
                arcTo(12f, 12f, 0f, false, true, 164f, 40f)
                close()
                moveToRelative(90.88f, 155.92f)
                lineToRelative(-54.56f, -92.08f)
                arcTo(15.87f, 15.87f, 0f, false, false, 186.55f, 96f)
                horizontalLineToRelative(0f)
                arcToRelative(15.85f, 15.85f, 0f, false, false, -13.76f, 7.84f)
                lineTo(146.63f, 148f)
                lineToRelative(-44.84f, -76.1f)
                arcToRelative(16f, 16f, 0f, false, false, -27.58f, 0f)
                lineTo(1.11f, 195.94f)
                arcTo(8f, 8f, 0f, false, false, 8f, 208f)
                horizontalLineTo(248f)
                arcToRelative(8f, 8f, 0f, false, false, 6.88f, -12.08f)
                close()
                moveTo(88f, 80f)
                lineToRelative(23.57f, 40f)
                horizontalLineTo(64.43f)
                close()
                moveTo(22f, 192f)
                lineToRelative(33f, -56f)
                horizontalLineToRelative(66f)
                lineToRelative(33f, 56f)
                close()
                moveToRelative(150.57f, 0f)
                lineToRelative(-16.66f, -28.28f)
                lineTo(186.55f, 112f)
                lineTo(234f, 192f)
                close()
            }
        }.build()
        
        return _PhosphorMountains!!
    }

private var _PhosphorMountains: ImageVector? = null