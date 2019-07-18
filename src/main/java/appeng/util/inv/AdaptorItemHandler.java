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

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import appeng.api.util.ItemInventoryAdaptor;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;


public class AdaptorItemHandler extends ItemInventoryAdaptor
{
	protected final IItemHandler itemHandler;
	private final IStorageChannel<IAEItemStack, ItemSlot, ItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

	public AdaptorItemHandler( IItemHandler itemHandler )
	{
		this.itemHandler = itemHandler;
	}



	@Override
	public boolean hasSlots()
	{
		return this.itemHandler.getSlots() > 0;
	}

	@Override
	public IAEStack removeItems(long amount, IAEStack filter, IInventoryDestination destination )
	{
		int slots = this.itemHandler.getSlots();
		IAEItemStack rv = null;

		for( int slot = 0; slot < slots && amount > 0; slot++ )
		{
			final ItemStack is = this.itemHandler.getStackInSlot( slot );
			if( is.isEmpty() || ( !filter.isEmpty() && !is.equals(filter) ) )
			{
				continue;
			}

			if( destination != null )
			{
				IAEStack extracted = AEItemStack.fromItemStack( this.itemHandler.extractItem( slot, (int)amount, true ) );
				if( extracted.isEmpty() )
				{
					continue;
				}

				if( !destination.canInsert( extracted ) )
				{
					continue;
				}
			}

			// Attempt extracting it
			IAEItemStack extracted = AEItemStack.fromItemStack(this.itemHandler.extractItem( slot, (int)amount, false ));

			if( extracted.isEmpty() )
			{
				continue;
			}

			if( rv == null || rv.isEmpty() )
			{
				// Use the first stack as a template for the result
				rv = extracted;
				filter = extracted;
				amount -= extracted.getStackSize();
			}
			else
			{
				// Subsequent stacks will just increase the extracted size
				rv.setStackSize(rv.getStackSize() + extracted.getStackSize() );
				amount -= extracted.getStackSize();
			}
		}

		return rv;
	}

	@Override
	public IAEStack simulateRemove( long amount, IAEStack filter, IInventoryDestination destination )
	{
		int slots = this.itemHandler.getSlots();
		IAEStack rv = null;

		for( int slot = 0; slot < slots && amount > 0; slot++ )
		{
			final ItemStack is = this.itemHandler.getStackInSlot( slot );
			if( !is.isEmpty() && ( filter.isEmpty() || is.equals(filter) ) )
			{
				IAEStack extracted = AEItemStack.fromItemStack( this.itemHandler.extractItem( slot, (int)amount, true ) );

				if( extracted.isEmpty() )
				{
					continue;
				}

				if( destination != null )
				{
					if( !destination.canInsert( extracted ) )
					{
						continue;
					}
				}

				if( rv == null || rv.isEmpty() )
				{
					// Use the first stack as a template for the result
					rv = extracted.copy();
					filter = extracted;
					amount -= extracted.getStackSize();
				}
				else
				{
					// Subsequent stacks will just increase the extracted size
					rv.incStackSize( extracted.getStackSize() );
					amount -= extracted.getStackSize();
				}
			}
		}

		return rv;
	}

	/**
	 * For fuzzy extract, we will only ever extract one slot, since we're afraid of merging two item stacks with
	 * different damage values.
	 */
	@Override
	public IAEStack removeSimilarItems( long amount, IAEStack filter, FuzzyMode fuzzyMode, IInventoryDestination destination )
	{
		int slots = this.itemHandler.getSlots();
		IAEStack extracted = null;

		for( int slot = 0; slot < slots &&  (extracted == null || extracted.isEmpty()); slot++ )
		{
			final IAEStack is = AEItemStack.fromItemStack( this.itemHandler.getStackInSlot( slot ) );
			if( is.isEmpty() || ( !filter.isEmpty() && !is.fuzzyComparison( filter, fuzzyMode ) ) )
			{
				continue;
			}

			if( destination != null )
			{
				IAEStack simulated = AEItemStack.fromItemStack( this.itemHandler.extractItem( slot, (int)amount, true ) );
				if( simulated.isEmpty() )
				{
					continue;
				}

				if( !destination.canInsert( simulated ) )
				{
					continue;
				}
			}

			// Attempt extracting it
			extracted = AEItemStack.fromItemStack( this.itemHandler.extractItem( slot, (int)amount, false ) );
		}

		return extracted;
	}

	@Override
	public IAEStack simulateSimilarRemove( long amount, IAEStack filter, FuzzyMode fuzzyMode, IInventoryDestination destination )
	{
		int slots = this.itemHandler.getSlots();
		IAEStack extracted = null;

		for( int slot = 0; slot < slots && (extracted == null || extracted.isEmpty()); slot++ )
		{
			final IAEStack is = AEItemStack.fromItemStack( this.itemHandler.getStackInSlot( slot ) );
			if( is.isEmpty() || ( !filter.isEmpty() && !is.fuzzyComparison( filter, fuzzyMode ) ) )
			{
				continue;
			}

			// Attempt extracting it
			extracted = AEItemStack.fromItemStack( this.itemHandler.extractItem( slot, (int)amount, true ) );

			if( !extracted.isEmpty() && destination != null )
			{
				if( !destination.canInsert( extracted ) )
				{
					extracted = null; // Keep on looking...
				}
			}
		}

		return extracted;
	}

	@Override
	public IAEStack addItems( IAEStack toBeAdded )
	{
		return this.addItems( toBeAdded, false );
	}

	@Override
	public IAEStack simulateAdd( IAEStack toBeSimulated )
	{
		return this.addItems( toBeSimulated, true );
	}

	protected IAEStack addItems( final IAEStack itemsToAdd, final boolean simulate )
	{
		if( itemsToAdd == null || itemsToAdd.isEmpty()  )
		{
			return null;
		}
		if( itemsToAdd.getChannel() != AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) )
			return itemsToAdd;

		IAEStack left = itemsToAdd.copy();

		for( int slot = 0; slot < this.itemHandler.getSlots(); slot++ )
		{
			left = AEItemStack.fromItemStack( this.itemHandler.insertItem( slot, (ItemStack)left.getStack(), simulate ) );

			if( left.isEmpty() )
			{
				return null;
			}
		}

		return left;
	}

	@Override
	public boolean containsItems()
	{
		int slots = this.itemHandler.getSlots();
		for( int slot = 0; slot < slots; slot++ )
		{
			if( !this.itemHandler.getStackInSlot( slot ).isEmpty() )
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<ISlot> iterator()
	{
		return new ItemHandlerIterator( this.itemHandler );
	}
}
