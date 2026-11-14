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

val PhosphorPlanet: ImageVector
    get() {
        if (_PhosphorPlanet != null) return _PhosphorPlanet!!
        
        _PhosphorPlanet = ImageVector.Builder(
            name = "planet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.2f,
            ) {
                moveTo(216f, 128f)
                arcToRelative(88f, 88f, 0f, true, true, -88f, -88f)
                arcTo(88f, 88f, 0f, false, true, 216f, 128f)
                close()
            }
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(245.11f, 60.68f)
                curveToRelative(-7.65f, -13.19f, -27.84f, -16.16f, -58.5f, -8.66f)
                arcTo(95.93f, 95.93f, 0f, false, false, 32f, 128f)
                arcToRelative(98f, 98f, 0f, false, false, 0.78f, 12.31f)
                curveTo(5.09f, 169f, 5.49f, 186f, 10.9f, 195.32f)
                curveTo(16f, 204.16f, 26.64f, 208f, 40.64f, 208f)
                arcToRelative(124.11f, 124.11f, 0f, false, false, 28.79f, -4f)
                arcTo(95.93f, 95.93f, 0f, false, false, 224f, 128f)
                arcToRelative(97.08f, 97.08f, 0f, false, false, -0.77f, -12.25f)
                curveToRelative(12.5f, -13f, 20.82f, -25.35f, 23.65f, -35.92f)
                curveTo(248.83f, 72.51f, 248.24f, 66.07f, 245.11f, 60.68f)
                close()
                moveTo(128f, 48f)
                arcToRelative(80.11f, 80.11f, 0f, false, true, 78f, 62.2f)
                curveToRelative(-17.06f, 16.06f, -40.15f, 32.53f, -62.07f, 45.13f)
                curveTo(116.38f, 171.14f, 92.48f, 181f, 73.42f, 186.4f)
                arcTo(79.94f, 79.94f, 0f, false, true, 128f, 48f)
                close()
                moveTo(24.74f, 187.29f)
                curveToRelative(-1.46f, -2.51f, -0.65f, -7.24f, 2.22f, -13f)
                arcToRelative(79.05f, 79.05f, 0f, false, true, 10.29f, -15.05f)
                arcToRelative(96f, 96f, 0f, false, false, 18f, 31.32f)
                curveTo(38f, 193.46f, 27.24f, 191.61f, 24.74f, 187.29f)
                close()
                moveTo(128f, 208f)
                arcToRelative(79.45f, 79.45f, 0f, false, true, -38.56f, -9.94f)
                arcToRelative(370f, 370f, 0f, false, false, 62.43f, -28.86f)
                curveToRelative(21.58f, -12.39f, 40.68f, -25.82f, 56.07f, -39.08f)
                arcTo(80.07f, 80.07f, 0f, false, true, 128f, 208f)
                close()
                moveTo(231.42f, 75.69f)
                curveToRelative(-1.7f, 6.31f, -6.19f, 13.53f, -12.63f, 21.13f)
                arcToRelative(95.69f, 95.69f, 0f, false, false, -18f, -31.35f)
                curveToRelative(14.21f, -2.35f, 27.37f, -2.17f, 30.5f, 3.24f)
                curveTo(232.19f, 70.28f, 232.24f, 72.63f, 231.42f, 75.69f)
                close()
            }
        }.build()
        
        return _PhosphorPlanet!!
    }

private var _PhosphorPlanet: ImageVector? = null