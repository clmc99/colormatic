/*
 * Colormatic
 * Copyright (C) 2021-2022  Thalia Nero
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * As an additional permission, when conveying the Corresponding Source of an
 * object code form of this work, you may exclude the Corresponding Source for
 * "Minecraft" by Mojang Studios, AB.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.kvverti.colormatic.properties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Divider;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

public final class DefaultColumns {

    private static final Logger log = LogManager.getLogger();

    /**
     * Mapping of dynamic biomes to nearest vanilla columns.
     */
    private static final Map<Identifier, Identifier> dynamicColumns = new HashMap<>();

    /**
     * The default mapping of biomes to columns (based on vanilla biome raw ID).
     */
    public static Map<Identifier, ColormapProperties.ColumnBounds> currentColumns;

    /**
     * The 1.17 mapping of biomes to columns. Uses the 1.17 column equivalents for custom biomes and doubles up
     * columns for new vanilla biomes.
     */
    private static final Map<Identifier, ColormapProperties.ColumnBounds> legacyColumns = createLegacyColumnBounds();

    /**
     * A stable mapping of biomes to columns. Never uses biome raw IDs to determine column.
     */
    private static final Map<Identifier, ColormapProperties.ColumnBounds> stableColumns = createStableColumnBounds();

    private static final int LEGACY_1_17_BIOME_COUNT = 176;

    private DefaultColumns() {
    }

    /**
     * Returns columns based on vanilla biomes' raw IDs and datapack biomes' nearest temperature neighbors.
     * The default for colormaps in the Colormatic namespace.
     */
    public static ColormapProperties.ColumnBounds getDefaultBounds(RegistryKey<Biome> biomeKey) {
        var bounds = currentColumns.get(biomeKey.getValue());
        if(bounds == null) {
            bounds = currentColumns.get(approximateToVanilla(biomeKey));
            if(bounds == null) {
                // see comment in approximateToVanilla()
                var msg = "Custom biome has no approximate: " + biomeKey.getValue();
                log.error(msg);
                throw new IllegalStateException(msg);
            }
        }
        return bounds;
    }

    /**
     * Returns columns based on both vanilla biomes' and datapack biomes' raw IDs.
     * The default for colormaps in the Optifine namespace.
     */
    public static ColormapProperties.ColumnBounds getOptifineBounds(RegistryKey<Biome> biomeKey, Registry<Biome> biomeRegistry) {
        int rawID = biomes.getOrDefault(biomeKey.getValue().getPath(), -1);

        return new ColormapProperties.ColumnBounds(rawID, 1);
    }


    private static Registry<Biome> fixBiomeIds(Registry<Biome> idRegistry, Registry<Biome> valueRegistry) {
        SimpleRegistry<Biome> registry = new SimpleRegistry<>(RegistryKey.ofRegistry(new Identifier("biomes")), Lifecycle.stable(), true);

        for(RegistryKey<Biome> biomeKey : idRegistry.getKeys()) {
            Biome biome = valueRegistry.get(biomeKey);
            if (biome == null) {
                biome = idRegistry.get(biomeKey);
            }

            int id = idRegistry.getRawId(idRegistry.get(biomeKey));
            registry.createEntry(biome);
            RegistryEntry.Reference<Biome> var8 = registry.set(id, biomeKey, biome, Lifecycle.stable());
        }

        for(RegistryKey<Biome> biomeKey : valueRegistry.getKeys()) {
            if (!registry.contains(biomeKey)) {
                Biome biome = valueRegistry.get(biomeKey);
                registry.createEntry(biome);
                RegistryEntry.Reference<Biome> var13 = registry.add(biomeKey, biome, Lifecycle.stable());
            }
        }

        return registry;
    }

    /**
     * The 1.17 vanilla biome column bounds, with new vanilla biomes assigned to an approximate.
     * Custom biomes are offset so that the first custom biome takes column 176.
     */
    public static ColormapProperties.ColumnBounds getLegacyBounds(RegistryKey<Biome> biomeKey, Registry<Biome> biomeRegistry, boolean optifine) {
        var bounds = legacyColumns.get(biomeKey.getValue());
        if(bounds == null) {
            if(optifine) {
                // Optifine computes grid colors using the raw ID
                int rawID = biomeRegistry.getRawId(biomeRegistry.get(biomeKey));
                return new ColormapProperties.ColumnBounds(rawID - biomeRegistry.size() + LEGACY_1_17_BIOME_COUNT, 1);
            } else {
                bounds = legacyColumns.get(approximateToVanilla(biomeKey));
                if(bounds == null) {
                    // see comment in approximateToVanilla()
                    var msg = "Custom biome has no approximate: " + biomeKey.getValue();
                    log.error(msg);
                    throw new IllegalStateException(msg);
                }
            }
        }
        return bounds;
    }

    /**
     * Returns the stable column bounds for the given biome. Vanilla biome columns are guaranteed to be stable
     * across versions, and custom biomes are always approximated with a vanilla biome.
     */
    public static ColormapProperties.ColumnBounds getStableBounds(RegistryKey<Biome> biomeKey) {
        var bounds = stableColumns.get(biomeKey.getValue());
        if(bounds == null) {
            bounds = stableColumns.get(approximateToVanilla(biomeKey));
            if(bounds == null) {
                // see comment in approximateToVanilla()
                var msg = "Custom biome has no approximate: " + biomeKey.getValue();
                log.error(msg);
                throw new IllegalStateException(msg);
            }
        }
        return bounds;
    }

    /**
     * Retrieves the vanilla approximation for a custom biome.
     */
    private static Identifier approximateToVanilla(RegistryKey<Biome> biomeKey) {
        Identifier id;
        // Colormatic computes grid colors using temperature-humidity distance
        id = dynamicColumns.get(biomeKey.getValue());
        if(id == null) {
            // this exception tends to trigger a crash in the crash report generator due to it
            // happening off-thread, so we log before bailing
            var msg = "No column bounds for dynamic biome: " + biomeKey.getValue();
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return id;
    }

    /**
     * Called when the dynamic registry manager is changed to re-generate default column bounds.
     * This keeps column bounds in sync with the available biome list.
     *
     * @param manager The dynamic registry manager.
     */
    public static void reloadDefaultColumnBounds(DynamicRegistryManager manager) {
        dynamicColumns.clear();
        if(manager != null) {
            var biomeRegistry = manager.get(RegistryKeys.BIOME);
            for(var entry : biomeRegistry.getEntrySet()) {
                var key = entry.getKey();
                if(!currentColumns.containsKey(key.getValue())) {
                    dynamicColumns.put(key.getValue(), computeClosestDefaultBiome(key, biomeRegistry));
                }
            }
        }
    }

    /**
     * Given a custom biome, finds the vanilla biome closest in temperature and humidity to the given
     * biome and returns its bounds.
     *
     * @param biomeKey      The key of the custom biome.
     * @param biomeRegistry The biome registry.
     * @return The ID of the vanilla biome closest to the given biome.
     */
    private static Identifier computeClosestDefaultBiome(RegistryKey<Biome> biomeKey, Registry<Biome> biomeRegistry) {
        var customBiome = biomeRegistry.get(biomeKey);
        if(customBiome == null) {
            throw new IllegalStateException("Biome is not registered: " + biomeKey.getValue());
        }
        double temperature = customBiome.getTemperature();
        // TODO IMS FIX THIS
        double humidity = MathHelper.clamp(0.5f, 0.0, 1.0);
        double minDistanceSq = Double.POSITIVE_INFINITY;
        Identifier minBiomeId = null;
        for(var entry : currentColumns.entrySet()) {
            var vanillaBiome = biomeRegistry.get(entry.getKey());
            if(vanillaBiome == null) {
                log.error("Vanilla biome is not registered????? : {}", entry.getKey());
                continue;
            }
            var dTemperature = temperature - vanillaBiome.getTemperature();
            // TODO IMS FIX THIS
            var dHumidity = humidity - MathHelper.clamp(0.5f, 0.0, 1.0);
            var thisDistanceSq = dTemperature * dTemperature + dHumidity * dHumidity;
            if(thisDistanceSq < minDistanceSq) {
                minDistanceSq = thisDistanceSq;
                minBiomeId = entry.getKey();
            }
        }
        return minBiomeId;
    }

    public static Map<Identifier, ColormapProperties.ColumnBounds> createCurrentColumnBounds(World world) {
        if (world == null) return Collections.emptyMap();
        // based on the raw IDs in current Minecraft code
        var map = new HashMap<Identifier, ColormapProperties.ColumnBounds>();
        Registry<Biome> biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        for(var biome : biomeRegistry) {
            var id = biomeRegistry.getId(biome);
            var rawId = biomeRegistry.getRawId(biome);
            map.put(id, new ColormapProperties.ColumnBounds(rawId, 1));
        }
        return map;
    }

    private static final Object2IntMap<String> biomes;

    static {
        biomes = new Object2IntArrayMap<>();
        biomes.put("the_void", 0);
        biomes.put("plains", 1);
        biomes.put("sunflower_plains", 2);
        biomes.put("snowy_plains", 3);
        biomes.put("ice_spikes", 4);
        biomes.put("desert", 5);
        biomes.put("swamp", 6);
        biomes.put("mangrove_swamp", 7);
        biomes.put("forest", 8);
        biomes.put("flower_forest", 9);
        biomes.put("birch_forest", 10);
        biomes.put("dark_forest", 11);
        biomes.put("old_growth_birch_forest", 12);
        biomes.put("old_growth_pine_taiga", 13);
        biomes.put("old_growth_spruce_taiga", 14);
        biomes.put("taiga", 15);
        biomes.put("snowy_taiga", 16);
        biomes.put("savanna", 17);
        biomes.put("savanna_plateau", 18);
        biomes.put("windswept_hills", 19);
        biomes.put("windswept_gravelly_hills", 20);
        biomes.put("windswept_forest", 21);
        biomes.put("windswept_savanna", 22);
        biomes.put("jungle", 23);
        biomes.put("sparse_jungle", 24);
        biomes.put("bamboo_jungle", 25);
        biomes.put("badlands", 26);
        biomes.put("eroded_badlands", 27);
        biomes.put("wooded_badlands", 28);
        biomes.put("meadow", 29);
        biomes.put("grove", 30);
        biomes.put("snowy_slopes", 31);
        biomes.put("frozen_peaks", 32);
        biomes.put("jagged_peaks", 33);
        biomes.put("stony_peaks", 34);
        biomes.put("river", 35);
        biomes.put("frozen_river", 36);
        biomes.put("beach", 37);
        biomes.put("snowy_beach", 38);
        biomes.put("stony_shore", 39);
        biomes.put("warm_ocean", 40);
        biomes.put("lukewarm_ocean", 41);
        biomes.put("deep_lukewarm_ocean", 42);
        biomes.put("ocean", 43);
        biomes.put("deep_ocean", 44);
        biomes.put("cold_ocean", 45);
        biomes.put("deep_cold_ocean", 46);
        biomes.put("frozen_ocean", 47);
        biomes.put("deep_frozen_ocean", 48);
        biomes.put("mushroom_fields", 49);
        biomes.put("dripstone_caves", 50);
        biomes.put("lush_caves", 51);
        biomes.put("deep_dark", 52);
        biomes.put("nether_wastes", 53);
        biomes.put("warped_forest", 54);
        biomes.put("crimson_forest", 55);
        biomes.put("soul_sand_valley", 56);
        biomes.put("basalt_deltas", 57);
        biomes.put("the_end", 58);
        biomes.put("end_highlands", 59);
        biomes.put("end_midlands", 60);
        biomes.put("small_end_islands", 61);
        biomes.put("end_barrens", 62);
    }

    private static Map<Identifier, ColormapProperties.ColumnBounds> createLegacyColumnBounds() {
        // taken from the vanilla raw ID assignments in 1.17
        var map = new HashMap<Identifier, ColormapProperties.ColumnBounds>();
        map.put(BiomeKeys.OCEAN.getValue(), new ColormapProperties.ColumnBounds(0, 1));
        map.put(BiomeKeys.PLAINS.getValue(), new ColormapProperties.ColumnBounds(1, 1));
        map.put(BiomeKeys.DESERT.getValue(), new ColormapProperties.ColumnBounds(2, 1));
        map.put(BiomeKeys.WINDSWEPT_HILLS.getValue(), new ColormapProperties.ColumnBounds(3, 1));
        map.put(BiomeKeys.FOREST.getValue(), new ColormapProperties.ColumnBounds(4, 1));
        map.put(BiomeKeys.TAIGA.getValue(), new ColormapProperties.ColumnBounds(5, 1));
        map.put(BiomeKeys.SWAMP.getValue(), new ColormapProperties.ColumnBounds(6, 1));
        map.put(BiomeKeys.RIVER.getValue(), new ColormapProperties.ColumnBounds(7, 1));
        map.put(BiomeKeys.NETHER_WASTES.getValue(), new ColormapProperties.ColumnBounds(8, 1));
        map.put(BiomeKeys.THE_END.getValue(), new ColormapProperties.ColumnBounds(9, 1));
        map.put(BiomeKeys.FROZEN_OCEAN.getValue(), new ColormapProperties.ColumnBounds(10, 1));
        map.put(BiomeKeys.FROZEN_RIVER.getValue(), new ColormapProperties.ColumnBounds(11, 1));
        map.put(BiomeKeys.SNOWY_PLAINS.getValue(), new ColormapProperties.ColumnBounds(12, 1));
        map.put(BiomeKeys.MUSHROOM_FIELDS.getValue(), new ColormapProperties.ColumnBounds(14, 1));
        map.put(BiomeKeys.BEACH.getValue(), new ColormapProperties.ColumnBounds(16, 1));
        map.put(BiomeKeys.JUNGLE.getValue(), new ColormapProperties.ColumnBounds(21, 1));
        map.put(BiomeKeys.SPARSE_JUNGLE.getValue(), new ColormapProperties.ColumnBounds(23, 1));
        map.put(BiomeKeys.DEEP_OCEAN.getValue(), new ColormapProperties.ColumnBounds(24, 1));
        map.put(BiomeKeys.STONY_SHORE.getValue(), new ColormapProperties.ColumnBounds(25, 1));
        map.put(BiomeKeys.SNOWY_BEACH.getValue(), new ColormapProperties.ColumnBounds(26, 1));
        map.put(BiomeKeys.BIRCH_FOREST.getValue(), new ColormapProperties.ColumnBounds(27, 1));
        map.put(BiomeKeys.DARK_FOREST.getValue(), new ColormapProperties.ColumnBounds(29, 1));
        map.put(BiomeKeys.SNOWY_TAIGA.getValue(), new ColormapProperties.ColumnBounds(30, 1));
        map.put(BiomeKeys.OLD_GROWTH_PINE_TAIGA.getValue(), new ColormapProperties.ColumnBounds(32, 1));
        map.put(BiomeKeys.WINDSWEPT_FOREST.getValue(), new ColormapProperties.ColumnBounds(34, 1));
        map.put(BiomeKeys.SAVANNA.getValue(), new ColormapProperties.ColumnBounds(35, 1));
        map.put(BiomeKeys.SAVANNA_PLATEAU.getValue(), new ColormapProperties.ColumnBounds(36, 1));
        map.put(BiomeKeys.BADLANDS.getValue(), new ColormapProperties.ColumnBounds(37, 1));
        map.put(BiomeKeys.WOODED_BADLANDS.getValue(), new ColormapProperties.ColumnBounds(38, 1));
        map.put(BiomeKeys.SMALL_END_ISLANDS.getValue(), new ColormapProperties.ColumnBounds(40, 1));
        map.put(BiomeKeys.END_MIDLANDS.getValue(), new ColormapProperties.ColumnBounds(41, 1));
        map.put(BiomeKeys.END_HIGHLANDS.getValue(), new ColormapProperties.ColumnBounds(42, 1));
        map.put(BiomeKeys.END_BARRENS.getValue(), new ColormapProperties.ColumnBounds(43, 1));
        map.put(BiomeKeys.WARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(44, 1));
        map.put(BiomeKeys.LUKEWARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(45, 1));
        map.put(BiomeKeys.COLD_OCEAN.getValue(), new ColormapProperties.ColumnBounds(46, 1));
        map.put(BiomeKeys.DEEP_LUKEWARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(48, 1));
        map.put(BiomeKeys.DEEP_COLD_OCEAN.getValue(), new ColormapProperties.ColumnBounds(49, 1));
        map.put(BiomeKeys.DEEP_FROZEN_OCEAN.getValue(), new ColormapProperties.ColumnBounds(50, 1));
        // formerly "mutated" variants of biomes, normal biome ID + 128, except for
        // the post-1.7 biome additions.
        map.put(BiomeKeys.THE_VOID.getValue(), new ColormapProperties.ColumnBounds(127, 1));
        map.put(BiomeKeys.SUNFLOWER_PLAINS.getValue(), new ColormapProperties.ColumnBounds(129, 1));
        map.put(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue(), new ColormapProperties.ColumnBounds(131, 1));
        map.put(BiomeKeys.FLOWER_FOREST.getValue(), new ColormapProperties.ColumnBounds(132, 1));
        map.put(BiomeKeys.ICE_SPIKES.getValue(), new ColormapProperties.ColumnBounds(140, 1));
        map.put(BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue(), new ColormapProperties.ColumnBounds(155, 1));
        map.put(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue(), new ColormapProperties.ColumnBounds(160, 1));
        map.put(BiomeKeys.WINDSWEPT_SAVANNA.getValue(), new ColormapProperties.ColumnBounds(163, 1));
        map.put(BiomeKeys.ERODED_BADLANDS.getValue(), new ColormapProperties.ColumnBounds(165, 1));
        map.put(BiomeKeys.BAMBOO_JUNGLE.getValue(), new ColormapProperties.ColumnBounds(168, 1));
        // 1.16 nether biomes
        map.put(BiomeKeys.SOUL_SAND_VALLEY.getValue(), new ColormapProperties.ColumnBounds(170, 1));
        map.put(BiomeKeys.CRIMSON_FOREST.getValue(), new ColormapProperties.ColumnBounds(171, 1));
        map.put(BiomeKeys.WARPED_FOREST.getValue(), new ColormapProperties.ColumnBounds(172, 1));
        map.put(BiomeKeys.BASALT_DELTAS.getValue(), new ColormapProperties.ColumnBounds(173, 1));
        // 1.17 cave biomes
        map.put(BiomeKeys.DRIPSTONE_CAVES.getValue(), new ColormapProperties.ColumnBounds(174, 1));
        map.put(BiomeKeys.LUSH_CAVES.getValue(), new ColormapProperties.ColumnBounds(175, 1));
        // 1.18 highland biomes
        // meadow -> plains
        map.put(BiomeKeys.MEADOW.getValue(), new ColormapProperties.ColumnBounds(1, 1));
        // grove -> snowy taiga
        map.put(BiomeKeys.GROVE.getValue(), new ColormapProperties.ColumnBounds(30, 1));
        // snowy slopes -> snowy plains
        map.put(BiomeKeys.SNOWY_SLOPES.getValue(), new ColormapProperties.ColumnBounds(12, 1));
        map.put(BiomeKeys.FROZEN_PEAKS.getValue(), new ColormapProperties.ColumnBounds(12, 1));
        // non-snow peaks -> windswept hills
        map.put(BiomeKeys.JAGGED_PEAKS.getValue(), new ColormapProperties.ColumnBounds(3, 1));
        map.put(BiomeKeys.STONY_PEAKS.getValue(), new ColormapProperties.ColumnBounds(3, 1));
        // 1.19 wild biomes
        // mangrove swamp -> swamp
        map.put(BiomeKeys.MANGROVE_SWAMP.getValue(), new ColormapProperties.ColumnBounds(6, 1));
        // deep dark -> lush caves
        map.put(BiomeKeys.DEEP_DARK.getValue(), new ColormapProperties.ColumnBounds(175, 1));
        return map;
    }


    private static Map<Identifier, ColormapProperties.ColumnBounds> createStableColumnBounds() {
        // taken from biome raw IDs but frozen in perpetuity
        var map = new HashMap<Identifier, ColormapProperties.ColumnBounds>();
        // 1.18
        map.put(BiomeKeys.THE_VOID.getValue(), new ColormapProperties.ColumnBounds(0, 1));
        map.put(BiomeKeys.PLAINS.getValue(), new ColormapProperties.ColumnBounds(1, 1));
        map.put(BiomeKeys.SUNFLOWER_PLAINS.getValue(), new ColormapProperties.ColumnBounds(2, 1));
        map.put(BiomeKeys.SNOWY_PLAINS.getValue(), new ColormapProperties.ColumnBounds(3, 1));
        map.put(BiomeKeys.ICE_SPIKES.getValue(), new ColormapProperties.ColumnBounds(4, 1));
        map.put(BiomeKeys.DESERT.getValue(), new ColormapProperties.ColumnBounds(5, 1));
        map.put(BiomeKeys.SWAMP.getValue(), new ColormapProperties.ColumnBounds(6, 1));
        map.put(BiomeKeys.FOREST.getValue(), new ColormapProperties.ColumnBounds(7, 1));
        map.put(BiomeKeys.FLOWER_FOREST.getValue(), new ColormapProperties.ColumnBounds(8, 1));
        map.put(BiomeKeys.BIRCH_FOREST.getValue(), new ColormapProperties.ColumnBounds(9, 1));
        map.put(BiomeKeys.DARK_FOREST.getValue(), new ColormapProperties.ColumnBounds(10, 1));
        map.put(BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue(), new ColormapProperties.ColumnBounds(11, 1));
        map.put(BiomeKeys.OLD_GROWTH_PINE_TAIGA.getValue(), new ColormapProperties.ColumnBounds(12, 1));
        map.put(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue(), new ColormapProperties.ColumnBounds(13, 1));
        map.put(BiomeKeys.TAIGA.getValue(), new ColormapProperties.ColumnBounds(14, 1));
        map.put(BiomeKeys.SNOWY_TAIGA.getValue(), new ColormapProperties.ColumnBounds(15, 1));
        map.put(BiomeKeys.SAVANNA.getValue(), new ColormapProperties.ColumnBounds(16, 1));
        map.put(BiomeKeys.SAVANNA_PLATEAU.getValue(), new ColormapProperties.ColumnBounds(17, 1));
        map.put(BiomeKeys.WINDSWEPT_HILLS.getValue(), new ColormapProperties.ColumnBounds(18, 1));
        map.put(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue(), new ColormapProperties.ColumnBounds(19, 1));
        map.put(BiomeKeys.WINDSWEPT_FOREST.getValue(), new ColormapProperties.ColumnBounds(20, 1));
        map.put(BiomeKeys.WINDSWEPT_SAVANNA.getValue(), new ColormapProperties.ColumnBounds(21, 1));
        map.put(BiomeKeys.JUNGLE.getValue(), new ColormapProperties.ColumnBounds(22, 1));
        map.put(BiomeKeys.SPARSE_JUNGLE.getValue(), new ColormapProperties.ColumnBounds(23, 1));
        map.put(BiomeKeys.BAMBOO_JUNGLE.getValue(), new ColormapProperties.ColumnBounds(24, 1));
        map.put(BiomeKeys.BADLANDS.getValue(), new ColormapProperties.ColumnBounds(25, 1));
        map.put(BiomeKeys.ERODED_BADLANDS.getValue(), new ColormapProperties.ColumnBounds(26, 1));
        map.put(BiomeKeys.WOODED_BADLANDS.getValue(), new ColormapProperties.ColumnBounds(27, 1));
        map.put(BiomeKeys.MEADOW.getValue(), new ColormapProperties.ColumnBounds(28, 1));
        map.put(BiomeKeys.GROVE.getValue(), new ColormapProperties.ColumnBounds(29, 1));
        map.put(BiomeKeys.SNOWY_SLOPES.getValue(), new ColormapProperties.ColumnBounds(30, 1));
        map.put(BiomeKeys.FROZEN_PEAKS.getValue(), new ColormapProperties.ColumnBounds(31, 1));
        map.put(BiomeKeys.JAGGED_PEAKS.getValue(), new ColormapProperties.ColumnBounds(32, 1));
        map.put(BiomeKeys.STONY_PEAKS.getValue(), new ColormapProperties.ColumnBounds(33, 1));
        map.put(BiomeKeys.RIVER.getValue(), new ColormapProperties.ColumnBounds(34, 1));
        map.put(BiomeKeys.FROZEN_RIVER.getValue(), new ColormapProperties.ColumnBounds(35, 1));
        map.put(BiomeKeys.BEACH.getValue(), new ColormapProperties.ColumnBounds(36, 1));
        map.put(BiomeKeys.SNOWY_BEACH.getValue(), new ColormapProperties.ColumnBounds(37, 1));
        map.put(BiomeKeys.STONY_SHORE.getValue(), new ColormapProperties.ColumnBounds(38, 1));
        map.put(BiomeKeys.WARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(39, 1));
        map.put(BiomeKeys.LUKEWARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(40, 1));
        map.put(BiomeKeys.DEEP_LUKEWARM_OCEAN.getValue(), new ColormapProperties.ColumnBounds(41, 1));
        map.put(BiomeKeys.OCEAN.getValue(), new ColormapProperties.ColumnBounds(42, 1));
        map.put(BiomeKeys.DEEP_OCEAN.getValue(), new ColormapProperties.ColumnBounds(43, 1));
        map.put(BiomeKeys.COLD_OCEAN.getValue(), new ColormapProperties.ColumnBounds(44, 1));
        map.put(BiomeKeys.DEEP_COLD_OCEAN.getValue(), new ColormapProperties.ColumnBounds(45, 1));
        map.put(BiomeKeys.FROZEN_OCEAN.getValue(), new ColormapProperties.ColumnBounds(46, 1));
        map.put(BiomeKeys.DEEP_FROZEN_OCEAN.getValue(), new ColormapProperties.ColumnBounds(47, 1));
        map.put(BiomeKeys.MUSHROOM_FIELDS.getValue(), new ColormapProperties.ColumnBounds(48, 1));
        map.put(BiomeKeys.DRIPSTONE_CAVES.getValue(), new ColormapProperties.ColumnBounds(49, 1));
        map.put(BiomeKeys.LUSH_CAVES.getValue(), new ColormapProperties.ColumnBounds(50, 1));
        map.put(BiomeKeys.NETHER_WASTES.getValue(), new ColormapProperties.ColumnBounds(51, 1));
        map.put(BiomeKeys.WARPED_FOREST.getValue(), new ColormapProperties.ColumnBounds(52, 1));
        map.put(BiomeKeys.CRIMSON_FOREST.getValue(), new ColormapProperties.ColumnBounds(53, 1));
        map.put(BiomeKeys.SOUL_SAND_VALLEY.getValue(), new ColormapProperties.ColumnBounds(54, 1));
        map.put(BiomeKeys.BASALT_DELTAS.getValue(), new ColormapProperties.ColumnBounds(55, 1));
        map.put(BiomeKeys.THE_END.getValue(), new ColormapProperties.ColumnBounds(56, 1));
        map.put(BiomeKeys.END_HIGHLANDS.getValue(), new ColormapProperties.ColumnBounds(57, 1));
        map.put(BiomeKeys.END_MIDLANDS.getValue(), new ColormapProperties.ColumnBounds(58, 1));
        map.put(BiomeKeys.SMALL_END_ISLANDS.getValue(), new ColormapProperties.ColumnBounds(59, 1));
        map.put(BiomeKeys.END_BARRENS.getValue(), new ColormapProperties.ColumnBounds(60, 1));
        // 1.19
        map.put(BiomeKeys.MANGROVE_SWAMP.getValue(), new ColormapProperties.ColumnBounds(61, 1));
        map.put(BiomeKeys.DEEP_DARK.getValue(), new ColormapProperties.ColumnBounds(62, 1));
        return map;
    }
}
