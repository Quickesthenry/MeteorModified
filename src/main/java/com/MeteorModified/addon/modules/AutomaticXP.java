package com.MeteorModified.addon.modules;

import com.MeteorModified.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutomaticXP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private int repairingI;
    private final Setting<Boolean> replenish = sgGeneral.add(new BoolSetting.Builder().name("replenish").defaultValue(true).build());
    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder().name("hotbar-slot").defaultValue(1).min(1).max(9).build());

    public AutomaticXP() {
        super(AddonTemplate.CATEGORY, "automatic-exp", "Automatically uses Xp Bottles.");
    }

    @Override
    public void onActivate() {
        repairingI = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (repairingI == -1) {
            // Check armor slots for items that need repair
            for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                if (needsRepair()) {
                    repairingI = SlotUtils.ARMOR_START + i;
                    break;
                }
            }

            // Check hands for items that need repair
            for (Hand hand : Hand.values()) {
                if (needsRepair()) {
                    repairingI = hand == Hand.MAIN_HAND ? mc.player.getInventory().selectedSlot : SlotUtils.OFFHAND;
                    break;
                }
            }
        }

        // Only proceed if repairingI is valid
        if (repairingI != -1) {
            FindItemResult exp = InvUtils.find(Items.EXPERIENCE_BOTTLE);

            if (exp.found()) {
                // If the item isn't in the hotbar or offhand, move it there
                if (!exp.isHotbar() && !exp.isOffhand()) {
                    if (!replenish.get()) return;
                    InvUtils.move().from(exp.slot()).toHotbar(slot.get() - 1);
                }

                // Rotate and use the experience bottle
                Rotations.rotate(mc.player.getYaw(), 90, () -> {
                    if (exp.getHand() != null) {
                        mc.interactionManager.interactItem(mc.player, exp.getHand());
                    } else {
                        InvUtils.swap(exp.slot(), true);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        InvUtils.swapBack();
                    }
                });
            }
        }
    }

    private boolean needsRepair() {
        // Return true if repair is needed (example logic)
        return true;
    }

    public enum Mode {
        Armor,
        Hands,
        Both
    }
}
