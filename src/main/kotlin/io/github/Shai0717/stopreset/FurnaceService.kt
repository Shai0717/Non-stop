package io.github.Shai0717.stopreset

import io.github.Shai0717.kolor.KolorPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Furnace
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.inventory.FurnaceInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.io.File
import java.io.IOException
import java.util.UUID

internal class FurnaceService(
    private val plugin: KolorPlugin,
    private val scheduler: StopResetScheduler
) : Listener {
    private data class FurnaceKey(val world: UUID, val x: Int, val y: Int, val z: Int) {
        fun encode() = "$world,$x,$y,$z"
    }

    private data class ChunkKey(val world: UUID, val x: Int, val z: Int)

    private val inputs = setOf(
        Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
        Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON,
        Material.RABBIT, Material.COD, Material.SALMON, Material.KELP,
        Material.SAND, Material.RED_SAND, Material.COBBLESTONE
    )
    private val marker = NamespacedKey(plugin, "drive_through")
    private val file = File(plugin.dataFolder, "active-furnaces.yml")
    private val active = HashSet<FurnaceKey>()
    private val tickets = HashMap<ChunkKey, Int>()

    fun restore() {
        if (!file.exists()) return
        val data = YamlConfiguration.loadConfiguration(file)
        for (encoded in data.getStringList("furnaces")) {
            try {
                val parts = encoded.split(',')
                require(parts.size == 4) { "잘못된 좌표 형식" }
                val key = FurnaceKey(
                    UUID.fromString(parts[0]), parts[1].toInt(), parts[2].toInt(), parts[3].toInt()
                )
                if (Bukkit.getWorld(key.world) == null) {
                    plugin.logger.warning("화로 월드를 찾을 수 없습니다: $encoded")
                    continue
                }
                add(key)
            } catch (exception: IllegalArgumentException) {
                plugin.logger.warning("저장된 화로 좌표가 잘못되었습니다: $encoded")
            }
        }
        updateTickets()
    }

    fun absorb(item: Item) {
        val block = DroppedItems.support(item) ?: return
        if (block.type != Material.FURNACE) return
        val type = item.itemStack.type
        val input = type in inputs
        val fuel = type.isFuel && (
            type == Material.COAL || type == Material.CHARCOAL ||
                Tag.PLANKS.isTagged(type) || Tag.LOGS.isTagged(type)
            )
        if (!input && !fuel) return

        val furnace = block.state as Furnace
        val inventory = furnace.snapshotInventory
        val existing = if (input) inventory.smelting else inventory.fuel
        if (existing != null && !existing.isSimilar(item.itemStack)) return
        val present = existing?.amount ?: 0
        val count = minOf(item.itemStack.amount, item.itemStack.maxStackSize - present)
        if (count <= 0) return

        val updated = item.itemStack.clone().apply { amount = present + count }
        if (input) inventory.smelting = updated else inventory.fuel = updated
        furnace.persistentDataContainer.set(marker, PersistentDataType.BYTE, 1)
        if (!furnace.update()) {
            plugin.logger.warning("화로 상태를 저장하지 못했습니다: ${block.location}")
            return
        }
        DroppedItems.take(item, count)
        if (hasWork(furnace) && add(key(block))) save()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val furnace = event.block.state as? Furnace ?: return
        if (!furnace.persistentDataContainer.has(marker, PersistentDataType.BYTE)) return
        val result = event.result.clone()
        if (result.type.isAir || result.amount <= 0) return
        val block = event.block
        scheduler.at(block.location) { eject(block, result) }
    }

    private fun eject(block: Block, result: ItemStack) {
        val furnace = block.state as? Furnace ?: return
        val inventory = furnace.inventory
        val output = inventory.result
        if (output == null || !output.isSimilar(result) || output.amount < result.amount) {
            plugin.logger.warning("화로 결과가 변경되어 자동 배출하지 못했습니다: ${block.location}")
            return
        }
        val left = output.amount - result.amount
        inventory.result = if (left == 0) null else output.apply { amount = left }
        val dropped = block.world.dropItem(block.location.add(0.5, 1.1, 0.5), result)
        dropped.velocity = Vector(0.0, 0.18, 0.0)
    }

    fun updateTickets() {
        var changed = false
        for (key in active.toList()) {
            val world = Bukkit.getWorld(key.world)
            val furnace = world?.getBlockAt(key.x, key.y, key.z)?.state as? Furnace
            if (furnace == null || !hasWork(furnace)) {
                remove(key)
                changed = true
            }
        }
        if (changed) save()
    }

    fun close() {
        save()
        for (key in tickets.keys) {
            Bukkit.getWorld(key.world)?.removePluginChunkTicket(key.x, key.z, plugin)
        }
        tickets.clear()
        active.clear()
    }

    private fun hasWork(furnace: Furnace): Boolean {
        val inventory: FurnaceInventory = furnace.inventory
        return inventory.smelting != null && (inventory.fuel != null || furnace.burnTime > 0)
    }

    private fun key(block: Block) = FurnaceKey(
        block.world.uid, block.x, block.y, block.z
    )

    private fun add(key: FurnaceKey): Boolean {
        if (!active.add(key)) return false
        val chunk = ChunkKey(key.world, key.x shr 4, key.z shr 4)
        if (chunk !in tickets) {
            val world = Bukkit.getWorld(key.world)
            if (world == null) {
                active.remove(key)
                return false
            }
            world.addPluginChunkTicket(chunk.x, chunk.z, plugin)
        }
        tickets.merge(chunk, 1, Int::plus)
        return true
    }

    private fun remove(key: FurnaceKey) {
        active.remove(key)
        val chunk = ChunkKey(key.world, key.x shr 4, key.z shr 4)
        val remaining = checkNotNull(tickets[chunk]) { "화로 청크 티켓이 없습니다: $chunk" } - 1
        if (remaining == 0) {
            tickets.remove(chunk)
            Bukkit.getWorld(chunk.world)?.removePluginChunkTicket(chunk.x, chunk.z, plugin)
        } else {
            tickets[chunk] = remaining
        }
    }

    private fun save() {
        if (!plugin.dataFolder.isDirectory && !plugin.dataFolder.mkdirs()) {
            plugin.logger.severe("화로 데이터 폴더를 만들 수 없습니다: ${plugin.dataFolder}")
            return
        }
        val data = YamlConfiguration()
        data.set("furnaces", active.map(FurnaceKey::encode).sorted())
        try {
            data.save(file)
        } catch (exception: IOException) {
            plugin.logger.severe("화로 데이터 저장 실패: ${exception.message}")
        }
    }
}
