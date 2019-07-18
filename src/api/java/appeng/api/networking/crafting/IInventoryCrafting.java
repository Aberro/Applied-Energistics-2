package appeng.api.networking.crafting;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

public interface IInventoryCrafting
{
    int getSlotsCount(IStorageChannel channel);
    void setSlotsCount(IStorageChannel channel, int count);
    boolean isEmpty();
    IAEStack getStackInSlot(IStorageChannel channel, int index);
    IAEStack removeStackFromSlot(IStorageChannel channel, int index);
    void setStackInSlot(IStorageChannel channel, int index, IAEStack stack);
    net.minecraft.inventory.InventoryCrafting toMCInventoryCrafting();
    default int getSlotStackLimit(IStorageChannel channel, int index)
    {
        return channel.getDefaultStackLimit();
    }
    default void markDirty() { }
    default boolean isItemValidForSlot(IStorageChannel channel, int index, IAEStack stack)
    {
        return stack.getChannel() == channel;
    }
    void clear();
}
