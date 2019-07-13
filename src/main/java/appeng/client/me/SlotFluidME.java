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

package appeng.client.me;


import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.container.slots.IMEFluidSlot;


/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class SlotFluidME extends SlotItemHandler implements IMEFluidSlot
{
	private final int offset;
	private final FluidRepo repo;

	private IAEFluidStack getAEStack()
	{
		return this.repo.getReferenceFluid( this.offset );
	}

	public SlotFluidME( final FluidRepo def, final int offset, final int displayX, final int displayY )
	{
		super( null, 0, displayX, displayY );
		this.repo = def;
		this.offset = offset;
	}

	@Override
	public IAEFluidStack getAEFluidStack()
	{
		if( this.repo.hasPower() )
		{
			return this.getAEStack();
		}
		return null;
	}

	@Override
	public boolean isItemValid( final ItemStack par1ItemStack )
	{
		return false;
	}

	@Nonnull
	@Override
	public ItemStack getStack()
	{
		return ItemStack.EMPTY;
	}

	@Override
	public boolean getHasStack()
	{
		if( this.repo.hasPower() )
		{
			return this.getAEFluidStack() != null;
		}
		return false;
	}

	@Override
	public void putStack( final ItemStack par1ItemStack )
	{

	}

	@Override
	public int getSlotStackLimit()
	{
		return 0;
	}

	@Nonnull
	@Override
	public ItemStack decrStackSize( final int par1 )
	{
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isHere( final IInventory inv, final int slotIn )
	{
		return false;
	}

	@Override
	public boolean canTakeStack( final EntityPlayer par1EntityPlayer )
	{
		return false;
	}
}
