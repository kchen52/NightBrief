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

val TablerSettings: ImageVector
    get() {
        if (_TablerSettings != null) return _TablerSettings!!
        
        _TablerSettings = ImageVector.Builder(
            name = "settings",
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
                moveTo(10.325f, 4.317f)
                curveToRelative(0.426f, -1.756f, 2.924f, -1.756f, 3.35f, 0f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, 2.573f, 1.066f)
                curveToRelative(1.543f, -0.94f, 3.31f, 0.826f, 2.37f, 2.37f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, 1.065f, 2.572f)
                curveToRelative(1.756f, 0.426f, 1.756f, 2.924f, 0f, 3.35f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, -1.066f, 2.573f)
                curveToRelative(0.94f, 1.543f, -0.826f, 3.31f, -2.37f, 2.37f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, -2.572f, 1.065f)
                curveToRelative(-0.426f, 1.756f, -2.924f, 1.756f, -3.35f, 0f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, -2.573f, -1.066f)
                curveToRelative(-1.543f, 0.94f, -3.31f, -0.826f, -2.37f, -2.37f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, -1.065f, -2.572f)
                curveToRelative(-1.756f, -0.426f, -1.756f, -2.924f, 0f, -3.35f)
                arcToRelative(1.724f, 1.724f, 0f, false, false, 1.066f, -2.573f)
                curveToRelative(-0.94f, -1.543f, 0.826f, -3.31f, 2.37f, -2.37f)
                curveToRelative(1f, 0.608f, 2.296f, 0.07f, 2.572f, -1.065f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 12f)
                arcToRelative(3f, 3f, 0f, true, false, 6f, 0f)
                arcToRelative(3f, 3f, 0f, false, false, -6f, 0f)
            }
        }.build()
        
        return _TablerSettings!!
    }

private var _TablerSettings: ImageVector? = null