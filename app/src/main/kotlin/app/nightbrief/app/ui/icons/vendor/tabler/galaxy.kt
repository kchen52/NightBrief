/*
MIT License

Copyright (c) 2020-2026 Paweł Kuna

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

package app.nightbrief.app.ui.icons.vendor.tabler

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TablerGalaxy: ImageVector
    get() {
        if (_TablerGalaxy != null) return _TablerGalaxy!!
        
        _TablerGalaxy = ImageVector.Builder(
            name = "galaxy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                curveToRelative(-1.333f, 1f, -2f, 2.5f, -2f, 4.5f)
                curveToRelative(0f, 3f, 2f, 4.5f, 2f, 4.5f)
                reflectiveCurveToRelative(2f, 1.5f, 2f, 4.5f)
                curveToRelative(0f, 2f, -0.667f, 3.5f, -2f, 4.5f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.794f, 16.5f)
                curveToRelative(-0.2f, -1.655f, -1.165f, -2.982f, -2.897f, -3.982f)
                curveToRelative(-2.597f, -1.5f, -4.897f, -0.518f, -4.897f, -0.518f)
                reflectiveCurveToRelative(-2.299f, 0.982f, -4.897f, -0.518f)
                curveToRelative(-1.732f, -1f, -2.698f, -2.327f, -2.897f, -3.982f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.794f, 7.5f)
                curveToRelative(-1.532f, -0.655f, -3.165f, -0.482f, -4.897f, 0.518f)
                curveToRelative(-2.597f, 1.5f, -2.897f, 3.982f, -2.897f, 3.982f)
                reflectiveCurveToRelative(-0.299f, 2.482f, -2.897f, 3.982f)
                curveToRelative(-1.732f, 1f, -3.365f, 1.173f, -4.897f, 0.518f)
            }
        }.build()
        
        return _TablerGalaxy!!
    }

private var _TablerGalaxy: ImageVector? = null