package io.github.Shai0717.stopreset

import java.util.UUID

object MovementTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val world = UUID.randomUUID()
        check(Movement.shouldReset(Position(world, 0.0, 0.0), Position(world, 0.005, 0.005)))
        check(!Movement.shouldReset(Position(world, 0.0, 0.0), Position(world, 0.01, 0.0)))
        check(!Movement.shouldReset(Position(world, 0.0, 0.0), Position(world, 0.008, 0.008)))
        check(!Movement.shouldReset(
            Position(world, 0.0, 0.0),
            Position(UUID.randomUUID(), 0.0, 0.0)
        ))
        check(!Movement.shouldReset(
            Position(world, 0.0, 0.0, 179.0),
            Position(world, 0.0, 0.0, -179.0)
        ))
        check(!Movement.shouldReset(
            Position(world, 0.0, 0.0),
            Position(world, 0.0, 0.0, 0.2)
        ))
        check(!Movement.shouldReset(
            Position(world, 0.0, 0.0),
            Position(world, 0.0, 0.0, 0.0, 0.2)
        ))
        check(Movement.shouldReset(
            Position(world, 0.0, 0.0, 179.99),
            Position(world, 0.0, 0.0, -179.96)
        ))
    }
}
