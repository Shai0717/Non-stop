package io.github.Shai0717.stopreset

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.function.Predicate

internal class GroundCrafting {
    private data class Ingredient(val matches: Predicate<Material>, val amount: Int)
    private data class Recipe(val result: Material, val amount: Int, val ingredients: List<Ingredient>)
    private data class Take(val item: Item, val amount: Int)

    private val protectedItems = HashMap<UUID, Int>()
    private var cycle = 0

    fun advance() {
        cycle++
        protectedItems.values.removeIf { it <= cycle }
    }

    fun protect(item: Item) {
        protectedItems[item.uniqueId] = cycle + 8
    }

    fun process(world: World, items: List<Item>, axe: Boolean) {
        for (anchor in items) {
            if (!anchor.isValid || anchor.uniqueId in protectedItems ||
                !isIngredient(anchor.itemStack.type)
            ) continue

            val support = DroppedItems.support(anchor) ?: continue
            if (support.type == Material.FURNACE) continue
            val table = support.type == Material.CRAFTING_TABLE
            val group = mutableListOf(anchor)
            for (nearby in anchor.getNearbyEntities(1.5, 1.5, 1.5)) {
                val item = nearby as? Item ?: continue
                if (item.uniqueId in protectedItems ||
                    item.location.distanceSquared(anchor.location) > 2.25
                ) continue
                val other = DroppedItems.support(item) ?: continue
                if (if (table) other == support else
                    other.type != Material.FURNACE && other.type != Material.CRAFTING_TABLE
                ) group += item
            }

            for (recipe in recipes(group, table, axe)) {
                val takes = plan(group, recipe) ?: continue
                takes.forEach { DroppedItems.take(it.item, it.amount) }
                val location = support.location.add(0.5, 1.05, 0.5)
                val result = world.dropItem(location, ItemStack(recipe.result, recipe.amount))
                protect(result)
                world.playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f)
                world.spawnParticle(Particle.HAPPY_VILLAGER, location, 5, 0.2, 0.15, 0.2, 0.0)
                break
            }
        }
    }

    private fun isIngredient(material: Material): Boolean =
        Tag.PLANKS.isTagged(material) || Tag.LOGS.isTagged(material) ||
            material in setOf(
                Material.COBBLESTONE, Material.COAL, Material.STICK,
                Material.IRON_INGOT, Material.DIAMOND
            )

    private fun recipes(items: List<Item>, table: Boolean, axe: Boolean): List<Recipe> {
        val result = mutableListOf<Recipe>()
        if (table) {
            for (material in listOf(Material.DIAMOND, Material.IRON_INGOT, Material.COBBLESTONE)) {
                val prefix = when (material) {
                    Material.DIAMOND -> "DIAMOND"
                    Material.IRON_INGOT -> "IRON"
                    else -> "STONE"
                }
                result += recipe(
                    Material.valueOf(prefix + if (axe) "_AXE" else "_PICKAXE"), 1,
                    exact(material, 3), exact(Material.STICK, 2)
                )
                result += recipe(
                    Material.valueOf("${prefix}_SWORD"), 1,
                    exact(material, 2), exact(Material.STICK, 1)
                )
            }
            result += recipe(
                if (axe) Material.WOODEN_AXE else Material.WOODEN_PICKAXE, 1,
                ingredient(Tag.PLANKS::isTagged, 3), exact(Material.STICK, 2)
            )
            result += recipe(Material.IRON_CHESTPLATE, 1, exact(Material.IRON_INGOT, 8))
            result += recipe(Material.IRON_LEGGINGS, 1, exact(Material.IRON_INGOT, 7))
            result += recipe(Material.IRON_HELMET, 1, exact(Material.IRON_INGOT, 5))
            result += recipe(Material.IRON_BOOTS, 1, exact(Material.IRON_INGOT, 4))
            result += recipe(Material.BUCKET, 1, exact(Material.IRON_INGOT, 3))
        } else {
            result += recipe(Material.FURNACE, 1, exact(Material.COBBLESTONE, 8))
            result += recipe(Material.CRAFTING_TABLE, 1, ingredient(Tag.PLANKS::isTagged, 4))
            result += recipe(Material.TORCH, 4, exact(Material.COAL, 1), exact(Material.STICK, 1))
            result += recipe(Material.STICK, 4, ingredient(Tag.PLANKS::isTagged, 2))
            for (item in items) {
                val planks = planksFor(item.itemStack.type) ?: continue
                result += recipe(planks, 4, exact(item.itemStack.type, 1))
            }
        }
        return result
    }

    private fun planksFor(log: Material): Material? {
        if (!Tag.LOGS.isTagged(log)) return null
        val name = log.name.removePrefix("STRIPPED_")
        val separator = name.lastIndexOf('_')
        if (separator < 0) return null
        return Material.matchMaterial("${name.substring(0, separator)}_PLANKS")
    }

    private fun exact(material: Material, amount: Int) =
        ingredient({ candidate -> candidate == material }, amount)

    private fun ingredient(matches: (Material) -> Boolean, amount: Int) =
        Ingredient(Predicate(matches), amount)

    private fun recipe(result: Material, amount: Int, vararg ingredients: Ingredient) =
        Recipe(result, amount, ingredients.toList())

    private fun plan(items: List<Item>, recipe: Recipe): List<Take>? {
        val takes = mutableListOf<Take>()
        val reserved = HashMap<UUID, Int>()
        for (ingredient in recipe.ingredients) {
            var remaining = ingredient.amount
            for (item in items) {
                val stack = item.itemStack
                if (!ingredient.matches.test(stack.type)) continue
                val count = minOf(remaining, stack.amount - (reserved[item.uniqueId] ?: 0))
                if (count > 0) {
                    takes += Take(item, count)
                    reserved.merge(item.uniqueId, count, Int::plus)
                    remaining -= count
                }
                if (remaining == 0) break
            }
            if (remaining != 0) return null
        }
        return takes
    }
}
