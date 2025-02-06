package com.MeteorModified.addon.modules;

import com.MeteorModified.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import com.MeteorModified.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import java.util.function.BiPredicate;


import java.util.HashSet;
import java.util.Set;

public class BetterAutoEat extends Module {

    private static final Class<? extends Module>[] AURAS = new Class[]{KillAura.class, CrystalAura.class, AnchorAura.class, BedAura.class};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreshold = settings.createGroup("Threshold");

    public final Setting<Set<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("blacklist")
        .description("Which items to not eat.")
        .defaultValue(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHICKEN, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.SUSPICIOUS_STEW)
        .filter(item -> item.getComponents().get(DataComponentTypes.FOOD) != null)
        .build()
    );

    private final Setting<Boolean> pauseAuras = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-auras")
        .description("Pauses all auras when eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Pause baritone when eating.")
        .defaultValue(true)
        .build()
    );

    // Threshold
    private final Setting<ThresholdMode> thresholdMode = sgThreshold.add(new EnumSetting.Builder<ThresholdMode>()
        .name("threshold-mode")
        .description("The threshold mode to trigger auto eat.")
        .defaultValue(ThresholdMode.Any)
        .build()
    );

    private final Setting<Double> healthThreshold = sgThreshold.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("The level of health you eat at.")
        .defaultValue(10)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Hunger)
        .build()
    );

    private final Setting<Integer> hungerThreshold = sgThreshold.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("The level of hunger you eat at.")
        .defaultValue(16)
        .range(1, 19)
        .sliderRange(1, 19)
        .visible(() -> thresholdMode.get() != ThresholdMode.Health)
        .build()
    );

    private boolean eating;
    private int slot, prevSlot;
    private final Set<Class<? extends Module>> wasAura = new HashSet<>();
    private boolean wasBaritone = false;

    public BetterAutoEat() {
        super(AddonTemplate.CATEGORY, "better-auto-eat", "Automatically eats food.");
    }

    @Override
    public void onDeactivate() {
        if (eating) stopEating();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onTick(TickEvent.Pre event) {
        // Skip if Auto Gap is already eating
        if (Modules.get().get(AutoGap.class).isEating()) return;

        if (eating) {
            if (shouldEat()) {
                if (mc.player.getInventory().getStack(slot).get(DataComponentTypes.FOOD) == null) {
                    int newSlot = findSlot();
                    if (newSlot == -1) {
                        stopEating();
                        return;
                    }
                    changeSlot(newSlot);
                }
                eat();
            } else {
                stopEating();
            }
        } else {
            if (shouldEat()) {
                int newSlot = findSlot();
                if (newSlot != -1) startEating();
            }
        }
    }

    @EventHandler
    private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
        if (eating) event.target = null;
    }

    private void startEating() {
        prevSlot = mc.player.getInventory().selectedSlot;
        eat();

        pauseAuras();
        pauseBaritone();
    }

    private void pauseAuras() {
        wasAura.clear();
        if (pauseAuras.get()) {
            for (Class<? extends Module> aura : AURAS) {
                Module module = Modules.get().get(aura);
                if (module.isActive()) {
                    wasAura.add(aura);
                    module.toggle();
                }
            }
        }
    }

    private void pauseBaritone() {
        if (pauseBaritone.get() && PathManagers.get().isPathing() && !wasBaritone) {
            wasBaritone = true;
            PathManagers.get().pause();
        }
    }

    private void eat() {
        changeSlot(slot);
        setPressed(true);
        if (!mc.player.isUsingItem()) Utils.rightClick();
        eating = true;
    }

    private void stopEating() {
        changeSlot(prevSlot);
        setPressed(false);
        eating = false;

        resumeAuras();
        resumeBaritone();
    }

    private void resumeAuras() {
        if (pauseAuras.get()) {
            for (Class<? extends Module> aura : AURAS) {
                Module module = Modules.get().get(aura);
                if (wasAura.contains(aura) && !module.isActive()) {
                    module.toggle();
                }
            }
        }
    }

    private void resumeBaritone() {
        if (pauseBaritone.get() && wasBaritone) {
            wasBaritone = false;
            PathManagers.get().resume();
        }
    }

    private void setPressed(boolean pressed) {
        mc.options.useKey.setPressed(pressed);
    }

    private void changeSlot(int slot) {
        InvUtils.swap(slot, false);
        this.slot = slot;
    }

    public boolean shouldEat() {
        boolean health = mc.player.getHealth() <= healthThreshold.get();
        boolean hunger = mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.get();
        return thresholdMode.get().test(health, hunger);
    }

    private int findSlot() {
        int bestSlot = -1;
        int bestHunger = -1;

        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            FoodComponent foodComponent = item.getComponents().get(DataComponentTypes.FOOD);
            if (foodComponent == null) continue;

            int hunger = foodComponent.nutrition();
            if (hunger > bestHunger && !blacklist.get().contains(item)) {
                bestSlot = i;
                bestHunger = hunger;
            }
        }

        Item offHandItem = mc.player.getOffHandStack().getItem();
        FoodComponent offHandFood = offHandItem.getComponents().get(DataComponentTypes.FOOD);
        if (offHandFood != null && !blacklist.get().contains(offHandItem) && offHandFood.nutrition() > bestHunger) {
            bestSlot = SlotUtils.OFFHAND;
        }

        return bestSlot;
    }

    public enum ThresholdMode {
        Health((health, hunger) -> health),
        Hunger((health, hunger) -> hunger),
        Any((health, hunger) -> health || hunger),
        Both((health, hunger) -> health && hunger);

        private final BiPredicate<Boolean, Boolean> predicate;

        ThresholdMode(BiPredicate<Boolean, Boolean> predicate) {
            this.predicate = predicate;
        }

        public boolean test(boolean health, boolean hunger) {
            return predicate.test(health, hunger);
        }
    }
}
