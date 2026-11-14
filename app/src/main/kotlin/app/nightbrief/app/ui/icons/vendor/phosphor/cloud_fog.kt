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

val PhosphorCloudFog: ImageVector
    get() {
        if (_PhosphorCloudFog != null) return _PhosphorCloudFog!!
        
        _PhosphorCloudFog = ImageVector.Builder(
            name = "cloud-fog",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(224f, 100f)
                arcToRelative(68f, 68f, 0f, false, true, -68f, 68f)
                horizontalLineTo(76f)
                arcTo(44f, 44f, 0f, true, true, 90.2f, 82.34f)
                verticalLineToRelative(0.11f)
                arcTo(68.06f, 68.06f, 0f, false, true, 224f, 100f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(120f, 208f)
                horizontalLineTo(72f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                horizontalLineToRelative(48f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                close()
                moveToRelative(64f, -16f)
                horizontalLineTo(160f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(24f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveToRelative(-24f, 32f)
                horizontalLineTo(104f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(56f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveToRelative(72f, -124f)
                arcToRelative(76.08f, 76.08f, 0f, false, true, -76f, 76f)
                horizontalLineTo(76f)
                arcTo(52f, 52f, 0f, false, true, 76f, 72f)
                arcToRelative(53.26f, 53.26f, 0f, false, true, 8.92f, 0.76f)
                arcTo(76.08f, 76.08f, 0f, false, true, 232f, 100f)
                close()
                moveToRelative(-16f, 0f)
                arcTo(60.06f, 60.06f, 0f, false, false, 96f, 96.46f)
                arcToRelative(8f, 8f, 0f, false, true, -16f, -0.92f)
                quadToRelative(0.21f, -3.66f, 0.77f, -7.23f)
                arcTo(38.11f, 38.11f, 0f, false, false, 76f, 88f)
                arcToRelative(36f, 36f, 0f, false, false, 0f, 72f)
                horizontalLineToRelative(80f)
                arcTo(60.07f, 60.07f, 0f, false, false, 216f, 100f)
                close()
            }
        }.build()
        
        return _PhosphorCloudFog!!
    }

private var _PhosphorCloudFog: ImageVector? = null