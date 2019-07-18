package appeng.core.features.registries.cell;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.inv.ItemSlot;
import net.minecraft.item.ItemStack;

public class BasicItemCellHandler extends BasicCellHandler<IAEItemStack, ItemSlot, ItemStack>
{
    public BasicItemCellHandler()
    {
        super(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }
}
