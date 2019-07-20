package appeng.core.features.registries.cell;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.ISlot;
import appeng.util.inv.ItemSlot;
import net.minecraft.item.ItemStack;

public class BasicItemCellHandler extends BasicCellHandler<IAEItemStack, ISlot<ItemStack, IAEItemStack>, ItemStack>
{
    public BasicItemCellHandler()
    {
        super(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }
}
