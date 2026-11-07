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
public val PersonPinIcon: ImageVector
    get() {
        if (_person_pin != null) {
            return _person_pin!!
        }
        _person_pin = ImageVector.Builder(
            name = "PersonPinIcon",
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
                moveTo(12f, 23f)
                lineTo(9f, 20f)
                horizontalLineTo(5f)
                quadTo(4.18f, 20f, 3.59f, 19.41f)
                reflectiveQuadTo(3f, 18f)
                verticalLineTo(4f)
                quadTo(3f, 3.17f, 3.59f, 2.59f)
                reflectiveQuadTo(5f, 2f)
                horizontalLineTo(19f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(21f, 4f)
                verticalLineTo(18f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(19f, 20f)
                horizontalLineTo(15f)
                lineToRelative(-3f, 3f)
                close()
                moveTo(5f, 16.85f)
                quadTo(6.35f, 15.53f, 8.14f, 14.76f)
                reflectiveQuadTo(12f, 14f)
                reflectiveQuadToRelative(3.86f, 0.76f)
                quadToRelative(1.79f, 0.76f, 3.14f, 2.09f)
                verticalLineTo(4f)
                horizontalLineTo(5f)
                verticalLineTo(16.85f)
                close()
                moveToRelative(9.48f, -5.88f)
                quadTo(15.5f, 9.95f, 15.5f, 8.5f)
                reflectiveQuadTo(14.48f, 6.02f)
                reflectiveQuadTo(12f, 5f)
                reflectiveQuadTo(9.53f, 6.02f)
                reflectiveQuadTo(8.5f, 8.5f)
                reflectiveQuadToRelative(1.03f, 2.47f)
                reflectiveQuadTo(12f, 12f)
                reflectiveQuadToRelative(2.48f, -1.03f)
                close()
                moveTo(7f, 18f)
                horizontalLineTo(17f)
                quadToRelative(0f, -0.07f, 0f, -0.13f)
                reflectiveQuadToRelative(0f, -0.13f)
                quadTo(15.95f, 16.88f, 14.68f, 16.44f)
                reflectiveQuadTo(12f, 16f)
                reflectiveQuadTo(9.33f, 16.44f)
                reflectiveQuadTo(7f, 17.75f)
                quadToRelative(0f, 0.07f, 0f, 0.13f)
                reflectiveQuadTo(7f, 18f)
                close()
                moveTo(10.94f, 9.56f)
                quadTo(10.5f, 9.13f, 10.5f, 8.5f)
                reflectiveQuadTo(10.94f, 7.44f)
                reflectiveQuadTo(12f, 7f)
                reflectiveQuadToRelative(1.06f, 0.44f)
                reflectiveQuadTo(13.5f, 8.5f)
                reflectiveQuadTo(13.06f, 9.56f)
                reflectiveQuadTo(12f, 10f)
                reflectiveQuadTo(10.94f, 9.56f)
                close()
                moveTo(12f, 10.43f)
                close()
            }
        }.build()
        return _person_pin!!
    }

private var _person_pin: ImageVector? = null
