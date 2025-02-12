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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.CraftingWatcher;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.GenericInterestManager;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;


public class CraftingGridCache implements ICraftingGrid, ICraftingProviderHelper, ICellProvider, IMEInventoryHandler
{

	private static final ExecutorService CRAFTING_POOL;
	private static final Comparator<ICraftingPatternDetails> COMPARATOR = ( firstDetail, nextDetail ) -> nextDetail.getPriority() - firstDetail.getPriority();

	static
	{
		final ThreadFactory factory = ar -> new Thread( ar, "AE Crafting Calculator" );

		CRAFTING_POOL = Executors.newCachedThreadPool( factory );
	}

	private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
	private final Set<ICraftingProvider> craftingProviders = new HashSet<>();
	private final Map<IGridNode, ICraftingWatcher> craftingWatchers = new HashMap<>();
	private final IGrid grid;
	private final Map<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods = new HashMap<>();
	private final Map<IStorageChannel, Map<IAEStack, ImmutableList<ICraftingPatternDetails>>> craftableItems = new HashMap<>();
	private final Map<IStorageChannel, Set<IAEStack>> emitableItems = new HashMap<>();
	private final Map<String, CraftingLinkNexus> craftingLinks = new HashMap<>();
	private final Multimap<IAEStack, CraftingWatcher> interests = HashMultimap.create();
	private final GenericInterestManager<CraftingWatcher> interestManager = new GenericInterestManager<>( this.interests );
	private IStorageGrid storageGrid;
	private IEnergyGrid energyGrid;
	private boolean updateList = false;

	public CraftingGridCache( final IGrid grid )
	{
		this.grid = grid;
	}

	@MENetworkEventSubscribe
	public void afterCacheConstruction( final MENetworkPostCacheConstruction cacheConstruction )
	{
		this.storageGrid = this.grid.getCache( IStorageGrid.class );
		this.energyGrid = this.grid.getCache( IEnergyGrid.class );

		this.storageGrid.registerCellProvider( this );
	}

	@Override
	public void onUpdateTick()
	{
		if( this.updateList )
		{
			this.updateList = false;
			this.updateCPUClusters();
		}

		final Iterator<CraftingLinkNexus> craftingLinkIterator = this.craftingLinks.values().iterator();
		while( craftingLinkIterator.hasNext() )
		{
			if( craftingLinkIterator.next().isDead( this.grid, this ) )
			{
				craftingLinkIterator.remove();
			}
		}

		for( final CraftingCPUCluster cpu : this.craftingCPUClusters )
		{
			cpu.updateCraftingLogic( this.grid, this.energyGrid, this );
		}
	}

	@Override
	public void removeNode( final IGridNode gridNode, final IGridHost machine )
	{
		if( machine instanceof ICraftingWatcherHost )
		{
			final ICraftingWatcher craftingWatcher = this.craftingWatchers.get( machine );
			if( craftingWatcher != null )
			{
				craftingWatcher.reset();
				this.craftingWatchers.remove( machine );
			}
		}

		if( machine instanceof ICraftingRequester )
		{
			for( final CraftingLinkNexus link : this.craftingLinks.values() )
			{
				if( link.isMachine( machine ) )
				{
					link.removeNode();
				}
			}
		}

		if( machine instanceof TileCraftingTile )
		{
			this.updateList = true;
		}

		if( machine instanceof ICraftingProvider )
		{
			this.craftingProviders.remove( machine );
			this.updatePatterns();
		}
	}

	@Override
	public void addNode( final IGridNode gridNode, final IGridHost machine )
	{
		if( machine instanceof ICraftingWatcherHost )
		{
			final ICraftingWatcherHost watcherHost = (ICraftingWatcherHost) machine;
			final CraftingWatcher watcher = new CraftingWatcher( this, watcherHost );
			this.craftingWatchers.put( gridNode, watcher );
			watcherHost.updateWatcher( watcher );
		}

		if( machine instanceof ICraftingRequester )
		{
			for( final ICraftingLink link : ( (ICraftingRequester) machine ).getRequestedJobs() )
			{
				if( link instanceof CraftingLink )
				{
					this.addLink( (CraftingLink) link );
				}
			}
		}

		if( machine instanceof TileCraftingTile )
		{
			this.updateList = true;
		}

		if( machine instanceof ICraftingProvider )
		{
			this.craftingProviders.add( (ICraftingProvider) machine );
			this.updatePatterns();
		}
	}

	@Override
	public void onSplit( final IGridStorage destinationStorage )
	{ // nothing!
	}

	@Override
	public void onJoin( final IGridStorage sourceStorage )
	{
		// nothing!
	}

	@Override
	public void populateGridStorage( final IGridStorage destinationStorage )
	{
		// nothing!
	}

	private void updatePatterns()
	{
		final Map<IStorageChannel, Map<IAEStack, ImmutableList<ICraftingPatternDetails>>> oldItems = this.craftableItems;

		// erase list.
		this.craftingMethods.clear();
		this.craftableItems.clear();
		this.emitableItems.clear();
		for( Entry<IStorageChannel, Map<IAEStack, ImmutableList<ICraftingPatternDetails>>> channel : oldItems.entrySet()) {
			// update the stuff that was in the list...
			this.storageGrid.postAlterationOfStoredItems(channel.getValue().keySet(), new BaseActionSource());
		}

		// re-create list..
		for( final ICraftingProvider provider : this.craftingProviders )
		{
			provider.provideCrafting( this );
		}

		final Map<IAEStack, Set<ICraftingPatternDetails>> tmpCraft = new HashMap<>();

		// new craftables!
		for( final ICraftingPatternDetails details : this.craftingMethods.keySet() )
		{
			for( IAEStack out : details.getAllCondensedOutputs() )
			{
				out = out.copy();
				out.reset();
				out.setCraftable( true );

				Set<ICraftingPatternDetails> methods = tmpCraft.get( out );

				if( methods == null )
				{
					tmpCraft.put( out, methods = new TreeSet<>( COMPARATOR ) );
				}

				methods.add( details );
			}
		}

		// make them immutable
		for( final Entry<IAEStack, Set<ICraftingPatternDetails>> e : tmpCraft.entrySet() )
		{
			IStorageChannel channel = e.getKey().getChannel();
			Map<IAEStack, ImmutableList<ICraftingPatternDetails>> map = this.craftableItems.get(channel);
			if(map == null)
				this.craftableItems.put(channel, map = new HashMap<>());
			map.put( e.getKey(), ImmutableList.copyOf( e.getValue() ) );
		}

		for( Entry<IStorageChannel, Map<IAEStack, ImmutableList<ICraftingPatternDetails>>> channel : this.craftableItems.entrySet()) {
			// update the stuff that was in the list...
			this.storageGrid.postAlterationOfStoredItems(channel.getValue().keySet(), new BaseActionSource());
		}
	}

	private void updateCPUClusters()
	{
		this.craftingCPUClusters.clear();

		for( final IGridNode cst : this.grid.getMachines( TileCraftingStorageTile.class ) )
		{
			final TileCraftingStorageTile tile = (TileCraftingStorageTile) cst.getMachine();
			final CraftingCPUCluster cluster = (CraftingCPUCluster) tile.getCluster();
			if( cluster != null )
			{
				this.craftingCPUClusters.add( cluster );

				if( cluster.getLastCraftingLink() != null )
				{
					this.addLink( (CraftingLink) cluster.getLastCraftingLink() );
				}
			}
		}

	}

	public void addLink( final CraftingLink link )
	{
		if( link.isStandalone() )
		{
			return;
		}

		CraftingLinkNexus nexus = this.craftingLinks.get( link.getCraftingID() );
		if( nexus == null )
		{
			this.craftingLinks.put( link.getCraftingID(), nexus = new CraftingLinkNexus( link.getCraftingID() ) );
		}

		link.setNexus( nexus );
	}

	@MENetworkEventSubscribe
	public void updateCPUClusters( final MENetworkCraftingCpuChange c )
	{
		this.updateList = true;
	}

	@MENetworkEventSubscribe
	public void updateCPUClusters( final MENetworkCraftingPatternChange c )
	{
		this.updatePatterns();
	}

	@Override
	public void addCraftingOption( final ICraftingMedium medium, final ICraftingPatternDetails api )
	{
		List<ICraftingMedium> details = this.craftingMethods.get( api );
		if( details == null )
		{
			details = new ArrayList<>();
			details.add( medium );
			this.craftingMethods.put( api, details );
		}
		else
		{
			details.add( medium );
		}
	}

	@Override
	public void setEmitable( IStorageChannel channel, final IAEStack someItem )
	{
		Set<IAEStack> set = this.emitableItems.get(channel);
		if(set == null)
			this.emitableItems.put(channel, set = new HashSet<>());
		set.add( someItem.copy() );
	}

	@Override
	public List<IMEInventoryHandler> getCellArray( )
	{
		List<IMEInventoryHandler> out = new ArrayList<>(1);
		out.add(this);
		return out;
	}

	@Override
	public List<IMEInventoryHandler> getCellArray( final IStorageChannel channel )
	{
		return getCellArray();
	}

	@Override
	public int getPriority()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public AccessRestriction getAccess()
	{
		return AccessRestriction.WRITE;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return true;
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		for( final CraftingCPUCluster cpu : this.craftingCPUClusters )
		{
			if( cpu.canAccept( input ) )
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public int getSlot()
	{
		return 0;
	}

	@Override
	public boolean validForPass( final int i )
	{
		return i == 1;
	}

	@Override
	public IAEStack injectItems( IAEStack input, final Actionable type, final IActionSource src )
	{
		for( final CraftingCPUCluster cpu : this.craftingCPUClusters )
		{
			input = cpu.injectItems( input, type, src );
		}

		return input;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		return null;
	}

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList<IAEStack> out )
	{
		// add craftable items!
		Map<IAEStack, ImmutableList<ICraftingPatternDetails>> map = this.craftableItems.get(channel);
		if(map != null)
			for( final IAEStack stack : map.keySet() )
			{
				out.addCrafting( stack );
			}

		Set<IAEStack> set = this.emitableItems.get(channel);
		if(set != null)
			for( final IAEStack st : set )
			{
				out.addCrafting( st );
			}
		return out;
	}

	@Override
	public ImmutableCollection<ICraftingPatternDetails> getCraftingFor( final IAEStack whatToCraft, final ICraftingPatternDetails details, final int slotIndex, final World world )
	{
		ImmutableList<ICraftingPatternDetails> res = null;
		Map<IAEStack, ImmutableList<ICraftingPatternDetails>> map =this.craftableItems.get(whatToCraft.getChannel());
		if(map != null)
			res = map.get( whatToCraft );

		if( res == null )
		{
			if( details != null && details.isCraftable() )
			{
				for( IStorageChannel channel : this.craftableItems.keySet())
					for( final IAEStack ais : this.craftableItems.get(channel).keySet() )
					{
						if( ais.equals(whatToCraft) )
						{
							// TODO: check if OK
							// TODO: this is slightly hacky, but fine as long as we only deal with itemstacks
							if( details.isValidItemForSlot( slotIndex, ais.asItemStackRepresentation(), world ) )
							{
								return this.craftableItems.get(channel).get( ais );
							}
						}
					}
			}

			return ImmutableSet.of();
		}

		return res;
	}

	@Override
	public Future<ICraftingJob> beginCraftingJob( final World world, final IGrid grid, final IActionSource actionSrc, final IAEStack slotItem, final ICraftingCallback cb )
	{
		if( world == null || grid == null || actionSrc == null || slotItem == null )
		{
			throw new IllegalArgumentException( "Invalid Crafting Job Request" );
		}

		final CraftingJob job = new CraftingJob( world, grid, actionSrc, slotItem, cb );

		return CRAFTING_POOL.submit( job, (ICraftingJob) job );
	}

	@Override
	public ICraftingLink submitJob( final ICraftingJob job, final ICraftingRequester requestingMachine, final ICraftingCPU target, final boolean prioritizePower, final IActionSource src )
	{
		if( job.isSimulation() )
		{
			return null;
		}

		CraftingCPUCluster cpuCluster = null;

		if( target instanceof CraftingCPUCluster )
		{
			cpuCluster = (CraftingCPUCluster) target;
		}

		if( target == null )
		{
			final List<CraftingCPUCluster> validCpusClusters = new ArrayList<>();
			for( final CraftingCPUCluster cpu : this.craftingCPUClusters )
			{
				if( cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.getByteTotal() )
				{
					validCpusClusters.add( cpu );
				}
			}

			Collections.sort( validCpusClusters, ( firstCluster, nextCluster ) ->
			{
				if( prioritizePower )
				{
					final int comparison1 = Long.compare( nextCluster.getCoProcessors(), firstCluster.getCoProcessors() );
					if( comparison1 != 0 )
					{
						return comparison1;
					}
					return Long.compare( nextCluster.getAvailableStorage(), firstCluster.getAvailableStorage() );
				}

				final int comparison2 = Long.compare( firstCluster.getCoProcessors(), nextCluster.getCoProcessors() );
				if( comparison2 != 0 )
				{
					return comparison2;
				}
				return Long.compare( firstCluster.getAvailableStorage(), nextCluster.getAvailableStorage() );
			} );

			if( !validCpusClusters.isEmpty() )
			{
				cpuCluster = validCpusClusters.get( 0 );
			}
		}

		if( cpuCluster != null )
		{
			return cpuCluster.submitJob( this.grid, job, src, requestingMachine );
		}

		return null;
	}

	@Override
	public ImmutableSet<ICraftingCPU> getCpus()
	{
		return ImmutableSet.copyOf( new ActiveCpuIterator( this.craftingCPUClusters ) );
	}

	@Override
	public boolean canEmitFor( final IAEStack someItem )
	{
		IStorageChannel channel = someItem.getChannel();
		Set<IAEStack> set = this.emitableItems.get(channel);
		if(set == null)
			return false;
		return set.contains( someItem );
	}

	@Override
	public boolean isRequesting( final IAEStack what )
	{
		return this.requesting( what ) > 0;
	}

	@Override
	public long requesting( IAEStack what )
	{
		long requested = 0;

		for( final CraftingCPUCluster cluster : this.craftingCPUClusters )
		{
			final IAEStack stack = cluster.making( what );
			requested += stack != null ? stack.getStackSize() : 0;
		}

		return requested;
	}

	public List<ICraftingMedium> getMediums( final ICraftingPatternDetails key )
	{
		List<ICraftingMedium> mediums = this.craftingMethods.get( key );

		if( mediums == null )
		{
			mediums = ImmutableList.of();
		}

		return mediums;
	}

	public boolean hasCpu( final ICraftingCPU cpu )
	{
		return this.craftingCPUClusters.contains( cpu );
	}

	public GenericInterestManager<CraftingWatcher> getInterestManager()
	{
		return this.interestManager;
	}

	private static class ActiveCpuIterator implements Iterator<ICraftingCPU>
	{

		private final Iterator<CraftingCPUCluster> iterator;
		private CraftingCPUCluster cpuCluster;

		public ActiveCpuIterator( final Collection<CraftingCPUCluster> o )
		{
			this.iterator = o.iterator();
			this.cpuCluster = null;
		}

		@Override
		public boolean hasNext()
		{
			this.findNext();

			return this.cpuCluster != null;
		}

		private void findNext()
		{
			while( this.iterator.hasNext() && this.cpuCluster == null )
			{
				this.cpuCluster = this.iterator.next();
				if( !this.cpuCluster.isActive() || this.cpuCluster.isDestroyed() )
				{
					this.cpuCluster = null;
				}
			}
		}

		@Override
		public ICraftingCPU next()
		{
			final ICraftingCPU o = this.cpuCluster;
			this.cpuCluster = null;

			return o;
		}

		@Override
		public void remove()
		{
			// no..
		}
	}
}
