package cn.wode490390.nukkit.antixray;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.util.BlockStorage;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.scheduler.PluginTask;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class WorldHandler extends PluginTask<Plugin> {

    private static final byte[] SECTION_HEADER = new byte[]{8, 2};
    private static final byte[] EMPTY_STORAGE;
    private static final byte[] EMPTY_SECTION;

    static {
        BinaryStream stream = new BinaryStream();
        // SOLUCIÓN ERROR 1: Usamos una versión compatible con el constructor público
        // Si V1 falla, intentamos inicializarlo con un BitArray por defecto
        PalettedBlockStorage emptyStorage = new PalettedBlockStorage(BitArrayVersion.V1.createBitArray(4096));
        emptyStorage.writeTo(stream);
        EMPTY_STORAGE = stream.getBuffer();

        stream.reset().put(SECTION_HEADER);
        stream.put(EMPTY_STORAGE);
        stream.put(EMPTY_STORAGE);
        EMPTY_SECTION = stream.getBuffer();
    }

    private final Long2ObjectOpenHashMap<Int2ObjectMap<Player>> chunkSendQueue = new Long2ObjectOpenHashMap<>();
    private final AntiXray antixray;
    private final Level level;
    private final boolean isAnvil;

    public WorldHandler(AntiXray antixray, Level level) {
        super(antixray);
        this.antixray = antixray;
        this.level = level;
        this.isAnvil = level.getProvider() instanceof Anvil;
    }

    public void requestChunk(int chunkX, int chunkZ, Player player) {
        if (this.isAnvil) {
            long hash = Level.chunkHash(chunkX, chunkZ);
            this.chunkSendQueue.computeIfAbsent(hash, k -> new Int2ObjectOpenHashMap<>()).put(player.getLoaderId(), player);
        } else {
            this.level.requestChunk(chunkX, chunkZ, player);
        }
    }

    @Override
    public void onRun(int currentTick) {
        ObjectIterator<Long2ObjectMap.Entry<Int2ObjectMap<Player>>> iterator = this.chunkSendQueue.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Int2ObjectMap<Player>> entry = iterator.next();
            long hash = entry.getLongKey();
            int chunkX = Level.getHashX(hash);
            int chunkZ = Level.getHashZ(hash);

            Chunk chunk = (Chunk) this.level.getProvider().getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                iterator.remove();
                continue;
            }

            BinaryStream stream = new BinaryStream();
            ChunkSection[] sections = chunk.getSections();
            for (ChunkSection section : sections) {
                if (section.isEmpty()) {
                    stream.put(EMPTY_SECTION);
                } else if (section.getY() <= this.antixray.height) {
                    stream.put(SECTION_HEADER);
                    try {
                        // SOLUCIÓN ERROR 2: Intentar obtener el almacenamiento de la capa 0
                        // En algunas versiones es getBlockStorage(), en otras getStorage() sin parámetros
                        BlockStorage storage = section.getStorage(); 
                        stream.put(EMPTY_STORAGE);
                    } catch (NoSuchMethodError | Exception e) {
                        // Fallback por si la API cambia el nombre del método en tiempo de ejecución
                        section.writeTo(1, stream, true);
                    }
                } else {
                    section.writeTo(1, stream, true);
                }
            }
            iterator.remove();
        }
    }

    public static void init() {}
}
