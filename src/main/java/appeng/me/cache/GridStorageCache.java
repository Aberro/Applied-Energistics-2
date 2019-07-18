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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import appeng.util.item.MixedList;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.GenericInterestManager;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.ItemWatcher;
import appeng.me.storage.NetworkInventoryHandler;


public class GridStorageCache implements IStorageGrid
{

	private final IGrid myGrid;
	private final HashSet<ICellProvider> activeCellProviders = new HashSet<>();
	private final HashSet<ICellProvider> inactiveCellProviders = new HashSet<>();
	private final SetMultimap<IAEStack, ItemWatcher> interests = HashMultimap.create();
	private final GenericInterestManager<ItemWatcher> interestManager = new GenericInterestManager<>( this.interests );
	private final HashMap<IGridNode, IStackWatcher> watchers = new HashMap<>();
	private NetworkInventoryHandler storageNetwork;
	private NetworkMonitor storageMonitor;

	public GridStorageCache( final IGrid g )
	{
		this.myGrid = g;
		this.storageNetwork = this.buildNetworkStorage();
		this.storageMonitor = new NetworkMonitor( this );
	}

	@Override
	public void onUpdateTick()
	{
		this.storageMonitor.onTick();
	}

	@Override
	public void removeNode( final IGridNode node, final IGridHost machine )
	{
		if( machine instanceof ICellContainer )
		{
			final ICellContainer cc = (ICellContainer) machine;
			final CellChangeTracker tracker = new CellChangeTracker();

			this.removeCellProvider( cc, tracker );
			this.inactiveCellProviders.remove( cc );
			this.getGrid().postEvent( new MENetworkCellArrayUpdate() );

			tracker.applyChanges();
		}

		if( machine instanceof IStackWatcherHost )
		{
			final IStackWatcher myWatcher = this.watchers.get( machine );

			if( myWatcher != null )
			{
				myWatcher.reset();
				this.watchers.remove( machine );
			}
		}
	}

	@Override
	public void addNode( final IGridNode node, final IGridHost machine )
	{
		if( machine instanceof ICellContainer )
		{
			final ICellContainer cc = (ICellContainer) machine;
			this.inactiveCellProviders.add( cc );

			this.getGrid().postEvent( new MENetworkCellArrayUpdate() );

			if( node.isActive() )
			{
				final CellChangeTracker tracker = new CellChangeTracker();

				this.addCellProvider( cc, tracker );
				tracker.applyChanges();
			}
		}

		if( machine instanceof IStackWatcherHost )
		{
			final IStackWatcherHost swh = (IStackWatcherHost) machine;
			final ItemWatcher iw = new ItemWatcher( this, swh );
			this.watchers.put( node, iw );
			swh.updateWatcher( iw );
		}
	}

	@Override
	public void onSplit( final IGridStorage storageB )
	{

	}

	@Override
	public void onJoin( final IGridStorage storageB )
	{

	}

	@Override
	public void populateGridStorage( final IGridStorage storage )
	{

	}

	public IMEInventoryHandler getInventoryHandler( )
	{
		return this.storageNetwork;
	}

	@Override
	public IMEMonitor getInventory( )
	{
		return this.storageMonitor;
	}

	private CellChangeTracker addCellProvider( final ICellProvider cc, final CellChangeTracker tracker )
	{
		if( this.inactiveCellProviders.contains( cc ) )
		{
			this.inactiveCellProviders.remove( cc );
			this.activeCellProviders.add( cc );

			final IActionSource actionSrc = cc instanceof IActionHost ? new MachineSource( (IActionHost) cc ) : new BaseActionSource();

			for( final IMEInventoryHandler h : cc.getCellArray( ) )
			{
				tracker.postChanges( 1, h, actionSrc );
			}
		}

		return tracker;
	}

	private CellChangeTracker removeCellProvider( final ICellProvider cc, final CellChangeTracker tracker )
	{
		if( this.activeCellProviders.contains( cc ) )
		{
			this.activeCellProviders.remove( cc );
			this.inactiveCellProviders.add( cc );

			final IActionSource actionSrc = cc instanceof IActionHost ? new MachineSource( (IActionHost) cc ) : new BaseActionSource();


			for( final IMEInventoryHandler h : cc.getCellArray( ) )
			{
				tracker.postChanges(  -1, h, actionSrc );
			}
		}

		return tracker;
	}

	@MENetworkEventSubscribe
	public void cellUpdate( final MENetworkCellArrayUpdate ev )
	{
		this.storageNetwork = null;

		final List<ICellProvider> ll = new ArrayList<ICellProvider>();
		ll.addAll( this.inactiveCellProviders );
		ll.addAll( this.activeCellProviders );

		final CellChangeTracker tracker = new CellChangeTracker();

		for( final ICellProvider cc : ll )
		{
			boolean active = true;

			if( cc instanceof IActionHost )
			{
				final IGridNode node = ( (IActionHost) cc ).getActionableNode();
				if( node != null && node.isActive() )
				{
					active = true;
				}
				else
				{
					active = false;
				}
			}

			if( active )
			{
				this.addCellProvider( cc, tracker );
			}
			else
			{
				this.removeCellProvider( cc, tracker );
			}
		}

		this.storageMonitor.forceUpdate();

		tracker.applyChanges();
	}

	private void postChangesToNetwork( final int upOrDown, final IItemList<IAEStack> availableItems, final IActionSource src )
	{
		this.storageMonitor.postChange( upOrDown > 0, (Iterable) availableItems, src );
	}

	private NetworkInventoryHandler buildNetworkStorage( )
	{
		final SecurityCache security = this.getGrid().getCache( ISecurityGrid.class );

		final NetworkInventoryHandler storageNetwork = new NetworkInventoryHandler( security );

		for( final ICellProvider cc : this.activeCellProviders )
		{
			for( final IMEInventoryHandler h : cc.getCellArray( ) )
			{
				storageNetwork.addNewStorage( h );
			}
		}

		return storageNetwork;
	}

	@Override
	public void postAlterationOfStoredItems( final Iterable<IAEStack> input, final IActionSource src )
	{
		this.storageMonitor.postChange( true, (Iterable) input, src );
	}

	@Override
	public void registerCellProvider( final ICellProvider provider )
	{
		this.inactiveCellProviders.add( provider );
		this.addCellProvider( provider, new CellChangeTracker() ).applyChanges();
	}

	@Override
	public void unregisterCellProvider( final ICellProvider provider )
	{
		this.removeCellProvider( provider, new CellChangeTracker() ).applyChanges();
		this.inactiveCellProviders.remove( provider );
	}

	public GenericInterestManager<ItemWatcher> getInterestManager()
	{
		return this.interestManager;
	}

	IGrid getGrid()
	{
		return this.myGrid;
	}

	private class CellChangeTrackerRecord<T extends IAEStack>
	{
		final int up_or_down;
		final IItemList<IAEStack> list;
		final IActionSource src;

		public CellChangeTrackerRecord( final int i, final IMEInventoryHandler h, final IActionSource actionSrc )
		{
			this.up_or_down = i;
			this.src = actionSrc;

			IItemList<IAEStack> list = new MixedList();
			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				h.getAvailableItems( channel, list );
			this.list = list;
		}

		public void applyChanges()
		{
			GridStorageCache.this.postChangesToNetwork( this.up_or_down, this.list, this.src );
		}
	}

	private class CellChangeTracker<T extends IAEStack>
	{

		final List<CellChangeTrackerRecord<T>> data = new ArrayList<>();

		public void postChanges( final int i, final IMEInventoryHandler h, final IActionSource actionSrc )
		{
			this.data.add( new CellChangeTrackerRecord<T>( i, h, actionSrc ) );
		}

		public void applyChanges()
		{
			for( final CellChangeTrackerRecord<T> rec : this.data )
			{
				rec.applyChanges();
			}
		}
	}
}
