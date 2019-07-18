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

package appeng.me.storage;


import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEStack;
import appeng.items.storage.ItemCreativeStorageCell;
import appeng.util.inv.ItemSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.contents.CellConfig;
import appeng.util.item.AEItemStack;


public class CreativeCellInventory implements IMEInventoryHandler
{

	private final IItemList<IAEStack> itemListCache = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList();

	protected CreativeCellInventory( final ItemStack o )
	{
		final CellConfig cc = new CellConfig( o );
		for( final ItemStack is : cc )
		{
			if( !is.isEmpty() )
			{
				final IAEItemStack i = AEItemStack.fromItemStack( is );
				i.setStackSize( Integer.MAX_VALUE );
				this.itemListCache.add( i );
			}
		}
	}

	public static ICellInventoryHandler getCell( final ItemStack o )
	{
		return new BasicCellInventoryHandler( new CreativeCellInventory( o ), AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ) );
	}

	public static boolean isCell(ItemStack is, IStorageChannel<IAEItemStack, ItemSlot, ItemStack> channel)
	{
		return !is.isEmpty() && is.getItem() instanceof ItemCreativeStorageCell;
	}

	@Override
	public IAEStack injectItems( final IAEStack input, final Actionable mode, final IActionSource src )
	{
		final IAEStack local = this.itemListCache.findPrecise( input );
		if( local == null )
		{
			return input;
		}

		return null;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		final IAEStack local = this.itemListCache.findPrecise( request );
		if( local == null )
		{
			return null;
		}

		return request.copy();
	}

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList out )
	{
		if(channel != this.getChannel())
			return out;
		for( final IAEStack ais : this.itemListCache )
		{
			out.add( ais );
		}
		return out;
	}

	public IStorageChannel getChannel()
	{
		return AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class );
	}

	@Override
	public AccessRestriction getAccess()
	{
		return AccessRestriction.READ_WRITE;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return this.itemListCache.findPrecise( input ) != null;
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		return this.itemListCache.findPrecise( input ) != null;
	}

	@Override
	public int getPriority()
	{
		return 0;
	}

	@Override
	public int getSlot()
	{
		return 0;
	}

	@Override
	public boolean validForPass( final int i )
	{
		return true;
	}
}
