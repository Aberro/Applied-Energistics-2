/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.items;


import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import appeng.util.ItemInventoryAdaptor;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.helper.FluidCellConfig;
import appeng.items.materials.MaterialType;
import appeng.items.storage.AbstractStorageCell;


/**
 * @author DrummerMC
 * @version rv6 - 2018-01-17
 * @since rv6 2018-01-17
 */
public final class BasicFluidStorageCell extends AbstractStorageCell<IAEFluidStack, ISlot<FluidStack, IAEFluidStack>, FluidStack>
{

	private final int perType;
	private final double idleDrain;

	public BasicFluidStorageCell( final MaterialType whichCell, final int kilobytes )
	{
		super( whichCell, kilobytes );
		switch( whichCell )
		{
			case FLUID_CELL1K_PART:
				this.idleDrain = 0.5;
				this.perType = 8;
				break;
			case FLUID_CELL4K_PART:
				this.idleDrain = 1.0;
				this.perType = 32;
				break;
			case FLUID_CELL16K_PART:
				this.idleDrain = 1.5;
				this.perType = 128;
				break;
			case FLUID_CELL64K_PART:
				this.idleDrain = 2.0;
				this.perType = 512;
				break;
			default:
				this.idleDrain = 0.0;
				this.perType = 8;
		}

	}

	@Override
	public int getBytesPerType( ItemStack cellItem )
	{
		return this.perType;
	}

	@Override
	public double getIdleDrain()
	{
		return this.idleDrain;
	}

	@Override
	public IStorageChannel<IAEFluidStack, ISlot<FluidStack, IAEFluidStack>, FluidStack> getChannel()
	{
		return AEApi.instance().storage().getStorageChannel( IFluidStorageChannel.class );
	}

	@Override
	public int getTotalTypes( final ItemStack cellItem )
	{
		return 5;
	}

	@Override
	public IItemHandler getConfigInventory( final ItemStack is )
	{
		return new FluidCellConfig( is );
	}

	@Override
	protected void dropEmptyStorageCellCase(final ItemInventoryAdaptor ia, final EntityPlayer player )
	{
		AEApi.instance().definitions().materials().emptyStorageCell().maybeStack( 1 ).ifPresent( is ->
		{
			final IAEStack extraA = ia.addItems(AEItemStack.fromItemStack( is ) );
			if( !extraA.isEmpty() )
			{
				player.dropItem( (ItemStack)extraA.getStack(), false );
			}
		} );
	}
}