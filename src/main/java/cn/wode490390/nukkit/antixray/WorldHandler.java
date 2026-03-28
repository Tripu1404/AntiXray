package cn.wode490390.nukkit.antixray;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.level.LevelUnloadEvent;
import cn.nukkit.event.player.PlayerChunkRequestEvent;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AntiXray extends PluginBase implements Listener {

    int height = 4;
    int maxY;
    boolean obfuscatorMode = true;
    boolean memoryCache = false;
    private List<String> worlds;

    final boolean[] filter = new boolean[256];
    final boolean[] ore = new boolean[256];
    final int[] dimension = new int[]{Block.STONE, Block.NETHERRACK, Block.AIR, Block.AIR};

    private final Map<Level, WorldHandler> handlers = Maps.newHashMap();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        Config config = this.getConfig();

        String node = "scan-chunk-height-limit";
        if (config.exists(node)) {
            this.height = config.getInt(node, this.height);
        } else {
            this.height = config.getInt("scan-height-limit", 64) >> 4;
        }
        this.height = Math.max(Math.min(this.height, 15), 1);
        this.maxY = this.height << 4;

        this.memoryCache = config.getBoolean("memory-cache", config.getBoolean("cache-chunks", false));
        this.obfuscatorMode = config.getBoolean("obfuscator-mode", true);
        
        this.dimension[0] = config.getInt("overworld-fake-block", Block.STONE) & 0xff;
        this.dimension[1] = config.getInt("nether-fake-block", Block.NETHERRACK) & 0xff;

        this.worlds = config.getStringList("protect-worlds");
        List<Integer> ores = config.getIntegerList("ores");

        if (this.worlds != null && !this.worlds.isEmpty()) {
            List<Integer> filters = config.getIntegerList("filters");
            for (int id : filters) {
                if (id >= 0 && id < 256) this.filter[id] = true;
            }
            if (!this.obfuscatorMode && ores != null) {
                for (int id : ores) {
                    if (id >= 0 && id < 256) this.ore[id] = true;
                }
            }

            this.getServer().getPluginManager().registerEvents(this, this);
            this.getLogger().info("§aAntiXray cargado correctamente (Versión Corregida).");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChunkRequest(PlayerChunkRequestEvent event) {
        Player player = event.getPlayer();
        Level level = player.getLevel();
        if (this.worlds.contains(level.getName()) && player.getLoaderId() > 0) {
            event.setCancelled();
            WorldHandler handler = this.handlers.computeIfAbsent(level, l -> new WorldHandler(this, l));
            handler.requestChunk(event.getChunkX(), event.getChunkZ(), player);
        }
    }

    @EventHandler
    public void onBlockUpdate(BlockUpdateEvent event) {
        Position position = event.getBlock();
        Level level = position.getLevel();
        if (this.worlds.contains(level.getName())) {
            List<UpdateBlockPacket> packets = Lists.newArrayList();
            Vector3[] sides = {position.add(1), position.add(-1), position.add(0, 1), position.add(0, -1), position.add(0, 0, 1), position.add(0, 0, -1)};
            
            for (Vector3 vector : sides) {
                int y = vector.getFloorY();
                if (y > 255 || y < 0) continue;
                
                UpdateBlockPacket packet = new UpdateBlockPacket();
                packet.x = vector.getFloorX();
                packet.y = y;
                packet.z = vector.getFloorZ();
                packet.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(level.getBlockIdAt(packet.x, y, packet.z), level.getBlockDataAt(packet.x, y, packet.z));
                packet.flags = UpdateBlockPacket.FLAG_ALL_PRIORITY;
                packets.add(packet);
            }

            if (!packets.isEmpty()) {
                this.getServer().batchPackets(level.getChunkPlayers(position.getChunkX(), position.getChunkZ()).values().toArray(new Player[0]), packets.toArray(new UpdateBlockPacket[0]));
            }
        }
    }

    @EventHandler
    public void onLevelUnload(LevelUnloadEvent event) {
        this.handlers.remove(event.getLevel());
    }
}
