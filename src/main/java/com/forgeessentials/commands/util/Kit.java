package com.forgeessentials.commands.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.permission.PermissionManager;

import com.forgeessentials.commands.item.CommandKit;
import com.forgeessentials.core.misc.Translator;
import com.forgeessentials.util.PlayerInfo;
import com.forgeessentials.util.output.ChatOutputHandler;

public class Kit
{

    private String name;

    private int cooldown;

    private ItemStack[] items;

    private ItemStack[] armor;

    public Kit(EntityPlayer player, String name, int cooldown)
    {
        this.cooldown = cooldown;
        this.name = name;

        List<ItemStack> collapsedInventory = new ArrayList<ItemStack>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            if (player.inventory.mainInventory[i] != null)
                collapsedInventory.add(player.inventory.mainInventory[i]);
        items = collapsedInventory.toArray(new ItemStack[collapsedInventory.size()]);

        armor = new ItemStack[player.inventory.armorInventory.length];
        for (int i = 0; i < 4; i++)
            if (player.inventory.armorInventory[i] != null)
                armor[i] = player.inventory.armorInventory[i].copy();

        CommandDataManager.addKit(this);
    }

    public String getName()
    {
        return name;
    }

    public int getCooldown()
    {
        return cooldown;
    }

    public ItemStack[] getItems()
    {
        return items;
    }

    public ItemStack[] getArmor()
    {
        return armor;
    }

    public void giveKit(EntityPlayer player)
    {
        if (!PermissionManager.checkPermission(player, CommandKit.PERM_BYPASS_COOLDOWN) && cooldown > 0)
        {
            PlayerInfo pi = PlayerInfo.get(player.getPersistentID());
            long timeout = pi.getRemainingTimeout("KIT_" + name);
            if (timeout > 0)
            {
                ChatOutputHandler.chatWarning(player,
                        Translator.format("Kit cooldown active, %s to go!", ChatOutputHandler.formatTimeDurationReadable(timeout / 1000, true)));
                return;
            }
            pi.startTimeout("KIT_" + name, cooldown * 1000);
        }

        boolean couldNotGiveItems = false;

        for (ItemStack stack : items)
            couldNotGiveItems |= !player.inventory.addItemStackToInventory(ItemStack.copyItemStack(stack));

        for (int i = 0; i < 4; i++)
            if (armor[i] != null)
                if (player.inventory.armorInventory[i] == null)
                    player.inventory.armorInventory[i] = armor[i];
                else
                    couldNotGiveItems |= !player.inventory.addItemStackToInventory(ItemStack.copyItemStack(armor[i]));

        if (couldNotGiveItems)
            ChatOutputHandler.chatError(player, Translator.translate("Could not give some kit items."));
        ChatOutputHandler.chatConfirmation(player, "Kit dropped.");
    }

}
