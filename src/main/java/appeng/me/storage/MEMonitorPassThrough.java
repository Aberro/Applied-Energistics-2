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


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.inv.ItemListIgnoreCrafting;
import appeng.util.item.MixedList;


public class MEMonitorPassThrough extends MEPassThrough implements IMEMonitor, IMEMonitorHandlerReceiver
{

	private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
	private IActionSource changeSource;
	private IMEMonitor monitor;

	public MEMonitorPassThrough( final IMEInventory i )
	{
		super( i );
		if( i instanceof IMEMonitor )
		{
			this.monitor = (IMEMonitor) i;
		}
	}

	@Override
	public void setInternal( final IMEInventory i )
	{
		if( this.monitor != null )
		{
			this.monitor.removeListener( this );
		}

		this.monitor = null;
		final Map<IStorageChannel, IItemList<IAEStack>> before = new HashMap<>();
		if(this.getInternal() != null)
			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				before.put(channel, this.getInternal().getAvailableItems( channel, new ItemListIgnoreCrafting( channel.createList() ) ) );


		super.setInternal( i );
		if( i instanceof IMEMonitor )
		{
			this.monitor = (IMEMonitor) i;
		}

		final Map<IStorageChannel, IItemList<IAEStack>> after = new HashMap<>();
		if(this.getInternal() != null)
			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				after.put( channel, this.getInternal().getAvailableItems( channel, new ItemListIgnoreCrafting( channel.createList() ) ) );

		if( this.monitor != null )
		{
			this.monitor.addListener( this, this.monitor );
		}

		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
		{
			IItemList b = before.get(channel);
			IItemList a = after.get(channel);
			Platform.postListChanges( b, a, this, this.getChangeSource() );
		}

	}

	@Override
	public IItemList<IAEStack> getAvailableItems(IStorageChannel channel, final IItemList<IAEStack> out )
	{
		super.getAvailableItems( channel, new ItemListIgnoreCrafting( out ) );
		return out;
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
	public IItemList<IAEStack> getStorageList(IStorageChannel channel)
	{
		if( this.monitor == null )
		{
			final IItemList<IAEStack> out = channel.createList();
			this.getInternal().getAvailableItems( channel, new ItemListIgnoreCrafting( out ) );

			return out;
		}
		return this.monitor.getStorageList(channel);
	}

	@Override
	public boolean isValid( final Object verificationToken )
	{
		return verificationToken == this.monitor;
	}

	@Override
	public void postChange( final IBaseMonitor monitor, final Iterable<IAEStack> change, final IActionSource source )
	{
		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet().iterator();
		while( i.hasNext() )
		{
			final Entry<IMEMonitorHandlerReceiver, Object> e = i.next();
			final IMEMonitorHandlerReceiver receiver = e.getKey();
			if( receiver.isValid( e.getValue() ) )
			{
				receiver.postChange( this, change, source );
			}
			else
			{
				i.remove();
			}
		}
	}

	@Override
	public void onListUpdate()
	{
		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet().iterator();
		while( i.hasNext() )
		{
			final Entry<IMEMonitorHandlerReceiver, Object> e = i.next();
			final IMEMonitorHandlerReceiver receiver = e.getKey();
			if( receiver.isValid( e.getValue() ) )
			{
				receiver.onListUpdate();
			}
			else
			{
				i.remove();
			}
		}
	}

	private IActionSource getChangeSource()
	{
		return this.changeSource;
	}

	public void setChangeSource( final IActionSource changeSource )
	{
		this.changeSource = changeSource;
	}
}
