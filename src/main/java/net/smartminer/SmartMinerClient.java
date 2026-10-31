package net.smartminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SmartMinerClient implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean isEnabled = false;
    private static BlockPos targetBlock = null;
    private static int breakCooldown = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smartminer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.smartminer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                isEnabled = !isEnabled;
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal("§a[SmartMiner] " + (isEnabled ? "ВКЛ" : "ВЫКЛ")), true);
                }
            }

            if (isEnabled && client.player != null && client.world != null && client.interactionManager != null) {
                tick(client);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;

        // Исправленная проверка еды для 1.21+
        if (player.getHungerManager().getFoodLevel() < 15 && !player.getMainHandStack().isEmpty() && player.getMainHandStack().contains(DataComponentTypes.FOOD)) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            player.swingHand(Hand.MAIN_HAND);
            return;
        }

        if (targetBlock == null || client.world.getBlockState(targetBlock).isAir()) {
            targetBlock = findNearestOre(player);
        }

        if (targetBlock != null) {
            double dist = player.getPos().distanceTo(Vec3d.ofCenter(targetBlock));
            
            if (dist > 3.5) {
                lookAt(player, targetBlock);
                client.options.forwardKey.setPressed(true);
                if (player.horizontalCollision) client.options.jumpKey.setPressed(true);
            } else {
                client.options.forwardKey.setPressed(false);
                client.options.jumpKey.setPressed(false);
                lookAt(player, targetBlock);
                
                if (breakCooldown <= 0) {
                    client.interactionManager.updateBlockBreakingProgress(targetBlock, Direction.UP);
                    player.swingHand(Hand.MAIN_HAND);
                    breakCooldown = 2; 
                } else {
                    breakCooldown--;
                }
            }
        } else {
            client.options.forwardKey.setPressed(true);
        }
    }

    private static BlockPos findNearestOre(ClientPlayerEntity player) {
        BlockPos pPos = player.getBlockPos();
        BlockPos nearest = null;
        double bestDist = 12.0;

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = pPos.add(x, y, z);
                    var block = player.getWorld().getBlockState(pos).getBlock();
                    if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
                        double d = pPos.getSquaredDistance(pos);
                        if (d < bestDist) {
                            bestDist = d;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static void lookAt(ClientPlayerEntity player, BlockPos pos) {
        Vec3d target = Vec3d.ofCenter(pos);
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        float dYaw = Math.floorMod((int) (targetYaw - player.getYaw() + 180), 360) - 180;
        float dPitch = targetPitch - player.getPitch();

        player.setYaw(player.getYaw() + Math.signum(dYaw) * Math.min(Math.abs(dYaw), 15.0f));
        player.setPitch(player.getPitch() + Math.signum(dPitch) * Math.min(Math.abs(dPitch), 15.0f));
    }
}
