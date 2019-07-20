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

package appeng.tile.misc;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import appeng.api.storage.data.IAEStack;
import appeng.api.util.ISlot;
import appeng.util.inv.ItemSlot;
import appeng.util.item.MixedList;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.BaseActionSource;
import appeng.me.storage.ITickingMonitor;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;


class CondenserItemInventory implements IMEMonitor, ITickingMonitor
{
	private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
	private final TileCondenser target;
	private boolean hasChanged = true;
	private final IItemList<IAEStack> cachedList = new MixedList();
	private IActionSource actionSource = new BaseActionSource();
	private IItemList<IAEStack> changeSet = new MixedList();

	CondenserItemInventory( final TileCondenser te )
	{
		this.target = te;
	}

	@Override
	public IAEStack injectItems(final IAEStack input, final Actionable mode, final IActionSource src )
	{
		if( mode == Actionable.MODULATE && input != null )
		{
			this.target.addPower( input.getStackSize() );
		}
		return null;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		if(request.getChannel() != this.getChannel())
			return null;
		IAEItemStack item = (IAEItemStack)request;
		AEItemStack ret = null;
		ItemStack slotItem = this.target.getOutputSlot().getStackInSlot( 0 );
		if( !slotItem.isEmpty() && item .isSameType( slotItem ) )
		{
			int count = (int) Math.min( item .getStackSize(), Integer.MAX_VALUE );
			ret = AEItemStack.fromItemStack( this.target.getOutputSlot().extractItem( 0, count, mode == Actionable.SIMULATE ) );
		}
		return ret;
	}

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList<IAEStack> out )
	{
		if( !this.target.getOutputSlot().getStackInSlot( 0 ).isEmpty() )
		{
			out.add( AEItemStack.fromItemStack( this.target.getOutputSlot().getStackInSlot( 0 ) ) );
		}
		return out;
	}

	@Override
	public IItemList<IAEStack> getStorageList(IStorageChannel channel)
	{
		if( this.hasChanged )
		{
			this.hasChanged = false;
			this.cachedList.resetStatus();
			return this.getAvailableItems( this.getChannel(), this.cachedList );
		}
		return this.cachedList;
	}

	public IStorageChannel<IAEItemStack, ISlot<ItemStack, IAEItemStack>, ItemStack> getChannel()
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
		return i == 2;
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

	public void updateOutput( ItemStack added, ItemStack removed )
	{
		this.hasChanged = true;
		if( !added.isEmpty() )
		{
			this.changeSet.add( AEItemStack.fromItemStack( added ) );
		}
		if( !removed.isEmpty() )
		{
			this.changeSet.add( AEItemStack.fromItemStack( removed ).setStackSize( -removed.getCount() ) );
		}
	}

	@Override
	public TickRateModulation onTick()
	{
		final IItemList<IAEStack> currentChanges = this.changeSet;

		if( currentChanges.isEmpty() )
		{
			return TickRateModulation.IDLE;
		}

		this.changeSet = new MixedList();
		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet().iterator();
		while( i.hasNext() )
		{
			final Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
			final IMEMonitorHandlerReceiver key = l.getKey();
			if( key.isValid( l.getValue() ) )
			{
				key.postChange( this, currentChanges, this.actionSource );
			}
			else
			{
				i.remove();
			}
		}

		return TickRateModulation.URGENT;
	}

	@Override
	public void setActionSource( IActionSource actionSource )
	{
		this.actionSource = actionSource;
	}
}
