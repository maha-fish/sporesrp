package com.maha_fish.sporesrp.util;

import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.List;

/**
 * 群系区域转换的共性实现：**直接写 biome palette**，而不是逐方块 {@code setBiome}。
 *
 * <p>群系数据的粒度是 4×4×4 方块，逐方块写有 64 倍冗余；
 * 这里按格子写，并跳过已经是目标群系的格子，因此对同一区域反复执行的开销趋近于零。
 *
 * <p>只处理**已加载**区块（不会触发加载或生成），未加载的交给调用方的后续扫描补。
 */
public final class BiomeRegionConverter {

    private BiomeRegionConverter() {
    }

    /**
     * 把单个已加载区块内、落在区域中的群系格子写成目标群系。
     *
     * @param onlyFrom 非 null 时只替换「当前等于该群系」的格子；
     *                 null 表示区域内所有格子都替换成 {@code target}
     * @return 有改动的区块；未加载、或没有任何格子需要改动时返回 null
     */
    public static LevelChunk convertChunk(ServerLevel level, Holder<Biome> target, Holder<Biome> onlyFrom,
                                          int cx, int cz,
                                          int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ) {
        if (!(level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            return null;
        }

        LevelChunkSection[] sections = chunk.getSections();
        boolean changed = false;

        int minSectionY = SectionPos.blockToSectionCoord(minY);
        int maxSectionY = SectionPos.blockToSectionCoord(maxY);

        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            int idx = level.getSectionIndexFromSectionY(sectionY);
            if (idx < 0 || idx >= sections.length) continue;

            LevelChunkSection section = sections[idx];
            if (section == null) continue;

            // getBiomes() 的声明类型是只读接口，实际实现恒为 PalettedContainer；
            // 拿不到可写实例就跳过这个 section（不影响正确性，只是这次不处理）。
            if (!(section.getBiomes() instanceof PalettedContainer<Holder<Biome>> biomes)) continue;

            int baseX = cx << 4;
            int baseY = sectionY << 4;
            int baseZ = cz << 4;

            for (int cellX = 0; cellX < 4; cellX++) {
                int blockX = baseX + (cellX << 2) + 2; // 取格子中心做范围判断
                if (blockX < minX || blockX > maxX) continue;

                for (int cellZ = 0; cellZ < 4; cellZ++) {
                    int blockZ = baseZ + (cellZ << 2) + 2;
                    if (blockZ < minZ || blockZ > maxZ) continue;

                    for (int cellY = 0; cellY < 4; cellY++) {
                        int blockY = baseY + (cellY << 2) + 2;
                        if (blockY < minY || blockY > maxY) continue;

                        Holder<Biome> current = biomes.get(cellX, cellY, cellZ);
                        if (current == target) continue;              // 已经是目标群系
                        if (onlyFrom != null && current != onlyFrom) continue; // 只清理指定来源群系

                        biomes.set(cellX, cellY, cellZ, target);
                        changed = true;
                    }
                }
            }
        }

        if (!changed) return null;
        chunk.setUnsaved(true); // 不标脏则不会落盘，重进世界会回滚
        return chunk;
    }

    /**
     * 把本 tick 改动过的区块合并成**一个**群系包发给同维度玩家。
     *
     * <p>不调用本方法改出来的群系只有重新进入视野时才会被客户端看到。
     */
    public static void sendBiomePacket(List<LevelChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        ClientboundChunksBiomesPacket packet = ClientboundChunksBiomesPacket.forChunks(chunks);
        ServerLevel level = (ServerLevel) chunks.get(0).getLevel();
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }
}
