package appeng.util;


import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.AdaptorItemHandlerPlayerInv;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public abstract class ItemInventoryAdaptor extends InventoryAdaptor
{
    public static ItemInventoryAdaptor getAdaptor(final TileEntity te, final EnumFacing d )
    {
        if( te != null && te.hasCapability( CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d ) )
        {
            // Attempt getting an IItemHandler for the given side via caps
            IItemHandler itemHandler = te.getCapability( CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d );
            if( itemHandler != null )
            {
                return new AdaptorItemHandler( itemHandler );
            }
        }
        return null;
    }

    public static ItemInventoryAdaptor getAdaptor( final EntityPlayer te )
    {
        if( te != null )
        {
            return new AdaptorItemHandlerPlayerInv( te );
        }
        return null;
    }

    public IStorageChannel getChannel()
    {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }
}
