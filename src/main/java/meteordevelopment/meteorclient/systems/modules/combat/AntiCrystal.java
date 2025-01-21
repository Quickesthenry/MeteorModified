package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AntiCrystal extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Anti-Crystal Block (Obsidian)
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Which blocks to use for self defense.")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many ticks between block placements.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends rotation packets to the server when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private int delay;
    private final List<BlockPos> placePositions = new ArrayList<>();

    public AntiCrystal() {
        super(Categories.Combat, "anti-crystal", "Detects End Crystals and places Obsidian blocks for defense.");
    }

    @Override
    public void onActivate() {
        placePositions.clear();
        delay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        World world = mc.player.getWorld();
        Vec3d playerPos = mc.player.getPos();

        // Bereich um den Spieler, in dem nach End Kristallen gesucht wird
        double radius = 10.0;
        Box searchArea = new Box(playerPos.add(-radius, -radius, -radius), playerPos.add(radius, radius, radius));

        // Suche nach End Kristallen in der Nähe des Spielers
        boolean foundEndCrystal = world.getEntitiesByClass(EndCrystalEntity.class, searchArea, entity -> true)
            .stream()
            .anyMatch(endCrystal -> endCrystal != null);

        if (foundEndCrystal) {
            System.out.println("End Kristall in der Nähe gefunden, Schutz wird aktiviert.");
            triggerSelfDefense();
        }

        if (delay >= delaySetting.get() && !placePositions.isEmpty()) {
            BlockPos blockPos = placePositions.get(0);

            FindItemResult itemResult = InvUtils.findInHotbar(Blocks.OBSIDIAN.asItem());
            if (itemResult.found()) {
                if (BlockUtils.place(blockPos, itemResult, rotate.get(), 50)) {
                    placePositions.remove(blockPos);
                }
            }

            delay = 0;
        } else {
            delay++;
        }
    }

    private void triggerSelfDefense() {
        BlockPos pos = mc.player.getBlockPos();
        placePositions.clear();

        // Selbstschutz durch Obsidian-Platzierung
        // Blöcke um und über dem Spieler
        add(pos.add(0, 2, 0), Blocks.OBSIDIAN);  // Block über dem Spieler
        add(pos.add(1, 1, 0), Blocks.OBSIDIAN);  // Block vor dem Spieler
        add(pos.add(-1, 1, 0), Blocks.OBSIDIAN); // Block hinter dem Spieler
        add(pos.add(0, 1, 1), Blocks.OBSIDIAN);  // Block rechts vom Spieler
        add(pos.add(0, 1, -1), Blocks.OBSIDIAN); // Block links vom Spieler

        // Neue Blöcke unterhalb des Spielers
        add(pos.add(0, -1, 0), Blocks.OBSIDIAN);  // Block unter dem Spieler
        add(pos.add(1, 0, 0), Blocks.OBSIDIAN);   // Block vor dem Spieler unten
        add(pos.add(-1, 0, 0), Blocks.OBSIDIAN);  // Block hinter dem Spieler unten
        add(pos.add(0, 0, 1), Blocks.OBSIDIAN);   // Block rechts vom Spieler unten
        add(pos.add(0, 0, -1), Blocks.OBSIDIAN);  // Block links vom Spieler unten
    }

    private void add(BlockPos blockPos, Block block) {
        if (!placePositions.contains(blockPos) &&
            mc.world.getBlockState(blockPos).isReplaceable() &&
            mc.world.canPlace(block.getDefaultState(), blockPos, ShapeContext.absent())) { // Korrektur
            placePositions.add(blockPos);
        }
    }

}

