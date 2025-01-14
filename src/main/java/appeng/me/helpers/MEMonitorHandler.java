/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.me.helpers;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import appeng.api.AEApi;
import appeng.api.implementations.items.IStorageCell;
import com.google.common.collect.ImmutableList;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;


/**
 * Common implementation of a simple class that monitors injection/extraction of a inventory to send events to a list of
 * listeners.
 *
 * TODO: Needs to be redesigned to solve performance issues.
 */
public class MEMonitorHandler implements IMEMonitor
{

	private final IMEInventoryHandler internalHandler;
	private final Map<IStorageChannel, IItemList> cachedList;
	private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();

	protected boolean hasChanged = true;

	public MEMonitorHandler( final IMEInventoryHandler t )
	{
		this.internalHandler = t;
		this.cachedList = new HashMap<>();
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			this.cachedList.put(channel, channel.createList());
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
	public IAEStack injectItems( final IAEStack input, final Actionable mode, final IActionSource src )
	{
		if( mode == Actionable.SIMULATE )
		{
			return this.getHandler().injectItems( input, mode, src );
		}
		return this.monitorDifference( input.copy(), this.getHandler().injectItems( input, mode, src ), false, src );
	}

	protected IMEInventoryHandler getHandler()
	{
		return this.internalHandler;
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

	protected void postChangesToListeners( final Iterable<IAEStack> changes, final IActionSource src )
	{
		this.notifyListenersOfChange( changes, src );
	}

	protected void notifyListenersOfChange( final Iterable<IAEStack> diff, final IActionSource src )
	{
		this.hasChanged = true;// need to update the cache.
		final Iterator<Map.Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
		while( i.hasNext() )
		{
			final Map.Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
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

	protected Iterator<Map.Entry<IMEMonitorHandlerReceiver, Object>> getListeners()
	{
		return this.listeners.entrySet().iterator();
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		if( mode == Actionable.SIMULATE )
		{
			return this.getHandler().extractItems( request, mode, src );
		}
		return this.monitorDifference( request.copy(), this.getHandler().extractItems( request, mode, src ), true, src );
	}

	@Override
	public AccessRestriction getAccess()
	{
		return this.getHandler().getAccess();
	}

	@Override
	public IItemList<IAEStack> getStorageList(IStorageChannel channel)
	{
		if( this.hasChanged )
		{
			this.hasChanged = false;
			IItemList list = this.cachedList.get(channel);
			list.resetStatus();
			return this.getAvailableItems( channel, list );
		}

		return this.cachedList.get(channel);
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return this.getHandler().isPrioritized( input );
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		return this.getHandler().canAccept( input );
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

	@Override
	public boolean validForPass( final int i )
	{
		return this.getHandler().validForPass( i );
	}

}
