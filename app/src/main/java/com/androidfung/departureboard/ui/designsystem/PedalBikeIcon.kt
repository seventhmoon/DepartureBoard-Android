package com.androidfung.departureboard.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val PedalBikeIcon: ImageVector
    get() {
        if (_pedal_bike != null) {
            return _pedal_bike!!
        }
        _pedal_bike = ImageVector.Builder(
            name = "PedalBikeIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero,
            ) {
                moveTo(5f, 20f)
                quadTo(2.88f, 20f, 1.44f, 18.56f)
                reflectiveQuadTo(0f, 15f)
                reflectiveQuadTo(1.46f, 11.44f)
                reflectiveQuadTo(5f, 10f)
                quadToRelative(1.93f, 0f, 3.24f, 1.15f)
                reflectiveQuadTo(9.9f, 14f)
                horizontalLineToRelative(0.65f)
                lineTo(8.75f, 9f)
                horizontalLineTo(7f)
                verticalLineTo(7f)
                horizontalLineToRelative(5f)
                verticalLineTo(9f)
                horizontalLineTo(10.9f)
                lineToRelative(0.35f, 1f)
                horizontalLineToRelative(4.8f)
                lineTo(14.6f, 6f)
                horizontalLineTo(12f)
                verticalLineTo(4f)
                horizontalLineToRelative(2.6f)
                quadToRelative(0.65f, 0f, 1.16f, 0.35f)
                reflectiveQuadTo(16.5f, 5.3f)
                lineToRelative(1.7f, 4.65f)
                horizontalLineTo(19f)
                quadToRelative(2.08f, 0f, 3.54f, 1.46f)
                reflectiveQuadTo(24f, 14.95f)
                quadToRelative(0f, 2.1f, -1.45f, 3.57f)
                reflectiveQuadTo(19f, 20f)
                quadToRelative(-1.8f, 0f, -3.16f, -1.13f)
                reflectiveQuadTo(14.1f, 16f)
                horizontalLineTo(9.9f)
                quadTo(9.55f, 17.73f, 8.2f, 18.86f)
                reflectiveQuadTo(5f, 20f)
                close()
                moveTo(5f, 18f)
                quadToRelative(1.03f, 0f, 1.76f, -0.56f)
                reflectiveQuadTo(7.8f, 16f)
                horizontalLineTo(5f)
                verticalLineTo(14f)
                horizontalLineTo(7.8f)
                quadTo(7.5f, 13.1f, 6.76f, 12.55f)
                reflectiveQuadTo(5f, 12f)
                quadTo(3.73f, 12f, 2.86f, 12.86f)
                quadTo(2f, 13.73f, 2f, 15f)
                quadToRelative(0f, 1.25f, 0.86f, 2.13f)
                reflectiveQuadTo(5f, 18f)
                close()
                moveToRelative(7.7f, -4f)
                horizontalLineToRelative(1.4f)
                quadToRelative(0.13f, -0.58f, 0.34f, -1.08f)
                reflectiveQuadTo(15f, 12f)
                horizontalLineTo(11.95f)
                lineToRelative(0.75f, 2f)
                close()
                moveTo(19f, 18f)
                quadToRelative(1.28f, 0f, 2.14f, -0.88f)
                reflectiveQuadTo(22f, 15f)
                quadToRelative(0f, -1.28f, -0.86f, -2.14f)
                reflectiveQuadTo(19f, 12f)
                quadToRelative(-0.05f, 0f, -0.05f, 0f)
                reflectiveQuadTo(18.9f, 12f)
                lineToRelative(1f, 2.65f)
                lineTo(18f, 15.35f)
                lineTo(17.05f, 12.7f)
                quadToRelative(-0.5f, 0.43f, -0.77f, 1f)
                reflectiveQuadTo(16f, 15f)
                quadToRelative(0f, 1.25f, 0.86f, 2.13f)
                reflectiveQuadTo(19f, 18f)
                close()
                moveTo(4.9f, 15f)
                close()
                moveTo(19f, 15f)
                close()
            }
        }.build()
        return _pedal_bike!!
    }

private var _pedal_bike: ImageVector? = null
