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
import java.util.List;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.iterators.StackToSlotIterator;


public class AdaptorList extends InventoryAdaptor
{

	private final List<IAEStack> i;

	public AdaptorList( final List<IAEStack> s )
	{
		this.i = s;
	}

	@Override
	public boolean hasSlots()
	{
		return !this.i.isEmpty();
	}

	@Override
	public IAEStack removeItems( long amount, final IAEStack filter, final IInventoryDestination destination )
	{
		final int s = this.i.size();
		for( int x = 0; x < s; x++ )
		{
			final IAEStack is = this.i.get( x );
			if( !is.isEmpty() && ( filter.isEmpty() || is.equals(filter) ) )
			{
				if( amount > is.getStackSize() )
				{
					amount = is.getStackSize();
				}
				if( destination != null && !destination.canInsert( is ) )
				{
					amount = 0;
				}

				if( amount > 0 )
				{
					final IAEStack rv = is.copy();
					rv.setStackSize( amount );
					is.decStackSize( amount );

					// remove it..
					if( is.getStackSize() <= 0 )
					{
						this.i.remove( x );
					}

					return rv;
				}
			}
		}
		return null;
	}

	@Override
	public IAEStack simulateRemove( long amount, final IAEStack filter, final IInventoryDestination destination )
	{
		for( final IAEStack is : this.i )
		{
			if( !is.isEmpty() && ( filter.isEmpty() || is.equals( filter ) ) )
			{
				if( amount > is.getStackSize() )
				{
					amount = is.getStackSize();
				}
				if( destination != null && !destination.canInsert( is ) )
				{
					amount = 0;
				}

				if( amount > 0 )
				{
					final IAEStack rv = is.copy();
					rv.setStackSize( amount );
					return rv;
				}
			}
		}
		return null;
	}

	@Override
	public IAEStack removeSimilarItems( long amount, final IAEStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination )
	{
		final int s = this.i.size();
		for( int x = 0; x < s; x++ )
		{
			final IAEStack is = this.i.get( x );
			if( !is.isEmpty() && ( filter.isEmpty() || is.fuzzyComparison( filter, fuzzyMode ) ) )
			{
				if( amount > is.getStackSize() )
				{
					amount = is.getStackSize();
				}
				if( destination != null && !destination.canInsert( is ) )
				{
					amount = 0;
				}

				if( amount > 0 )
				{
					final IAEStack rv = is.copy();
					rv.setStackSize( amount );
					is.decStackSize( amount );

					// remove it..
					if( is.getStackSize() <= 0 )
					{
						this.i.remove( x );
					}

					return rv;
				}
			}
		}
		return null;
	}

	@Override
	public IAEStack simulateSimilarRemove( long amount, final IAEStack filter, final FuzzyMode fuzzyMode, final IInventoryDestination destination )
	{
		for( final IAEStack is : this.i )
		{
			if( !is.isEmpty() && ( filter.isEmpty() || is.fuzzyComparison( filter, fuzzyMode ) ) )
			{
				if( amount > is.getStackSize() )
				{
					amount = is.getStackSize();
				}
				if( destination != null && !destination.canInsert( is ) )
				{
					amount = 0;
				}

				if( amount > 0 )
				{
					final IAEStack rv = is.copy();
					rv.setStackSize( amount );
					return rv;
				}
			}
		}
		return null;
	}

	@Override
	public IAEStack addItems( final IAEStack toBeAdded )
	{
		if( toBeAdded == null || toBeAdded.isEmpty() )
		{
			return null;
		}

		final IAEStack left = toBeAdded.copy();

		for( final IAEStack is : this.i )
		{
			if( is.equals( left ) )
			{
				is.incStackSize( left.getStackSize() );
				return null;
			}
		}

		this.i.add( left );
		return null;
	}

	@Override
	public IAEStack simulateAdd( final IAEStack toBeSimulated )
	{
		return null;
	}

	@Override
	public boolean containsItems()
	{
		for( final IAEStack is : this.i )
		{
			if( !is.isEmpty() )
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<ISlot> iterator()
	{
		return new StackToSlotIterator( this.i.iterator() );
	}
}
