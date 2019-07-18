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

package appeng.me.cache;


import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;


public class NetworkMonitor implements IMEMonitor
{
	@Nonnull
	private static final Deque<NetworkMonitor> GLOBAL_DEPTH = Queues.newArrayDeque();

	@Nonnull
	private final GridStorageCache myGridCache;
	@Nonnull
	private final Map<IStorageChannel, IItemList<IAEStack>> cachedList;
	@Nonnull
	private final Map<IMEMonitorHandlerReceiver, Object> listeners;

	private boolean sendEvent = false;
	private boolean hasChanged = false;
	@Nonnegative
	private int localDepthSemaphore = 0;

	public NetworkMonitor( final GridStorageCache cache )
	{
		this.myGridCache = cache;
		this.cachedList = new HashMap<>();
		this.listeners = new HashMap<>();
	}

	@Override
	public void addListener( final IMEMonitorHandlerReceiver l, final Object verificationToken )
	{
		this.listeners.put( l, verificationToken );
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		return this.getHandler().canAccept( input );
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		if( mode == Actionable.SIMULATE )
		{
			return this.getHandler().extractItems( request, mode, src );
		}

		this.localDepthSemaphore++;
		final IAEStack leftover = this.getHandler().extractItems( request, mode, src );
		this.localDepthSemaphore--;

		if( this.localDepthSemaphore == 0 )
		{
			this.monitorDifference( request.copy(), leftover, true, src );
		}

		return leftover;
	}

	@Override
	public AccessRestriction getAccess()
	{
		return this.getHandler().getAccess();
	}

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList<IAEStack> out )
	{
		return this.getHandler().getAvailableItems( channel, out );
	}

	@Override
	public int getPriority()
	{
		return this.getHandler().getPriority();
	}

	@Override
	public int getSlot()
	{
		return this.getHandler().getSlot();
	}

	@Nonnull
	@Override
	public IItemList<IAEStack> getStorageList(IStorageChannel channel)
	{
		if( this.hasChanged )
		{
			this.hasChanged = false;
			IItemList<IAEStack> list = this.cachedList.get(channel);
			if(list != null)
				list.resetStatus();
			return this.getAvailableItems( channel, list );
		}

		return this.cachedList.get(channel);
	}

	@Override
	public IAEStack injectItems( final IAEStack input, final Actionable mode, final IActionSource src )
	{
		if( mode == Actionable.SIMULATE )
		{
			return this.getHandler().injectItems( input, mode, src );
		}

		this.localDepthSemaphore++;
		final IAEStack leftover = this.getHandler().injectItems( input, mode, src );
		this.localDepthSemaphore--;

		if( this.localDepthSemaphore == 0 )
		{
			this.monitorDifference( input.copy(), leftover, false, src );
		}

		return leftover;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return this.getHandler().isPrioritized( input );
	}

	@Override
	public void removeListener( final IMEMonitorHandlerReceiver l )
	{
		this.listeners.remove( l );
	}

	@Override
	public boolean validForPass( final int i )
	{
		return this.getHandler().validForPass( i );
	}

	@Nullable
	private IMEInventoryHandler getHandler()
	{
		return this.myGridCache.getInventoryHandler( );
	}

	private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners()
	{
		return this.listeners.entrySet().iterator();
	}

	private IAEStack monitorDifference( final IAEStack original, final IAEStack leftOvers, final boolean extraction, final IActionSource src )
	{
		final IAEStack diff = original.copy();

		if( extraction )
		{
			diff.setStackSize( leftOvers == null ? 0 : -leftOvers.getStackSize() );
		}
		else if( leftOvers != null )
		{
			diff.decStackSize( leftOvers.getStackSize() );
		}

		if( diff.getStackSize() != 0 )
		{
			this.postChangesToListeners( ImmutableList.of( diff ), src );
		}

		return leftOvers;
	}

	private void notifyListenersOfChange( final Iterable<IAEStack> diff, final IActionSource src )
	{
		this.hasChanged = true;
		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

		while( i.hasNext() )
		{
			final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
			final IMEMonitorHandlerReceiver receiver = o.getKey();
			if( receiver.isValid( o.getValue() ) )
			{
				receiver.postChange( this, diff, src );
			}
			else
			{
				i.remove();
			}
		}
	}

	private void postChangesToListeners( final Iterable<IAEStack> changes, final IActionSource src )
	{
		this.postChange( true, changes, src );
	}

	protected void postChange( final boolean add, final Iterable<IAEStack> changes, final IActionSource src )
	{
		if( this.localDepthSemaphore > 0 || GLOBAL_DEPTH.contains( this ) )
		{
			return;
		}

		GLOBAL_DEPTH.push( this );
		this.localDepthSemaphore++;

		this.sendEvent = true;

		this.notifyListenersOfChange( changes, src );

		for( final IAEStack changedItem : changes )
		{
			IAEStack difference = changedItem;

			if( !add && changedItem != null )
			{
				difference = changedItem.copy();
				difference.setStackSize( -changedItem.getStackSize() );
			}

			if( this.myGridCache.getInterestManager().containsKey( changedItem ) )
			{
				final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get( changedItem );

				if( !list.isEmpty() )
				{
					IAEStack fullStack = this.getStorageList(changedItem.getChannel()).findPrecise( changedItem );

					if( fullStack == null )
					{
						fullStack = changedItem.copy();
						fullStack.setStackSize( 0 );
					}

					this.myGridCache.getInterestManager().enableTransactions();

					for( final ItemWatcher iw : list )
					{
						iw.getHost().onStackChange( this.getStorageList(changedItem.getChannel()), fullStack, difference, src );
					}

					this.myGridCache.getInterestManager().disableTransactions();
				}
			}
		}

		final NetworkMonitor last = GLOBAL_DEPTH.pop();
		this.localDepthSemaphore--;

		if( last != this )
		{
			throw new IllegalStateException( "Invalid Access to Networked Storage API detected." );
		}
	}

	void forceUpdate()
	{
		this.hasChanged = true;

		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
		while( i.hasNext() )
		{
			final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
			final IMEMonitorHandlerReceiver receiver = o.getKey();

			if( receiver.isValid( o.getValue() ) )
			{
				receiver.onListUpdate();
			}
			else
			{
				i.remove();
			}
		}
	}

	void onTick()
	{
		if( this.sendEvent )
		{
			this.sendEvent = false;
			this.myGridCache.getGrid().postEvent( new MENetworkStorageEvent( this ) );
		}
	}
}
