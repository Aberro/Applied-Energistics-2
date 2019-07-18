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

package appeng.util.inv;


import java.util.Iterator;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.InventoryAdaptor;
import appeng.util.item.AEItemStack;


public abstract class IMEAdaptor extends InventoryAdaptor
{

	private final IMEInventory target;
	private final IActionSource src;
	private int maxSlots = 0;

	public IMEAdaptor(final IMEInventory input, final IActionSource src )
	{
		this.target = input;
		this.src = src;
	}

	@Override
	public boolean hasSlots()
	{
		return true;
	}

	@Override
	public Iterator<ISlot> iterator()
	{
		return new IMEAdaptorIterator( this, this.getList() );
	}

	protected abstract IItemList<IAEStack> getList();

	@Override
	public IAEStack removeItems( final long amount, final IAEStack filter, final IInventoryDestination destination )
	{
		return this.doRemoveItems( amount, filter, destination, Actionable.MODULATE );
	}

	private IAEStack doRemoveItems( final long amount, final IAEStack filter, final IInventoryDestination destination, final Actionable type )
	{
		IAEStack req = null;

		if( filter.isEmpty() )
		{
			final IItemList<IAEStack> list = this.getList();
			if( !list.isEmpty() )
			{
				req = list.getFirstItem();
			}
		}
		else
		{
			req = filter;
		}

		IAEStack out = null;

		if( req != null )
		{
			req.setStackSize( amount );
			out = this.target.extractItems( req, type, this.src );
		}

		if( out != null )
		{
			return out;
		}

		return null;
	}

	@Override
	public IAEStack simulateRemove( final long amount, final IAEStack filter, final IInventoryDestination destination )
	{
		return this.doRemoveItems( amount, filter, destination, Actionable.SIMULATE );
	}

	@Override
	public IAEStack removeSimilarItems( final long amount, final IAEStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination )
	{
		if( filter.isEmpty() )
		{
			return this.doRemoveItems( amount, null, destination, Actionable.MODULATE );
		}
		return this.doRemoveItemsFuzzy( amount, filter, destination, Actionable.MODULATE, fuzzyMode );
	}

	private IAEStack doRemoveItemsFuzzy( final long amount, final IAEStack filter, final IInventoryDestination destination, final Actionable type, final FuzzyMode fuzzyMode )
	{
		if( filter == null )
		{
			return null;
		}

		IAEStack out = null;

		for( final IAEStack req : ImmutableList.copyOf( this.getList().findFuzzy( filter, fuzzyMode ) ) )
		{
			if( req != null )
			{
				req.setStackSize( amount );
				out = this.target.extractItems( req, type, this.src );
				if( out != null && out.getChannel() == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) )
				{
					return out;
				}
			}
		}

		return null;
	}

	@Override
	public IAEStack simulateSimilarRemove( final long amount, final IAEStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination )
	{
		if( filter.isEmpty() )
		{
			return this.doRemoveItems( amount, null, destination, Actionable.SIMULATE );
		}
		return this.doRemoveItemsFuzzy( amount, filter, destination, Actionable.SIMULATE, fuzzyMode );
	}

	@Override
	public IAEStack addItems( final IAEStack toBeAdded )
	{
		final IAEStack in = toBeAdded;
		if( in != null )
		{
			final IAEStack out = this.target.injectItems( in, Actionable.MODULATE, this.src );
			if( out != null && out.getChannel() == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class))
			{
				return out;
			}
		}
		return null;
	}

	@Override
	public IAEStack simulateAdd( final IAEStack toBeSimulated )
	{
		final IAEStack in = toBeSimulated;
		if( in != null )
		{
			final IAEStack out = this.target.injectItems( in, Actionable.SIMULATE, this.src );
			if( out != null && out.getChannel() == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) )
			{
				return out;
			}
		}
		return null;
	}

	@Override
	public boolean containsItems()
	{
		return !this.getList().isEmpty();
	}

	int getMaxSlots()
	{
		return this.maxSlots;
	}

	void setMaxSlots( final int maxSlots )
	{
		this.maxSlots = maxSlots;
	}
}
