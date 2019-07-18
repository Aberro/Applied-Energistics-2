package appeng.crafting;

import akka.util.Index;
import appeng.api.AEApi;
import appeng.api.networking.crafting.IInventoryCrafting;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.util.item.AEStack;
import appeng.util.item.MixedList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryCrafting implements IInventoryCrafting
{
    Map<IStorageChannel, IAEStack[]> inventory = new HashMap<>();

    public InventoryCrafting()
    {
        for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
            inventory.put(channel, null);
    }
    @Override
    public int getSlotsCount(IStorageChannel channel)
    {
        if(inventory.containsKey(channel))
            return inventory.get(channel).length;
        return 0;
    }

    @Override
    public void setSlotsCount(IStorageChannel channel, int count)
    {
        if(!inventory.containsKey(channel))
            return;
        IAEStack[] tmp = inventory.get(channel);
        IAEStack[] newInv = new AEStack[count];
        System.arraycopy(tmp, 0, newInv, 0, Math.min(tmp.length, count));
        inventory.put(channel, newInv);
    }

    @Override
    public boolean isEmpty()
    {
        for (IAEStack[] inv : inventory.values())
            for (IAEStack slot : inv)
                if (slot != null && !slot.isEmpty())
                    return false;
        return true;
    }

    @Override
    public IAEStack getStackInSlot(IStorageChannel channel, int index)
    {
        if(!inventory.containsKey(channel))
            return null;
        return inventory.get(channel)[index];
    }

    @Override
    public IAEStack removeStackFromSlot(IStorageChannel channel, int index)
    {
        if(!inventory.containsKey(channel))
            return null;
        IAEStack[] tmp = inventory.get(channel);
        IAEStack result = tmp[index];
        tmp[index] = null;
        return result;
    }

    @Override
    public void setStackInSlot(IStorageChannel channel, int index, IAEStack stack)
    {
        if(!inventory.containsKey(channel))
            throw new IndexOutOfBoundsException();
        IAEStack[] tmp =inventory.get(channel);
        if(index > tmp.length)
            throw new IndexOutOfBoundsException();
        tmp[index] = stack;
    }

    @Override
    public void clear()
    {
        for(IStorageChannel channel : inventory.keySet())
        {
            IAEStack[] slots = inventory.get(channel);
            for( int i = 0; i < slots.length; i++)
                slots[i] = null;
        }
    }

    @Override
    public net.minecraft.inventory.InventoryCrafting toMCInventoryCrafting()
    {
        IStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        for(IStorageChannel ch : inventory.keySet())
            if(ch != channel && getSlotsCount(ch) > 0)
                throw new IllegalStateException("Can't be transformed into Minecraft InventoryCrafting!");
        if(getSlotsCount(channel) != 9)
            throw new IllegalStateException("Can't be transformed into Minecraft InventoryCrafting!");
        IAEStack[] tmp = inventory.get(channel);
        return new net.minecraft.inventory.InventoryCrafting(new ContainerNull(), 3, 3);
    }
}
