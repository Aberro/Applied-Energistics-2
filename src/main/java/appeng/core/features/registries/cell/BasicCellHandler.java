/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.core.features.registries.cell;


import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.ISlot;
import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.BasicCellInventory;
import appeng.me.storage.BasicCellInventoryHandler;


public abstract class BasicCellHandler<TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack> implements ICellHandler<TAEStack, TSlot, TStack>
{
	private IStorageChannel channel;
	protected BasicCellHandler(IStorageChannel<TAEStack, TSlot, TStack> channel)
	{
		this.channel = channel;
	}

	@Override
	public IStorageChannel<TAEStack, TSlot, TStack> getChannel() { return this.channel; }

	@Override
	public boolean isCell( final ItemStack is )
	{
		return BasicCellInventory.isCell( is, getChannel() );
	}

	@Override
	public ICellInventoryHandler<TAEStack, TSlot, TStack> getCellInventory(final ItemStack is, final ISaveProvider container)
	{
		final ICellInventory<TAEStack, TSlot, TStack> inv = BasicCellInventory.createInventory( getChannel(), is, container );
		if( inv == null || inv.getChannel() != getChannel() )
		{
			return null;
		}
		return new BasicCellInventoryHandler<>( inv, getChannel() );
	}

}
