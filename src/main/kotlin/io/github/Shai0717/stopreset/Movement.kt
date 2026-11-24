package io.github.Shai0717.stopreset

import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

data class Position(
    val world: UUID,
    val x: Double,
    val z: Double,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0
)

object Movement {
    private const val MIN_HORIZONTAL_MOVEMENT = 0.01
    private const val MIN_VIEW_ROTATION = 0.1

    fun shouldReset(previous: Position, current: Position): Boolean {
        if (previous.world != current.world) return false
        val movedHorizontally = hypot(current.x - previous.x, current.z - previous.z) >= MIN_HORIZONTAL_MOVEMENT
        val rotatedView = angleDistance(previous.yaw, current.yaw) >= MIN_VIEW_ROTATION ||
            abs(current.pitch - previous.pitch) >= MIN_VIEW_ROTATION
        return !movedHorizontally && !rotatedView
    }

    private fun angleDistance(first: Double, second: Double): Double {
        val difference = (second - first + 180.0).mod(360.0) - 180.0
        return abs(difference)
    }
}
