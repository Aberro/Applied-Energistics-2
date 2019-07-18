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

package appeng.me.cluster.implementations;


import java.util.*;
import java.util.Map.Entry;

import appeng.api.networking.crafting.*;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.*;
import appeng.util.item.MixedList;
import com.google.common.collect.ImmutableList;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IItemList;
import appeng.api.util.WorldCoord;
import appeng.container.ContainerNull;
import appeng.core.AELog;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.IAECluster;
import appeng.me.helpers.MachineSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;


public final class CraftingCPUCluster implements IAECluster, ICraftingCPU
{

	private static final String LOG_MARK_AS_COMPLETE = "Completed job for %s.";

	private final WorldCoord min;
	private final WorldCoord max;
	private final int[] usedOps = new int[3];
	private final Map<ICraftingPatternDetails, TaskProgress> tasks = new HashMap<>();
	// INSTANCE sate
	private final List<TileCraftingTile> tiles = new ArrayList<>();
	private final List<TileCraftingTile> storage = new ArrayList<>();
	private final List<TileCraftingMonitorTile> status = new ArrayList<>();
	private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
	private ICraftingLink myLastLink;
	private String myName = "";
	private boolean isDestroyed = false;
	/**
	 * crafting job info
	 */
	private MECraftingInventory inventory = new MECraftingInventory();
	private IAEStack finalOutput;
	private boolean waiting = false;
	private IItemList<IAEStack> waitingFor = new MixedList();
	private long availableStorage = 0;
	private MachineSource machineSrc = null;
	private int accelerator = 0;
	private boolean isComplete = true;
	private int remainingOperations;
	private boolean somethingChanged;

	private long lastTime;
	private long elapsedTime;
	private long startItemCount;
	private long remainingItemCount;

	public CraftingCPUCluster( final WorldCoord min, final WorldCoord max )
	{
		this.min = min;
		this.max = max;
	}

	public boolean isDestroyed()
	{
		return this.isDestroyed;
	}

	public ICraftingLink getLastCraftingLink()
	{
		return this.myLastLink;
	}

	/**
	 * add a new Listener to the monitor, be sure to properly remove yourself when your done.
	 */
	@Override
	public void addListener( final IMEMonitorHandlerReceiver l, final Object verificationToken )
	{
		this.listeners.put( l, verificationToken );
	}

	/**
	 * remove a Listener to the monitor.
	 */
	@Override
	public void removeListener( final IMEMonitorHandlerReceiver l )
	{
		this.listeners.remove( l );
	}

	public IMEInventory getInventory()
	{
		return this.inventory;
	}

	@Override
	public void updateStatus( final boolean updateGrid )
	{
		for( final TileCraftingTile r : this.tiles )
		{
			r.updateMeta( true );
		}
	}

	@Override
	public void destroy()
	{
		if( this.isDestroyed )
		{
			return;
		}
		this.isDestroyed = true;

		boolean posted = false;

		for( final TileCraftingTile r : this.tiles )
		{
			final IGridNode n = r.getActionableNode();
			if( n != null && !posted )
			{
				final IGrid g = n.getGrid();
				if( g != null )
				{
					g.postEvent( new MENetworkCraftingCpuChange( n ) );
					posted = true;
				}
			}

			r.updateStatus( null );
		}
	}

	@Override
	public Iterator<IGridHost> getTiles()
	{
		return (Iterator) this.tiles.iterator();
	}

	void addTile( final TileCraftingTile te )
	{
		if( this.machineSrc == null || te.isCoreBlock() )
		{
			this.machineSrc = new MachineSource( te );
		}

		te.setCoreBlock( false );
		te.saveChanges();
		this.tiles.add( 0, te );

		if( te.isStorage() )
		{
			this.availableStorage += te.getStorageBytes();
			this.storage.add( te );
		}
		else if( te.isStatus() )
		{
			this.status.add( (TileCraftingMonitorTile) te );
		}
		else if( te.isAccelerator() )
		{
			this.accelerator++;
		}
	}

	public boolean canAccept( final IAEStack input )
	{
		if( input instanceof IAEStack )
		{
			final IAEStack is = this.waitingFor.findPrecise( input );
			if( is != null && is.getStackSize() > 0 )
			{
				return true;
			}
		}
		return false;
	}

	public IAEStack injectItems( final IAEStack input, final Actionable type, final IActionSource src )
	{
		final IAEStack what = input.copy();
		final IAEStack is = this.waitingFor.findPrecise( what );

		if( type == Actionable.SIMULATE )// causes crafting to lock up?
		{
			if( is != null && is.getStackSize() > 0 )
			{
				if( is.getStackSize() >= what.getStackSize() )
				{
					if( this.finalOutput.equals( what ) )
					{
						if( this.myLastLink != null )
						{
							return ( (CraftingLink) this.myLastLink ).injectItems( what.copy(), type );
						}

						return what; // ignore it.
					}

					return null;
				}

				final IAEStack leftOver = what.copy();
				leftOver.decStackSize( is.getStackSize() );

				final IAEStack used = what.copy();
				used.setStackSize( is.getStackSize() );

				if( this.finalOutput.equals( what ) )
				{
					if( this.myLastLink != null )
					{
						leftOver.add( ( (CraftingLink) this.myLastLink ).injectItems( used.copy(), type ) );
						return leftOver;
					}

					return what; // ignore it.
				}

				return leftOver;
			}
		}
		else if( type == Actionable.MODULATE )
		{
			if( is != null && is.getStackSize() > 0 )
			{
				this.waiting = false;

				this.postChange( what, src );

				if( is.getStackSize() >= what.getStackSize() )
				{
					is.decStackSize( what.getStackSize() );

					this.updateElapsedTime( what );
					this.markDirty();
					this.postCraftingStatusChange( what.copy().setStackSize( -what.getStackSize() ) );

					if( this.finalOutput.equals( what ) )
					{
						IAEStack leftover = what;

						this.finalOutput.decStackSize( what.getStackSize() );

						if( this.myLastLink != null )
						{
							leftover = ( (CraftingLink) this.myLastLink ).injectItems( what, type );
						}

						if( this.finalOutput.getStackSize() <= 0 )
						{
							this.completeJob();
						}

						this.updateCPU();

						return leftover; // ignore it.
					}

					// 2000
					return this.inventory.injectItems( what, type, src );
				}

				final IAEStack insert = what.copy();
				insert.setStackSize( is.getStackSize() );
				what.decStackSize( is.getStackSize() );

				is.setStackSize( 0 );
				this.postCraftingStatusChange( insert.copy().setStackSize( -insert.getStackSize() ) );

				if( this.finalOutput.equals( insert ) )
				{
					IAEStack leftover = input;

					this.finalOutput.decStackSize( insert.getStackSize() );

					if( this.myLastLink != null )
					{
						what.add( ( (CraftingLink) this.myLastLink ).injectItems( insert.copy(), type ) );
						leftover = what;
					}

					if( this.finalOutput.getStackSize() <= 0 )
					{
						this.completeJob();
					}

					this.updateCPU();
					this.markDirty();

					return leftover; // ignore it.
				}

				this.inventory.injectItems( insert, type, src );
				this.markDirty();

				return what;
			}
		}

		return input;
	}

	private void postChange( final IAEStack diff, final IActionSource src )
	{
		final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

		// protect integrity
		if( i.hasNext() )
		{
			final ImmutableList<IAEStack> single = ImmutableList.of( diff.copy() );

			while( i.hasNext() )
			{
				final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
				final IMEMonitorHandlerReceiver receiver = o.getKey();

				if( receiver.isValid( o.getValue() ) )
				{
					receiver.postChange( null, single, src );
				}
				else
				{
					i.remove();
				}
			}
		}

	}

	private void markDirty()
	{
		this.getCore().saveChanges();
	}

	private void postCraftingStatusChange( final IAEStack diff )
	{
		if( this.getGrid() == null )
		{
			return;
		}

		final CraftingGridCache sg = this.getGrid().getCache( ICraftingGrid.class );

		if( sg.getInterestManager().containsKey( diff ) )
		{
			final Collection<CraftingWatcher> list = sg.getInterestManager().get( diff );

			if( !list.isEmpty() )
			{
				for( final CraftingWatcher iw : list )

				{
					iw.getHost().onRequestChange( sg, diff );
				}
			}
		}
	}

	private void completeJob()
	{
		if( this.myLastLink != null )
		{
			( (CraftingLink) this.myLastLink ).markDone();
		}

		if( AELog.isCraftingLogEnabled() )
		{
			final IAEStack logStack = this.finalOutput.copy();
			logStack.setStackSize( this.startItemCount );
			AELog.crafting( LOG_MARK_AS_COMPLETE, logStack );
		}

		this.remainingItemCount = 0;
		this.startItemCount = 0;
		this.lastTime = 0;
		this.elapsedTime = 0;
		this.isComplete = true;
	}

	private void updateCPU()
	{
		IAEStack send = this.finalOutput;

		if( this.finalOutput != null && this.finalOutput.getStackSize() <= 0 )
		{
			send = null;
		}

		for( final TileCraftingMonitorTile t : this.status )
		{
			t.setJob( send );
		}
	}

	private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners()
	{
		return this.listeners.entrySet().iterator();
	}

	private TileCraftingTile getCore()
	{
		if( this.machineSrc == null )
		{
			return null;
		}
		return (TileCraftingTile) this.machineSrc.machine().get();
	}

	private IGrid getGrid()
	{
		for( final TileCraftingTile r : this.tiles )
		{
			final IGridNode gn = r.getActionableNode();
			if( gn != null )
			{
				final IGrid g = gn.getGrid();
				if( g != null )
				{
					return r.getActionableNode().getGrid();
				}
			}
		}

		return null;
	}

	private boolean canCraft( final ICraftingPatternDetails details, final IAEStack[] condensedInputs )
	{
		for( IAEStack g : condensedInputs )
		{

			if( details.isCraftable() )
			{
				boolean found = false;

				for( IAEStack fuzz : this.inventory.getItemList(g.getChannel()).findFuzzy( g, FuzzyMode.IGNORE_ALL ) )
				{
					fuzz = fuzz.copy();
					fuzz.setStackSize( g.getStackSize() );
					final IAEStack ais = this.inventory.extractItems( fuzz, Actionable.SIMULATE, this.machineSrc );
					if(!(ais instanceof IAEItemStack))
						continue;
					final ItemStack is = ais == null ? ItemStack.EMPTY : ((IAEItemStack)ais).getItemStack();

					if( !is.isEmpty() && is.getCount() == g.getStackSize() )
					{
						found = true;
						break;
					}
					else if( !is.isEmpty() )
					{
						g = g.copy();
						g.decStackSize( is.getCount() );
					}
				}

				if( !found )
				{
					return false;
				}
			}
			else
			{
				final IAEStack ais = this.inventory.extractItems( g.copy(), Actionable.SIMULATE, this.machineSrc );


				if( ais.isEmpty() || ais.getStackSize() < g.getStackSize() )
				{
					return false;
				}
			}
		}

		return true;
	}

	public void cancel()
	{
		if( this.myLastLink != null )
		{
			this.myLastLink.cancel();
		}

		final IItemList<IAEStack> list;
		this.getListOfItem( list = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList(), CraftingItemList.ALL );
		for( final IAEStack is : list )
		{
			this.postChange( is, this.machineSrc );
		}

		this.isComplete = true;
		this.myLastLink = null;
		this.tasks.clear();

		// final ImmutableSet<IAEStack> items = ImmutableSet.copyOf( this.waitingFor );
		final List<IAEStack> items = new ArrayList<>( this.waitingFor.size() );
		this.waitingFor.forEach( stack -> items.add( stack.copy().setStackSize( -stack.getStackSize() ) ) );

		this.waitingFor.resetStatus();

		for( final IAEStack is : items )
		{
			this.postCraftingStatusChange( is );
		}

		this.finalOutput = null;
		this.updateCPU();

		this.storeItems(); // marks dirty
	}

	public void updateCraftingLogic( final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc )
	{
		if( !this.getCore().isActive() )
		{
			return;
		}

		if( this.myLastLink != null )
		{
			if( this.myLastLink.isCanceled() )
			{
				this.myLastLink = null;
				this.cancel();
			}
		}

		if( this.isComplete )
		{
			for (IStorageChannel channel : AEApi.instance().storage().storageChannels())
			{
				if( !this.inventory.getItemList(channel).isEmpty() )
				{
					this.storeItems();
					return;
				}
			}
			return;
		}

		this.waiting = false;
		if( this.waiting || this.tasks.isEmpty() ) // nothing to do here...
		{
			return;
		}

		this.remainingOperations = this.accelerator + 1 - ( this.usedOps[0] + this.usedOps[1] + this.usedOps[2] );
		final int started = this.remainingOperations;

		if( this.remainingOperations > 0 )
		{
			do
			{
				this.somethingChanged = false;
				this.executeCrafting( eg, cc );
			}
			while( this.somethingChanged && this.remainingOperations > 0 );
		}
		this.usedOps[2] = this.usedOps[1];
		this.usedOps[1] = this.usedOps[0];
		this.usedOps[0] = started - this.remainingOperations;

		if( this.remainingOperations > 0 && !this.somethingChanged )
		{
			this.waiting = true;
		}
	}

	private void executeCrafting( final IEnergyGrid eg, final CraftingGridCache cc )
	{
		final Iterator<Entry<ICraftingPatternDetails, TaskProgress>> i = this.tasks.entrySet().iterator();

		while( i.hasNext() )
		{
			final Entry<ICraftingPatternDetails, TaskProgress> e = i.next();

			if( e.getValue().value <= 0 )
			{
				i.remove();
				continue;
			}

			final ICraftingPatternDetails details = e.getKey();
			executeCraftingInternalHelper helper = new executeCraftingInternalHelper(this, eg, cc, e);

			if(!this.canCraft( details, details.getAllCondensedInputs() ) )
				continue;
			{
				if (helper.executeCraftingInternal(details)) return;
			}
		}
	}
	private class executeCraftingInternalHelper
	{
		IInventoryCrafting ic = null;
		IEnergyGrid eg;
		CraftingGridCache cc;
		Entry<ICraftingPatternDetails, TaskProgress> e;
		CraftingCPUCluster owner;
		public boolean halt = false;

		public executeCraftingInternalHelper(CraftingCPUCluster owner, IEnergyGrid eg, CraftingGridCache cc, Entry<ICraftingPatternDetails, TaskProgress> e)
		{
			this.owner = owner;
			this.eg = eg;
			this.cc = cc;
			this.e = e;
		}

		// Returns true to break cycle, false to continue.
		public boolean executeCraftingInternal(ICraftingPatternDetails details)
		{
			for( final ICraftingMedium m : cc.getMediums( e.getKey() ) )
			{
				if(executeCraftingMediums(m, details) || halt)
					if(halt)
						return true;
					else
						break;
			}

			if( this.ic != null )
			{
				// put stuff back..
				for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
					for( int x = 0; x < this.ic.getSlotsCount(channel); x++ )
					{
						final IAEStack is = this.ic.getStackInSlot(channel, x );
						if( !is.isEmpty() )
						{
							this.owner.inventory.injectItems( is, Actionable.MODULATE, this.owner.machineSrc );
						}
					}
			}
			return false;
		}
		// Returns true to break cycle, false to continue.
		private boolean executeCraftingMediums(ICraftingMedium m, ICraftingPatternDetails details)
		{
			if( e.getValue().value <= 0 )
			{
				return false;
			}

			if( m.isBusy() )
				return false;

			if( ic == null )
			{
				double sum = Arrays.stream(details.getAllCondensedInputs()).mapToInt(x -> (int)x.getStackSize()).sum();

				// power...
				if( eg.extractAEPower( sum, Actionable.MODULATE, PowerMultiplier.CONFIG ) < sum - 0.01 )
				{
					return false;
				}

				//TODO can edit this for large crafting grids.
				ic = details.getInventoryCrafting();
				for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				{
					final IAEStack[] input = details.getChannelInputs(channel);
					boolean found = false;

					for( int x = 0; x < input.length; x++ )
					{
						if( input[x] != null )
						{
							found = false;

							if( details.isCraftable() )
							{
								found = executeCraftingMediumsCraftable(details, input, found, x);
							}
							else
							{
								// This is for processing recipies
								final IAEStack ais = this.owner.inventory.extractItems( input[x].copy(), Actionable.MODULATE, this.owner.machineSrc );

								if( ais != null && !ais.isEmpty() )
								{
									this.owner.postChange( input[x], this.owner.machineSrc );
									ic.setStackInSlot( channel, x, ais );
									if( ais.getStackSize() == input[x].getStackSize() )
									{
										found = true;
										continue;
									}
								}
							}

							if( !found )
							{
								break;
							}
						}
					}

					if( !found )
					{
						// put stuff back..
						for( int x = 0; x < ic.getSlotsCount(channel); x++ )
						{
							final IAEStack is = ic.getStackInSlot(channel, x );
							if( !is.isEmpty() )
							{
								this.owner.inventory.injectItems( is, Actionable.MODULATE, this.owner.machineSrc );
							}
						}
						ic = null;
						return true;
					}
				}
			}

			if( m.pushPattern( details, ic ) )
			{
				Boolean x = pushPattern(details);
				if (x != null) return x;
			}
			return false;
		}

		private boolean executeCraftingMediumsCraftable(ICraftingPatternDetails details, IAEStack[] input, boolean found, int x)
		{
			final Collection<IAEStack> itemList;

			if( details.canSubstitute() )
			{
				itemList = this.owner.inventory.getItemList(input[x].getChannel()).findFuzzy( input[x], FuzzyMode.IGNORE_ALL );
			}
			else
			{
				itemList = new ArrayList<>( 1 );

				final IAEStack item = this.owner.inventory.getItemList(input[x].getChannel()).findPrecise( input[x] );

				if( item != null )
				{
					itemList.add( item );
				}
			}

			for( IAEStack fuzz : itemList )
			{
				fuzz = fuzz.copy();
				fuzz.setStackSize( input[x].getStackSize() );

				if( details.isValidItemForSlot( x, (ItemStack)fuzz.getStack(), this.owner.getWorld() ) )
				{
					final IAEStack ais = this.owner.inventory.extractItems( fuzz, Actionable.MODULATE, this.owner.machineSrc );

					if( ais != null && !ais.isEmpty() )
					{
						this.owner.postChange( ais, this.owner.machineSrc );
						ic.setStackInSlot( AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class), x, ais );
						found = true;
						break;
					}
				}
			}
			return found;
		}

		private Boolean pushPattern(ICraftingPatternDetails details) {
			this.owner.somethingChanged = true;
			this.owner.remainingOperations--;

			for( final IAEStack out : details.getAllCondensedOutputs() )
			{
				this.owner.postChange( out, this.owner.machineSrc );
				this.owner.waitingFor.add( out.copy() );
				this.owner.postCraftingStatusChange( out.copy() );
			}

			if( details.isCraftable() )
			{
				FMLCommonHandler.instance()
						.firePlayerCraftingEvent( Platform.getPlayer( (WorldServer) this.owner.getWorld() ),
								details.getOutput( ic, this.owner.getWorld() ), ic.toMCInventoryCrafting() );
				IStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

				for( int x = 0; x < ic.getSlotsCount(channel); x++ )
				{
					final ItemStack output = Platform.getContainerItem( (ItemStack)ic.getStackInSlot( channel, x ).getStack() );
					if( !output.isEmpty() )
					{
						final IAEStack cItem = AEItemStack.fromItemStack( output );
						this.owner.postChange( cItem, this.owner.machineSrc );
						this.owner.waitingFor.add( cItem );
						this.owner.postCraftingStatusChange( cItem );
					}
				}
			}

			ic = null; // hand off complete!
			this.owner.markDirty();

			e.getValue().value--;
			if( e.getValue().value <= 0 )
			{
				return false;
			}

			if( this.owner.remainingOperations == 0 )
			{
				this.halt = true;
				return true;
			}
			return null;
		}
	}

	private void storeItems()
	{
		final IGrid g = this.getGrid();

		if( g == null )
		{
			return;
		}

		final IStorageGrid sg = g.getCache( IStorageGrid.class );
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
		{
			final IMEInventory ii = sg.getInventory( );

			for( IAEStack is : this.inventory.getItemList(channel) )
			{
				is = this.inventory.extractItems( is.copy(), Actionable.MODULATE, this.machineSrc );

				if( is != null )
				{
					this.postChange( is, this.machineSrc );
					is = ii.injectItems( is, Actionable.MODULATE, this.machineSrc );
				}

				if( is != null )
				{
					this.inventory.injectItems( is, Actionable.MODULATE, this.machineSrc );
				}
			}

			if( this.inventory.getItemList(channel).isEmpty() )
			{
				this.inventory = new MECraftingInventory();
			}
		}

		this.markDirty();
	}

	public ICraftingLink submitJob( final IGrid g, final ICraftingJob job, final IActionSource src, final ICraftingRequester requestingMachine )
	{
		if( !this.tasks.isEmpty() || !this.waitingFor.isEmpty() )
		{
			return null;
		}

		if( !( job instanceof CraftingJob ) )
		{
			return null;
		}

		if( this.isBusy() || !this.isActive() || this.availableStorage < job.getByteTotal() )
		{
			return null;
		}

		final IStorageGrid sg = g.getCache( IStorageGrid.class );
		final IMEInventory storage = sg.getInventory( );
		final MECraftingInventory ci = new MECraftingInventory( storage, true, false, false );

		try
		{
			this.waitingFor.resetStatus();
			( (CraftingJob) job ).getTree().setJob( ci, this, src );
			if( ci.commit( src ) )
			{
				this.finalOutput = job.getOutput();
				this.waiting = false;
				this.isComplete = false;
				this.markDirty();

				this.updateCPU();
				final String craftID = this.generateCraftingID();

				this.myLastLink = new CraftingLink( this.generateLinkData( craftID, requestingMachine == null, false ), this );

				this.prepareElapsedTime();

				if( requestingMachine == null )
				{
					return this.myLastLink;
				}

				final ICraftingLink whatLink = new CraftingLink( this.generateLinkData( craftID, false, true ), requestingMachine );

				this.submitLink( this.myLastLink );
				this.submitLink( whatLink );

				final IItemList<IAEStack> list = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList();
				this.getListOfItem( list, CraftingItemList.ALL );
				for( final IAEStack ge : list )
				{
					this.postChange( ge, this.machineSrc );
				}

				return whatLink;
			}
			else
			{
				this.tasks.clear();
				for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
					this.inventory.getItemList(channel).resetStatus();
			}
		}
		catch( final CraftBranchFailure e )
		{
			this.tasks.clear();
			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				this.inventory.getItemList(channel).resetStatus();
			// AELog.error( e );
		}

		return null;
	}

	@Override
	public boolean isBusy()
	{
		final Iterator<Entry<ICraftingPatternDetails, TaskProgress>> i = this.tasks.entrySet().iterator();

		while( i.hasNext() )
		{
			if( i.next().getValue().value <= 0 )
			{
				i.remove();
			}
		}

		return !this.tasks.isEmpty() || !this.waitingFor.isEmpty();
	}

	@Override
	public IActionSource getActionSource()
	{
		return this.machineSrc;
	}

	@Override
	public long getAvailableStorage()
	{
		return this.availableStorage;
	}

	@Override
	public int getCoProcessors()
	{
		return this.accelerator;
	}

	@Override
	public String getName()
	{
		return this.myName;
	}

	public boolean isActive()
	{
		final TileCraftingTile core = this.getCore();

		if( core == null )
		{
			return false;
		}

		final IGridNode node = core.getActionableNode();
		if( node == null )
		{
			return false;
		}

		return node.isActive();
	}

	private String generateCraftingID()
	{
		final long now = System.currentTimeMillis();
		final int hash = System.identityHashCode( this );
		final int hmm = this.finalOutput == null ? 0 : this.finalOutput.hashCode();

		return Long.toString( now, Character.MAX_RADIX ) + '-' + Integer.toString( hash, Character.MAX_RADIX ) + '-' + Integer.toString( hmm,
				Character.MAX_RADIX );
	}

	private NBTTagCompound generateLinkData( final String craftingID, final boolean standalone, final boolean req )
	{
		final NBTTagCompound tag = new NBTTagCompound();

		tag.setString( "CraftID", craftingID );
		tag.setBoolean( "canceled", false );
		tag.setBoolean( "done", false );
		tag.setBoolean( "standalone", standalone );
		tag.setBoolean( "req", req );

		return tag;
	}

	private void submitLink( final ICraftingLink myLastLink2 )
	{
		if( this.getGrid() != null )
		{
			final CraftingGridCache cc = this.getGrid().getCache( ICraftingGrid.class );
			cc.addLink( (CraftingLink) myLastLink2 );
		}
	}

	public void getListOfItem( final IItemList<IAEStack> list, final CraftingItemList whichList )
	{
		switch( whichList )
		{
			case ACTIVE:
				for( final IAEStack ais : this.waitingFor )
				{
					list.add( ais );
				}
				break;
			case PENDING:
				for( final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet() )
				{
					for( IAEStack ais : t.getKey().getAllCondensedOutputs() )
					{
						ais = ais.copy();
						ais.setStackSize( ais.getStackSize() * t.getValue().value );
						list.add( ais );
					}
				}
				break;
			case STORAGE:
				for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
					this.inventory.getAvailableItems( channel, list );
				break;
			default:
			case ALL:
				for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
					this.inventory.getAvailableItems( channel, list );

				for( final IAEStack ais : this.waitingFor )
				{
					list.add( ais );
				}

				for( final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet() )
				{
					for( IAEStack ais : t.getKey().getAllCondensedOutputs() )
					{
						ais = ais.copy();
						ais.setStackSize( ais.getStackSize() * t.getValue().value );
						list.add( ais );
					}
				}
				break;
		}
	}

	public void addStorage( final IAEStack extractItems )
	{
		this.inventory.injectItems( extractItems, Actionable.MODULATE, null );
	}

	public void addEmitable( final IAEStack i )
	{
		this.waitingFor.add( i );
		this.postCraftingStatusChange( i );
	}

	public void addCrafting( final ICraftingPatternDetails details, final long crafts )
	{
		TaskProgress i = this.tasks.get( details );

		if( i == null )
		{
			this.tasks.put( details, i = new TaskProgress() );
		}

		i.value += crafts;
	}

	public IAEStack getItemStack( final IAEStack what, final CraftingItemList storage2 )
	{
		IAEStack is;

		switch( storage2 )
		{
			case STORAGE:
				is = this.inventory.getItemList(what.getChannel()).findPrecise( what );
				break;
			case ACTIVE:
				is = this.waitingFor.findPrecise( what );
				break;
			case PENDING:

				is = what.copy();
				is.setStackSize( 0 );

				for( final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet() )
				{
					for( final IAEStack ais : t.getKey().getChannelCondensedOutputs(what.getChannel()) )
					{
						if( ais.equals( is ) )
						{
							is.setStackSize( is.getStackSize() + ais.getStackSize() * t.getValue().value );
						}
					}
				}

				break;
			default:
			case ALL:
				throw new IllegalStateException( "Invalid Operation" );
		}

		if( is != null )
		{
			return is.copy();
		}

		is = what.copy();
		is.setStackSize( 0 );
		return is;
	}

	public void writeToNBT( final NBTTagCompound data )
	{
		data.setTag( "finalOutput", this.writeItem( this.finalOutput ) );
		data.setTag( "inventory", this.writeInventory());
		data.setBoolean( "waiting", this.waiting );
		data.setBoolean( "isComplete", this.isComplete );

		if( this.myLastLink != null )
		{
			final NBTTagCompound link = new NBTTagCompound();
			this.myLastLink.writeToNBT( link );
			data.setTag( "link", link );
		}

		final NBTTagList list = new NBTTagList();
		for( final Entry<ICraftingPatternDetails, TaskProgress> e : this.tasks.entrySet() )
		{
			final NBTTagCompound item = this.writeItem( AEItemStack.fromItemStack( e.getKey().getPattern() ) );
			item.setLong( "craftingProgress", e.getValue().value );
			list.appendTag( item );
		}
		data.setTag( "tasks", list );

		data.setTag( "waitingFor", this.writeList( this.waitingFor ) );

		data.setLong( "elapsedTime", this.getElapsedTime() );
		data.setLong( "startItemCount", this.getStartItemCount() );
		data.setLong( "remainingItemCount", this.getRemainingItemCount() );
	}

	private NBTTagCompound writeInventory()
	{
		NBTTagCompound out = new NBTTagCompound();
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
		{
			IItemList<IAEStack> list = this.inventory.getItemList(channel);
			out.setTag(channel.getNBTName(), this.writeList(list));
		}
		return out;
	}

	private NBTTagCompound writeItem( final IAEStack finalOutput2 )
	{
		final NBTTagCompound out = new NBTTagCompound();
		if( finalOutput2 != null )
		{
			finalOutput2.writeToNBT( out );
		}

		return out;
	}

	private NBTTagList writeList( final IItemList<IAEStack> myList )
	{
		final NBTTagList out = new NBTTagList();

		for( final IAEStack ais : myList )
		{
			out.appendTag( this.writeItem( ais ) );
		}

		return out;
	}

	void done()
	{
		final TileCraftingTile core = this.getCore();

		core.setCoreBlock( true );

		if( core.getPreviousState() != null )
		{
			this.readFromNBT( core.getPreviousState() );
			core.setPreviousState( null );
		}

		this.updateCPU();
		this.updateName();
	}

	public void readFromNBT( final NBTTagCompound data )
	{
		this.finalOutput = AEItemStack.fromNBT( (NBTTagCompound) data.getTag( "finalOutput" ) );

		NBTBase tag = data.getTag("inventory");
		if(tag instanceof NBTTagList)
			for( final IAEStack ais : this.readList( (NBTTagList) data.getTag( "inventory" ) ) )
			{
				this.inventory.injectItems( ais, Actionable.MODULATE, this.machineSrc );
			}
		else if (tag instanceof NBTTagCompound)
			readInventory((NBTTagCompound)tag);


		this.waiting = data.getBoolean( "waiting" );
		this.isComplete = data.getBoolean( "isComplete" );

		if( data.hasKey( "link" ) )
		{
			final NBTTagCompound link = data.getCompoundTag( "link" );
			this.myLastLink = new CraftingLink( link, this );
			this.submitLink( this.myLastLink );
		}

		final NBTTagList list = data.getTagList( "tasks", 10 );
		for( int x = 0; x < list.tagCount(); x++ )
		{
			final NBTTagCompound itemTag = list.getCompoundTagAt( x );
			final IAEStack pattern = AEItemStack.fromNBT( itemTag );
			if( pattern != null && pattern instanceof IAEItemStack )
			{
				ItemStack itemStack = (ItemStack)pattern.getStack();
				Item item = itemStack.getItem();
				if( item instanceof ICraftingPatternItem )
				{
					final ICraftingPatternItem cpi = (ICraftingPatternItem) item;
					final ICraftingPatternDetails details = cpi.getPatternForItem( itemStack, this.getWorld() );
					if( details != null )
					{
						final TaskProgress tp = new TaskProgress();
						tp.value = itemTag.getLong( "craftingProgress" );
						this.tasks.put( details, tp );
					}
				}
			}
		}

		this.waitingFor = this.readList( (NBTTagList) data.getTag( "waitingFor" ) );
		for( final IAEStack is : this.waitingFor )
		{
			this.postCraftingStatusChange( is.copy() );
		}

		this.lastTime = System.nanoTime();
		this.elapsedTime = data.getLong( "elapsedTime" );
		this.startItemCount = data.getLong( "startItemCount" );
		this.remainingItemCount = data.getLong( "remainingItemCount" );
	}

	public void updateName()
	{
		this.myName = "";
		for( final TileCraftingTile te : this.tiles )
		{

			if( te.hasCustomInventoryName() )
			{
				if( this.myName.length() > 0 )
				{
					this.myName += ' ' + te.getCustomInventoryName();
				}
				else
				{
					this.myName = te.getCustomInventoryName();
				}
			}
		}
	}

	private void readInventory(final NBTTagCompound tag )
	{
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
		{
			for(IAEStack item : this.readList((NBTTagList)tag.getTag(channel.getNBTName())))
			{
				this.inventory.injectItems(item, Actionable.MODULATE, this.machineSrc );
			}
		}
	}
	private IItemList<IAEStack> readList( final NBTTagList tag )
	{
		final IItemList<IAEStack> out = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList();

		if( tag == null )
		{
			return out;
		}

		for( int x = 0; x < tag.tagCount(); x++ )
		{
			final IAEStack ais = AEItemStack.fromNBT( tag.getCompoundTagAt( x ) );
			if( ais != null )
			{
				out.add( ais );
			}
		}

		return out;
	}

	private World getWorld()
	{
		return this.getCore().getWorld();
	}

	public IAEStack making( final IAEStack what )
	{
		return this.waitingFor.findPrecise( what );
	}

	public void breakCluster()
	{
		final TileCraftingTile t = this.getCore();

		if( t != null )
		{
			t.breakCluster();
		}
	}

	private void prepareElapsedTime()
	{
		this.lastTime = System.nanoTime();
		this.elapsedTime = 0;

		final IItemList<IAEStack> list = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createList();

		this.getListOfItem( list, CraftingItemList.ACTIVE );
		this.getListOfItem( list, CraftingItemList.PENDING );

		int itemCount = 0;
		for( final IAEStack ge : list )
		{
			itemCount += ge.getStackSize();
		}

		this.startItemCount = itemCount;
		this.remainingItemCount = itemCount;
	}

	private void updateElapsedTime( final IAEStack is )
	{
		final long nextStartTime = System.nanoTime();
		this.elapsedTime = this.getElapsedTime() + nextStartTime - this.lastTime;
		this.lastTime = nextStartTime;
		this.remainingItemCount = this.getRemainingItemCount() - is.getStackSize();
	}

	public long getElapsedTime()
	{
		return this.elapsedTime;
	}

	public long getRemainingItemCount()
	{
		return this.remainingItemCount;
	}

	public long getStartItemCount()
	{
		return this.startItemCount;
	}

	private static class TaskProgress
	{
		private long value;
	}
}
