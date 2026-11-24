package io.github.Shai0717.stopreset

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Item

internal object DroppedItems {
    fun support(item: Item): Block? {
        if (!item.isValid || !item.isOnGround || item.itemStack.hasItemMeta()) return null
        val location: Location = item.location
        val block = location.clone().subtract(0.0, 0.05, 0.0).block
        return block.takeIf { it.type.isSolid }
    }

    fun take(item: Item, amount: Int) {
        val stack = item.itemStack
        if (stack.amount == amount) {
            item.remove()
        } else {
            stack.amount -= amount
            item.itemStack = stack
        }
    }
}
