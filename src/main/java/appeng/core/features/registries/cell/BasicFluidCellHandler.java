package appeng.core.features.registries.cell;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.util.ISlot;
import appeng.fluids.container.slots.IMEFluidSlot;
import net.minecraftforge.fluids.FluidStack;

public class BasicFluidCellHandler extends BasicCellHandler<IAEFluidStack, ISlot<FluidStack, IAEFluidStack>, FluidStack>
{
    public BasicFluidCellHandler()
    {
        super(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
    }
}
