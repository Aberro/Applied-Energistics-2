/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.slot;

import appeng.api.storage.data.IAEFluidStack;
import appeng.core.Api;
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

public class SlotFakeFluid extends SlotFake implements IOptionalSlot, IMEFluidSlot
{

    private final int srcX;
    private final int srcY;
    private final int groupNum;
    private final IOptionalSlotHost host;
    private boolean renderDisabled = true;

    public SlotFakeFluid( final IItemHandler inv, final IOptionalSlotHost containerBus, final int idx, final int x, final int y, final int offX, final int offY, final int groupNum )
    {
        super( inv, idx, x + offX * 18, y + offY * 18 );
        this.srcX = x;
        this.srcY = y;
        this.groupNum = groupNum;
        this.host = containerBus;
    }

    @Override
    @Nonnull
    public ItemStack getStack()
    {
        if( !this.isSlotEnabled() )
        {
            if( !this.getDisplayStack().isEmpty() )
            {
                this.clearStack();
            }
        }

        return super.getStack();
    }

    @Override
    public void putStack( ItemStack is )
    {
        if( is.isEmpty() || is.getItem() instanceof FluidDummyItem )
            super.putStack( is );
        FluidStack fluid = FluidUtil.getFluidContained( is );
        if( fluid == null || !Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack( 1 ).isPresent() )
        {
            return;
        }

        ItemStack dummyStack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack( 1 ).get();
        FluidDummyItem dummyItem = (FluidDummyItem) dummyStack.getItem();
        dummyItem.setFluidStack( dummyStack, fluid );
        super.putStack( dummyStack );
    }

    @Override
    public boolean isSlotEnabled()
    {
        if( this.host == null )
        {
            return false;
        }

        return this.host.isSlotEnabled( this.groupNum );
    }

    @Override
    public boolean isRenderDisabled()
    {
        return this.renderDisabled;
    }

    public void setRenderDisabled( final boolean renderDisabled )
    {
        this.renderDisabled = renderDisabled;
    }

    @Override
    public int getSourceX()
    {
        return this.srcX;
    }

    @Override
    public int getSourceY()
    {
        return this.srcY;
    }

    @Override
    public IAEFluidStack getAEFluidStack()
    {
        ItemStack dummyStack = getStack();
        Item item = dummyStack.getItem();
        if( item instanceof FluidDummyItem )
            return AEFluidStack.fromFluidStack( ( (FluidDummyItem)item ).getFluidStack(dummyStack) );
        return null;
    }
}
