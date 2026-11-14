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

val PhosphorShootingStar: ImageVector
    get() {
        if (_PhosphorShootingStar != null) return _PhosphorShootingStar!!
        
        _PhosphorShootingStar = ImageVector.Builder(
            name = "shooting-star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(230.08f, 78.26f)
                lineToRelative(-31.84f, 26.88f)
                lineTo(208f, 145.33f)
                arcToRelative(5.46f, 5.46f, 0f, false, true, -8.19f, 5.86f)
                lineTo(164f, 129.66f)
                lineToRelative(-35.78f, 21.53f)
                arcToRelative(5.46f, 5.46f, 0f, false, true, -8.19f, -5.86f)
                lineToRelative(9.73f, -40.19f)
                lineTo(97.92f, 78.26f)
                arcToRelative(5.38f, 5.38f, 0f, false, true, 3.13f, -9.48f)
                lineToRelative(41.79f, -3.31f)
                lineToRelative(16.1f, -38.14f)
                arcToRelative(5.51f, 5.51f, 0f, false, true, 10.12f, 0f)
                lineToRelative(16.1f, 38.14f)
                lineTo(227f, 68.78f)
                arcTo(5.38f, 5.38f, 0f, false, true, 230.08f, 78.26f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(239.35f, 70.08f)
                arcToRelative(13.41f, 13.41f, 0f, false, false, -11.77f, -9.28f)
                lineToRelative(-36.94f, -2.92f)
                lineTo(176.43f, 24.22f)
                arcToRelative(13.51f, 13.51f, 0f, false, false, -24.86f, 0f)
                lineTo(137.36f, 57.88f)
                lineTo(100.42f, 60.8f)
                arcToRelative(13.39f, 13.39f, 0f, false, false, -7.66f, 23.58f)
                lineToRelative(28.06f, 23.68f)
                lineToRelative(-8.56f, 35.39f)
                arcToRelative(13.32f, 13.32f, 0f, false, false, 5.1f, 13.91f)
                arcToRelative(13.51f, 13.51f, 0f, false, false, 15f, 0.69f)
                lineTo(164f, 139f)
                lineToRelative(31.65f, 19.06f)
                arcToRelative(13.54f, 13.54f, 0f, false, false, 15f, -0.69f)
                arcToRelative(13.34f, 13.34f, 0f, false, false, 5.09f, -13.91f)
                lineToRelative(-8.56f, -35.39f)
                lineToRelative(28.06f, -23.68f)
                arcTo(13.32f, 13.32f, 0f, false, false, 239.35f, 70.08f)
                close()
                moveTo(193.08f, 99f)
                arcToRelative(8f, 8f, 0f, false, false, -2.61f, 8f)
                lineToRelative(8.28f, 34.21f)
                lineTo(168.13f, 122.8f)
                arcToRelative(8f, 8f, 0f, false, false, -8.25f, 0f)
                lineToRelative(-30.62f, 18.43f)
                lineTo(137.54f, 107f)
                arcToRelative(8f, 8f, 0f, false, false, -2.62f, -8f)
                lineTo(108f, 76.26f)
                lineToRelative(35.52f, -2.81f)
                arcToRelative(8f, 8f, 0f, false, false, 6.74f, -4.87f)
                lineTo(164f, 35.91f)
                lineToRelative(13.79f, 32.67f)
                arcToRelative(8f, 8f, 0f, false, false, 6.74f, 4.87f)
                lineToRelative(35.53f, 2.81f)
                close()
                moveToRelative(-105f, 24.18f)
                lineTo(29.66f, 181.66f)
                arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                lineToRelative(58.45f, -58.45f)
                arcToRelative(8f, 8f, 0f, false, true, 11.32f, 11.32f)
                close()
                moveToRelative(10.81f, 49.87f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 11.31f)
                lineTo(45.66f, 237.66f)
                arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                lineToRelative(53.27f, -53.26f)
                arcTo(8f, 8f, 0f, false, true, 98.92f, 173.08f)
                close()
                moveToRelative(73f, -1f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 11.32f)
                lineToRelative(-54.28f, 54.28f)
                arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                lineToRelative(54.29f, -54.28f)
                arcTo(8f, 8f, 0f, false, true, 171.94f, 172.06f)
                close()
            }
        }.build()
        
        return _PhosphorShootingStar!!
    }

private var _PhosphorShootingStar: ImageVector? = null