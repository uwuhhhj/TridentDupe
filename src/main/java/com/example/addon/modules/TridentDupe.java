/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.example.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TridentDupe extends Module {
    // 作者 Killet Laztec & Ionar :3
    private final SettingGroup sgGeneral = settings.getDefaultGroup(); // 设置分组，所有设置都归在默认分组下
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("dupe-delay") // 设置名称：复制循环的延迟
        .description("Delay between each dupe cycle. Unlikely to need increasing.") // 设置描述：每次复制循环之间的延迟，通常无需增加
        .defaultValue(0) // 默认值为0毫秒
        .build()
    );

    private final Setting<Double> chargeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("charge-delay") // 设置名称：三叉戟蓄力与投掷之间的延迟
        .description("Delay between trident charge and throw. Increase if experiencing issues/lag.") // 设置描述：三叉戟蓄力和投掷之间的延迟，遇到卡顿可适当增加
        .defaultValue(5) // 默认值为5毫秒
        .build()
    );

    private final Setting<Boolean> dropTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("dropTridents") // 设置名称：是否丢弃三叉戟
        .description("Drops tridents in your last hotbar slot.") // 设置描述：是否丢弃你快捷栏最后一格的三叉戟
        .defaultValue(true) // 默认开启
        .build()
    );

    private final Setting<Boolean> durabilityManagement = sgGeneral.add(new BoolSetting.Builder()
        .name("durabilityManagement") // 设置名称：耐久管理
        .description("(More AFKable) Attempts to dupe the highest durability trident in your hotbar.") // 设置描述：更适合挂机，优先复制耐久最高的三叉戟
        .defaultValue(true) // 默认开启
        .build()
    );

    public TridentDupe() {
        // 调用父类构造方法，指定模块分类、名称和描述
        super(com.example.addon.TridentDupe.CATEGORY, "trident-dupe", "Dupes tridents in first hotbar slot. / / Killet / / Laztec / / Ionar");
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        // 拦截部分数据包，防止服务器检测到异常操作
        if (event.packet instanceof ClientTickEndC2SPacket
            || event.packet instanceof PlayerMoveC2SPacket
            || event.packet instanceof CloseHandledScreenC2SPacket)
            return; // 如果是这三种数据包，直接放行

        if (!(event.packet instanceof ClickSlotC2SPacket)
            && !(event.packet instanceof PlayerActionC2SPacket))
        {
            return; // 如果不是物品栏点击或玩家动作数据包，也直接放行
        }
        if (!cancel)
            return; // 如果cancel为false，不拦截

//        MutableText packetStr = Text.literal(event.packet.toString()).formatted(Formatting.WHITE);
//        System.out.println(packetStr);

        event.cancel(); // 拦截并取消该数据包的发送
    }

    @Override
    public void onActivate()
    {
        if (mc.player == null)
            return;

        scheduledTasks.clear();
        dupe();

    }

    private void dupe()
    {
        int lowestHotbarSlot = 0;
        int lowestHotbarDamage = 1000;
        for (int i = 0; i < 9; i++)
        {
            if (mc.player.getInventory().getStack((i)).getItem() == Items.TRIDENT || mc.player.getInventory().getStack((i)).getItem() == Items.BOW)
            {
                int currentHotbarDamage = mc.player.getInventory().getStack((i)).getDamage();
                if (lowestHotbarDamage > currentHotbarDamage) { lowestHotbarSlot = i; lowestHotbarDamage = currentHotbarDamage;}

            }
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        cancel = true;

        int finalLowestHotbarSlot = lowestHotbarSlot;
        scheduleTask(() -> { // 安排一个延迟任务（chargeDelay毫秒后执行），用于处理三叉戟复制的后续操作
            cancel = false; // 允许数据包正常发送（解除拦截），为后续操作做准备

            // 如果启用了耐久管理功能
            if(durabilityManagement.get()) {
                // 如果选中的三叉戟不在第一个快捷栏（0号位）
                if(finalLowestHotbarSlot != 0) {
                    // 将副手（44号槽）与选中的三叉戟槽交换
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, (44), 0, SlotActionType.SWAP, mc.player);
                    // 如果启用了自动丢弃三叉戟，丢弃副手（44号槽）中的三叉戟
                    if(dropTridents.get())
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 44, 0, SlotActionType.THROW, mc.player);
                    // 再把原本选中的三叉戟槽和副手（44号槽）交换回来
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, (36 + finalLowestHotbarSlot), 0, SlotActionType.SWAP, mc.player);
                }
            }

            // 将第4号槽（通常是背包的某个槽位）与当前主手物品交换
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, 0, SlotActionType.SWAP, mc.player);

            // 发送"释放使用物品"数据包，模拟玩家松开右键（即投掷三叉戟）
            PlayerActionC2SPacket packet2 = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0);
            mc.getNetworkHandler().sendPacket(packet2);

            // 如果启用了自动丢弃三叉戟，再次丢弃副手（44号槽）中的三叉戟
            if(dropTridents.get())
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 44, 0, SlotActionType.THROW, mc.player);

            cancel = true; // 再次开启数据包拦截，防止后续操作被服务器检测

            // 安排下一个复制循环（delay毫秒后再次执行dupe方法，实现自动挂机）
            scheduleTask2(this::dupe, delay.get() * 100);
        }, chargeDelay.get() * 100); // 本次任务将在chargeDelay毫秒后执行
    }


    private boolean cancel = true;

    private final List<Pair<Long, Runnable>> scheduledTasks = new ArrayList<>();
    private final List<Pair<Double, Runnable>> scheduledTasks2 = new ArrayList<>();

    public void scheduleTask(Runnable task, double tridentThrowTime) {
        // throw trident
        long executeTime = System.currentTimeMillis() + (int) tridentThrowTime;
        scheduledTasks.add(new Pair<>(executeTime, task));
    }

    public void scheduleTask2(Runnable task, double delayMillis) {
        // dupe loop
        double executeTime = System.currentTimeMillis() + delayMillis;
        scheduledTasks2.add(new Pair<>(executeTime, task));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long currentTime = System.currentTimeMillis();
        {
            Iterator<Pair<Long, Runnable>> iterator = scheduledTasks.iterator();

            while (iterator.hasNext()) {
                Pair<Long, Runnable> entry = iterator.next();
                if (entry.getLeft() <= currentTime) {
                    entry.getRight().run();
                    iterator.remove(); // Remove executed task from the list
                }
            }
        }

        {
            Iterator<Pair<Double, Runnable>> iterator = scheduledTasks2.iterator();

            while (iterator.hasNext()) {
                Pair<Double, Runnable> entry = iterator.next();
                if (entry.getLeft() <= currentTime) {
                    entry.getRight().run();
                    iterator.remove(); // Remove executed task from the list
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }
}
