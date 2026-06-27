/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.geysermc.geyser.platform.mod.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.geysermc.geyser.GeyserImpl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 模组方块状态桥接器。
 *
 * 设计目的：Geyser core 的 {@code BlockRegistries.BLOCK_STATES} 只包含 vanilla 方块，
 * 不包含模组方块。而 Geyser 扩展（如农夫乐事基岩版移植扩展）需要获取模组方块的
 * 服务端 runtime ID 才能通过 {@code GeyserDefineCustomBlocksEvent.registerOverride(
 * JavaBlockState, CustomBlockState)} 注册 non-vanilla override。
 *
 * 本类在 Geyser 启动前（{@code GeyserImpl.start()} 之前）于 fabric/neoforge 服务端
 * JVM 内枚举所有非 minecraft 命名空间的方块状态，记录 identifier→[javaId, waterloggedFlag]
 * 映射。扩展通过反射读取此映射。
 *
 * identifier 格式与 Geyser {@code BlockState.toString()} 一致：
 *   {@code <registry key>[<prop>=<val>,...]}
 * 例如：{@code farmersdelight:cabbages[age=0]}
 */
public final class ModdedBlockStateBridge {

    /** 模组方块状态映射：identifier → {javaId, waterloggedFlag(0/1)} */
    private static volatile Map<String, int[]> moddedStates = Map.of();
    private static volatile boolean collected = false;

    private ModdedBlockStateBridge() {}

    /**
     * 枚举所有非 vanilla 方块状态。应在 Geyser 启动前调用一次。
     */
    public static void collect() {
        Map<String, int[]> map = new HashMap<>();
        int blockCount = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            String key = BuiltInRegistries.BLOCK.getKey(block).toString();
            // 只收集非 minecraft 命名空间的方块（模组方块）
            if (key.startsWith("minecraft:")) {
                continue;
            }
            blockCount++;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int javaId = Block.BLOCK_STATE_REGISTRY.getId(state);
                boolean waterlogged = !state.getFluidState().isEmpty();
                String identifier = toIdentifier(key, state);
                map.put(identifier, new int[]{javaId, waterlogged ? 1 : 0});
            }
        }
        moddedStates = Map.copyOf(map);
        collected = true;
        GeyserImpl.getInstance().getLogger().info(
            "[ModdedBlockStateBridge] Collected " + map.size() + " modded block states from "
            + blockCount + " modded blocks.");
    }

    /**
     * 构造与 Geyser BlockState.toString() 一致的 identifier。
     * 格式：{@code <key>[<prop>=<val>,...]}，值小写（覆盖枚举）。
     */
    private static String toIdentifier(String key, BlockState state) {
        Collection<Property<?>> props = state.getProperties();
        if (props.isEmpty()) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key).append('[');
        boolean first = true;
        for (Property<?> p : props) {
            if (!first) sb.append(',');
            first = false;
            sb.append(p.getName()).append('=');
            Object value = state.getValue(p);
            sb.append(value.toString().toLowerCase(Locale.ROOT));
        }
        sb.append(']');
        return sb.toString();
    }

    /** 获取模组方块状态映射（identifier → {javaId, waterloggedFlag}）。 */
    public static Map<String, int[]> getModdedBlockStates() {
        return moddedStates;
    }

    /** 桥接器是否已收集数据。 */
    public static boolean isCollected() {
        return collected;
    }

    /** 按标识符查询单个方块状态信息，返回 {javaId, waterloggedFlag} 或 null。 */
    public static int[] getByIdentifier(String identifier) {
        return moddedStates.get(identifier);
    }
}
