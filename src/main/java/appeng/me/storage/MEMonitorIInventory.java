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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.ItemSlot;


public class MEMonitorIInventory implements IMEMonitor, ITickingMonitor
{

	private final InventoryAdaptor adaptor;
	private final IItemList<IAEStack> list = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList();
	private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
	private final NavigableMap<Integer, CachedItemStack> memory;
	private IActionSource mySource;
	private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

	public MEMonitorIInventory( final InventoryAdaptor adaptor )
	{
		this.adaptor = adaptor;
		this.memory = new ConcurrentSkipListMap<>();
	}

	@Override
	public void addListener( final IMEMonitorHandlerReceiver l, final Object verificationToken )
	{
		this.listeners.put( l, verificationToken );
	}

	@Override
	public void removeListener( final IMEMonitorHandlerReceiver l )
	{
		this.listeners.remove( l );
	}

	@Override
	public IAEStack injectItems(final IAEStack input, final Actionable type, final IActionSource src )
	{
		IAEStack out = null;
		IAEItemStack inputItem = (IAEItemStack)input;

		if( type == Actionable.SIMULATE )
		{
			out = this.adaptor.simulateAdd( inputItem );
		}
		else
		{
			out = this.adaptor.addItems( inputItem );
		}

		if( type == Actionable.MODULATE )
		{
			this.onTick();
		}

		if( out.isEmpty() )
		{
			return null;
		}

		// better then doing construction from scratch :3
		final IAEItemStack o = inputItem.copy();
		o.setStackSize( out.getStackSize() );
		return o;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable type, final IActionSource src )
	{
		IAEStack out = null;
		IAEItemStack requestItem = (IAEItemStack)request;

		if( type == Actionable.SIMULATE )
		{
			out = this.adaptor.simulateRemove( (int) requestItem.getStackSize(), requestItem, null );
		}
		else
		{
			out = this.adaptor.removeItems( (int) requestItem.getStackSize(), requestItem, null );
		}

		if( out.isEmpty() )
		{
			return null;
		}

		// better then doing construction from scratch :3
		final IAEItemStack o = requestItem.copy();
		o.setStackSize( out.getStackSize() );

		if( type == Actionable.MODULATE )
		{
			this.onTick();
		}

		return o;
	}

	@Override
	public TickRateModulation onTick()
	{

		final List<IAEStack> changes = new ArrayList<>();

		this.list.resetStatus();
		int high = 0;
		boolean changed = false;
		for( final ISlot is : this.adaptor )
		{
			final CachedItemStack old = this.memory.get( is.getSlot() );
			high = Math.max( high, is.getSlot() );

			final IAEStack newIS = !is.isExtractable() && this.getMode() == StorageFilter.EXTRACTABLE_ONLY ? null : is.getAEStack();
			final IAEStack oldIS = old.stack;

			if( ( ( newIS == null ) != ( oldIS == null ) ) || (newIS != null && !newIS.equals( oldIS ) ) )
			{
				final CachedItemStack cis = new CachedItemStack( is.getAEStack() );
				this.memory.put( is.getSlot(), cis );

				if( old != null && old.stack != null )
				{
					old.stack.setStackSize( -old.stack.getStackSize() );
					changes.add( old.stack );
				}

				if( cis.stack != null )
				{
					changes.add( cis.stack );
					this.list.add( cis.stack );
				}

				changed = true;
			}
			else
			{
				final long newSize = ( newIS.isEmpty() ? 0 : newIS.getStackSize() );
				final long diff = newSize - ( oldIS.isEmpty() ? 0 : oldIS.getStackSize() );

				final IAEStack stack = ( old == null || old.stack == null ? AEApi.instance()
						.storage()
						.getStorageChannel( IItemStorageChannel.class )
						.createStack( newIS ) : old.stack.copy() );
				if( stack != null )
				{
					stack.setStackSize( newSize );
					this.list.add( stack );
				}

				if( diff != 0 && stack != null )
				{
					final CachedItemStack cis = new CachedItemStack( is.getAEStack() );
					this.memory.put( is.getSlot(), cis );

					final IAEStack a = stack.copy();
					a.setStackSize( diff );
					changes.add( a );
					changed = true;
				}
			}
		}

		// detect dropped items; should fix non IISided Inventory Changes.
		final NavigableMap<Integer, CachedItemStack> end = this.memory.tailMap( high, false );
		if( !end.isEmpty() )
		{
			for( final CachedItemStack cis : end.values() )
			{
				if( cis != null && cis.stack != null )
				{
					final IAEStack a = cis.stack.copy();
					a.setStackSize( -a.getStackSize() );
					changes.add( a );
					changed = true;
				}
			}
			end.clear();
		}

		if( !changes.isEmpty() )
		{
			this.postDifference( changes );
		}

		return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
	}

	private void postDifference( final Iterable<IAEStack> a )
	{
		// AELog.info( a.getItemStack().getUnlocalizedName() + " @ " + a.getStackSize() );
		if( a != null )
		{
			final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet().iterator();
			while( i.hasNext() )
			{
				final Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
				final IMEMonitorHandlerReceiver key = l.getKey();
				if( key.isValid( l.getValue() ) )
				{
					key.postChange( this, a, this.getActionSource() );
				}
				else
				{
					i.remove();
				}
			}
		}
	}

	@Override
	public AccessRestriction getAccess()
	{
		return AccessRestriction.READ_WRITE;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return false;
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		return true;
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

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList<IAEStack> out )
	{
		for( final CachedItemStack is : this.memory.values() )
		{
			out.addStorage( is.stack );
		}

		return out;
	}

	@Override
	public IItemList<IAEStack> getStorageList( IStorageChannel channel )
	{
		return this.list;
	}

	private StorageFilter getMode()
	{
		return this.mode;
	}

	public void setMode( final StorageFilter mode )
	{
		this.mode = mode;
	}

	private IActionSource getActionSource()
	{
		return this.mySource;
	}

	@Override
	public void setActionSource( final IActionSource mySource )
	{
		this.mySource = mySource;
	}

	private static class CachedItemStack
	{

		private final IAEStack stack;

		public CachedItemStack( final IAEStack stack )
		{
			if( stack == null || stack.isEmpty() )
				this.stack = null;
			else
				this.stack = stack.copy();
		}
	}
}
