package io.github.Shai0717.stopreset

import io.github.Shai0717.kolor.KolorModule
import io.github.Shai0717.kolor.KolorPlugin
import io.github.Shai0717.kolor.KolorScheduler
import io.github.Shai0717.kolor.KolorInterop
import io.github.Shai0717.kolor.kolorCommand
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class StopResetPlugin : KolorPlugin() {
    private val positions = HashMap<UUID, Position>()
    private val graceTicks = HashMap<UUID, Int>()
    private lateinit var scheduler: StopResetScheduler
    private lateinit var crafting: GroundCrafting
    private lateinit var furnaces: FurnaceService
    private var running = false
    private var axe = false

    override fun onEnable() {
        attach(StopResetModule())
    }

    private fun initialize() {
        scheduler = StopResetScheduler(this)
        crafting = GroundCrafting()
        furnaces = FurnaceService(this, scheduler)
        server.pluginManager.registerEvents(furnaces, this)
        furnaces.restore()

        scheduler.global(delayTicks = 1, periodTicks = 1, task = ::checkMovement)
        scheduler.global(delayTicks = 1, periodTicks = 5, task = ::processItems)
        registerCommands()
    }

    private fun shutdown() {
        if (::furnaces.isInitialized) furnaces.close()
        positions.clear()
        graceTicks.clear()
    }

    private fun checkMovement() {
        if (!running) return
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.isDead ||
                (player.gameMode != GameMode.SURVIVAL && player.gameMode != GameMode.ADVENTURE)
            ) {
                positions.remove(player.uniqueId)
                graceTicks.remove(player.uniqueId)
                continue
            }
            val location = player.location
            val now = Position(location.world.uid, location.x, location.z, location.yaw.toDouble(), location.pitch.toDouble())
            val previous = positions.put(player.uniqueId, now)
            if (previous == null || previous.world != now.world) {
                graceTicks[player.uniqueId] = MOVE_GRACE_TICKS
                continue
            }
            val grace = graceTicks[player.uniqueId] ?: 0
            if (grace > 0) {
                graceTicks[player.uniqueId] = grace - 1
                continue
            }
            if (!Movement.shouldReset(previous, now)) continue

            if (player.teleport(player.world.spawnLocation)) {
                player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
                player.sendActionBar(Component.text("멈췄습니다! 태초마을로 복귀합니다."))
                player.location.let { spawn ->
                    positions[player.uniqueId] = Position(
                        spawn.world.uid, spawn.x, spawn.z, spawn.yaw.toDouble(), spawn.pitch.toDouble()
                    )
                }
                graceTicks[player.uniqueId] = MOVE_GRACE_TICKS
            } else {
                logger.warning("스폰 이동이 취소되었습니다: ${player.name}")
            }
        }
    }

    private fun processItems() {
        crafting.advance()
        for (world in Bukkit.getWorlds()) {
            val items = world.getEntitiesByClass(Item::class.java).toList()
            items.filter { it.isValid }.forEach(furnaces::absorb)
            crafting.process(world, items, axe)
        }
        furnaces.updateTickets()
    }

    private fun registerCommands() {
        kolorCommand("non-stop") {
            requires { it.hasPermission("nonstop.admin") }
            executes {
                it.sender.sendMessage("사용법: /non-stop <start|stop|status|tool pickaxe|axe>")
                1
            }
            literal("start") {
                executes { context ->
                    onGlobal(context.sender) {
                        positions.clear()
                        graceTicks.clear()
                        running = true
                        it.sendMessage("멈춤 챌린지를 시작했습니다.")
                    }
                }
            }
            literal("stop") {
                executes { context ->
                    onGlobal(context.sender) {
                        running = false
                        positions.clear()
                        graceTicks.clear()
                        it.sendMessage("멈춤 챌린지를 중지했습니다.")
                    }
                }
            }
            literal("status") {
                executes { context ->
                    onGlobal(context.sender) {
                        it.sendMessage("멈춤 감지: ${if (running) "실행 중" else "중지"}, 도구: ${if (axe) "도끼" else "곡괭이"}")
                    }
                }
            }
            literal("tool") {
                executes {
                    it.sender.sendMessage("사용법: /non-stop tool <pickaxe|axe>")
                    1
                }
                literal("pickaxe") {
                    executes { context ->
                        onGlobal(context.sender) {
                            axe = false
                            it.sendMessage("고급 도구 제작: 곡괭이")
                        }
                    }
                }
                literal("axe") {
                    executes { context ->
                        onGlobal(context.sender) {
                            axe = true
                            it.sendMessage("고급 도구 제작: 도끼")
                        }
                    }
                }
            }
        }
    }

    private fun onGlobal(sender: CommandSender, action: (CommandSender) -> Unit): Int {
        scheduler.global(delayTicks = 1, periodTicks = 0) {
            action(sender)
        }
        return 1
    }

    companion object {
        private const val MOVE_GRACE_TICKS = 4
    }

    private class StopResetModule : KolorModule {
        override val id = "stopreset-core"

        override fun onAttach(plugin: KolorPlugin) {
            (plugin as StopResetPlugin).initialize()
        }

        override fun onDetach(plugin: KolorPlugin) {
            (plugin as StopResetPlugin).shutdown()
        }
    }
}

internal class StopResetScheduler(plugin: JavaPlugin) {
    private val scheduler = KolorScheduler(plugin)

    fun global(delayTicks: Long, periodTicks: Long, task: () -> Unit) =
        KolorInterop.runGlobal(scheduler, delayTicks, periodTicks, Runnable(task))

    fun at(location: Location, delayTicks: Long = 1, task: () -> Unit) =
        KolorInterop.runAt(scheduler, location, delayTicks, 0, Runnable(task))
}
