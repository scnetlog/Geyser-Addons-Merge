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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.geysermc.geyser.platform.mod.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.geysermc.geyser.GeyserImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组物品桥接器。
 *
 * 设计目的：Geyser 扩展（如农夫乐事基岩版移植扩展）需要获取模组物品的
 * 服务端 runtime ID 和元数据才能通过 {@code GeyserDefineCustomItemsEvent.register(
 * NonVanillaCustomItemDefinition)} 注册自定义物品。
 *
 * 本类在 Geyser 启动前（{@code GeyserImpl.start()} 之前）于 fabric/neoforge 服务端
 * JVM 内枚举所有非 minecraft 命名空间的物品，记录每个物品的：
 * - identifier（如 farmersdelight:cabbage）
 * - javaId（BuiltInRegistries.ITEM 的网络 ID）
 * - isBlockItem（是否为 BlockItem，用于设置 block_placer 组件）
 * - translationKey（物品显示名翻译键）
 * - descriptionId（物品描述 id，用于显示名）
 *
 * 扩展通过反射读取此映射。
 */
public final class ModdedItemBridge {

    /** 模组物品信息列表 */
    private static volatile List<ItemInfo> moddedItems = List.of();
    private static volatile boolean collected = false;

    private ModdedItemBridge() {}

    /**
     * 枚举所有非 vanilla 物品。应在 Geyser 启动前调用一次。
     */
    public static void collect() {
        List<ItemInfo> list = new ArrayList<>();
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            String identifier = BuiltInRegistries.ITEM.getKey(item).toString();
            // 只收集非 minecraft 命名空间的物品（模组物品）
            if (identifier.startsWith("minecraft:")) {
                continue;
            }
            count++;
            int javaId = BuiltInRegistries.ITEM.getId(item);
            boolean isBlockItem = item instanceof BlockItem;
            String descriptionId = item.getDescriptionId();
            // 对于 BlockItem，记录其方块的 identifier，用于 block_placer 组件
            String blockIdentifier = null;
            if (isBlockItem) {
                BlockItem blockItem = (BlockItem) item;
                blockIdentifier = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            }
            list.add(new ItemInfo(identifier, javaId, isBlockItem, descriptionId, blockIdentifier));
        }
        moddedItems = List.copyOf(list);
        collected = true;
        GeyserImpl.getInstance().getLogger().info(
            "[ModdedItemBridge] Collected " + list.size() + " modded items from " + count + " items.");
    }

    /** 获取模组物品信息列表。 */
    public static List<ItemInfo> getModdedItems() {
        return moddedItems;
    }

    /** 桥接器是否已收集数据。 */
    public static boolean isCollected() {
        return collected;
    }

    /**
     * 模组物品信息。
     *
     * @param identifier       物品标识符（如 farmersdelight:cabbage）
     * @param javaId           Java 网络 ID
     * @param blockItem        是否为 BlockItem
     * @param descriptionId    物品描述 id（用于显示名翻译，如 block.farmersdelight.cabbage）
     * @param blockIdentifier  若为 BlockItem，对应的方块标识符；否则为 null
     */
    public record ItemInfo(
        String identifier,
        int javaId,
        boolean blockItem,
        String descriptionId,
        String blockIdentifier
    ) {}
}
