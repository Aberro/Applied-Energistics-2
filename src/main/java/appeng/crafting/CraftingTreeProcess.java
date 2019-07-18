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

package appeng.crafting;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.IInventoryCrafting;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;


public class CraftingTreeProcess
{

	private final CraftingTreeNode parent;
	final ICraftingPatternDetails details;
	private final CraftingJob job;
	private final Map<CraftingTreeNode, Long> nodes = new HashMap<>();
	private final int depth;
	boolean possible = true;
	private World world;
	private long crafts = 0;
	private boolean containerItems;
	private boolean limitQty;
	private boolean fullSimulation;
	private long bytes = 0;

	public CraftingTreeProcess( final ICraftingGrid cc, final CraftingJob job, final ICraftingPatternDetails details, final CraftingTreeNode craftingTreeNode, final int depth )
	{
		this.parent = craftingTreeNode;
		this.details = details;
		this.job = job;
		this.depth = depth;
		final World world = job.getWorld();

		if( details.isCraftable() )
		{
			final IAEItemStack[] list = details.getInputs();

			final IInventoryCrafting ic = details.getInventoryCrafting();
			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			{
				final IAEStack[] is = details.getChannelInputs(channel);
				for (int x = 0; x < ic.getSlotsCount(channel); x++)
				{
					ic.setStackInSlot( channel, x, is[x] );
				}
			}

			FMLCommonHandler.instance().firePlayerCraftingEvent( Platform.getPlayer( (WorldServer) world ), details.getOutput( ic, world ), ic.toMCInventoryCrafting() );

			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			{
				for( int x = 0; x < ic.getSlotsCount(channel); x++ )
				{
					final IAEStack g = ic.getStackInSlot( channel, x );
					if( !g.isEmpty() && g.getStackSize() > 1 )
					{
						this.fullSimulation = true;
					}
				}
			}

			for( final IAEStack part : details.getChannelCondensedInputs( AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) ) )
			{
				final ItemStack g = ((IAEItemStack)part).getItemStack();

				boolean isAnInput = false;
				for( final IAEStack a : details.getChannelCondensedOutputs( AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) ) )
				{
					if( !g.isEmpty() && a != null && a.equals( g ) )
					{
						isAnInput = true;
					}
				}

				if( isAnInput )
				{
					this.limitQty = true;
				}

				if( g.getItem().hasContainerItem( g ) )
				{
					this.limitQty = this.containerItems = true;
				}
			}

			final boolean complicated = false;

			if( this.containerItems || complicated )
			{
				for( int x = 0; x < list.length; x++ )
				{
					final IAEItemStack part = list[x];
					if( part != null )
					{
						this.nodes.put( new CraftingTreeNode( cc, job, part.copy(), this, x, depth + 1 ), (long)part.getStackSize() );
					}
				}
			}
			else
			{
				// this is minor different then below, this slot uses the pattern, but kinda fudges it.
				for( final IAEStack part : details.getChannelCondensedInputs( AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class) ) )
				{
					for( int x = 0; x < list.length; x++ )
					{
						final IAEStack comparePart = list[x];
						if( part != null && part.equals( comparePart ) )
						{
							// use the first slot...
							this.nodes.put( new CraftingTreeNode( cc, job, part.copy(), this, x, depth + 1 ), (long)part.getStackSize() );
							break;
						}
					}
				}
			}
		}
		else
		{
			for( final IAEStack part : details.getAllCondensedInputs() )
			{
				boolean isAnInput = false;
				for( final IAEStack a : details.getAllCondensedOutputs() )
				{
					if( !part.isEmpty() && a != null && a.equals( part ) )
					{
						isAnInput = true;
					}
				}

				if( isAnInput )
				{
					this.limitQty = true;
				}
			}

			for( final IAEStack part : details.getAllCondensedInputs() )
			{
				this.nodes.put( new CraftingTreeNode( cc, job, part.copy(), this, -1, depth + 1 ), (long)part.getStackSize() );
			}
		}
	}

	boolean notRecursive( final ICraftingPatternDetails details )
	{
		return this.parent == null || this.parent.notRecursive( details );
	}

	long getTimes( final long remaining, final long stackSize )
	{
		if( this.limitQty || this.fullSimulation )
		{
			return 1;
		}
		return ( remaining / stackSize ) + ( remaining % stackSize != 0 ? 1 : 0 );
	}

	void request( final MECraftingInventory inv, final long i, final IActionSource src ) throws CraftBranchFailure, InterruptedException
	{
		this.job.handlePausing();

		if( this.fullSimulation )
		{
			final IInventoryCrafting ic = details.getInventoryCrafting();

			for( final Entry<CraftingTreeNode, Long> entry : this.nodes.entrySet() )
			{
				final IAEStack item = entry.getKey().getStack( entry.getValue() );
				final IAEStack stack = entry.getKey().request( inv, item.getStackSize(), src );

				ic.setStackInSlot(stack.getChannel(), entry.getKey().getSlot(), stack );
			}

			FMLCommonHandler.instance().firePlayerCraftingEvent( Platform.getPlayer( (WorldServer) this.world ), this.details.getOutput( ic, this.world ), ic.toMCInventoryCrafting() );

			for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
				for( int x = 0; x < ic.getSlotsCount(channel); x++ )
				{
					IAEStack is = ic.getStackInSlot( channel, x );
					is = AEItemStack.fromItemStack( Platform.getContainerItem( (ItemStack)is.getStack() ) );

					final IAEItemStack o = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createStack( is );
					if( o != null )
					{
						this.bytes++;
						inv.injectItems( o, Actionable.MODULATE, src );
					}
				}
		}
		else
		{
			// request and remove inputs...
			for( final Entry<CraftingTreeNode, Long> entry : this.nodes.entrySet() )
			{
				final IAEStack item = entry.getKey().getStack( entry.getValue() );
				final IAEStack stack = entry.getKey().request( inv, item.getStackSize() * i, src );

				if( this.containerItems )
				{
					final ItemStack is = Platform.getContainerItem( ((IAEItemStack)stack).getItemStack() );
					final IAEItemStack o = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createStack( is );
					if( o != null )
					{
						this.bytes++;
						inv.injectItems( o, Actionable.MODULATE, src );
					}
				}
			}
		}

		// assume its possible.

		// add crafting results..
		for( final IAEStack out : this.details.getAllCondensedOutputs() )
		{
			final IAEStack o = out.copy();
			o.setStackSize( o.getStackSize() * i );
			inv.injectItems( o, Actionable.MODULATE, src );
		}

		this.crafts += i;
	}

	void dive( final CraftingJob job )
	{
		job.addTask( this.getAmountCrafted( this.parent.getStack( 1 ) ), this.crafts, this.details, this.depth );
		for( final CraftingTreeNode pro : this.nodes.keySet() )
		{
			pro.dive( job );
		}

		job.addBytes( 8 + this.crafts + this.bytes );
	}

	IAEStack getAmountCrafted( IAEStack what2 )
	{
		for( final IAEStack is : this.details.getAllCondensedOutputs() )
		{
			if( is.equals( what2 ) )
			{
				what2 = what2.copy();
				what2.setStackSize( is.getStackSize() );
				return what2;
			}
		}

		// more fuzzy!
		for( final IAEStack is : this.details.getAllCondensedInputs() )
		{
			if( is.fuzzyComparison(what2, FuzzyMode.IGNORE_ALL ) )
			{
				what2 = is.copy();
				what2.setStackSize( is.getStackSize() );
				return what2;
			}
		}

		throw new IllegalStateException( "Crafting Tree construction failed." );
	}

	void setSimulate()
	{
		this.crafts = 0;
		this.bytes = 0;

		for( final CraftingTreeNode pro : this.nodes.keySet() )
		{
			pro.setSimulate();
		}
	}

	void setJob( final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster, final IActionSource src ) throws CraftBranchFailure
	{
		craftingCPUCluster.addCrafting( this.details, this.crafts );

		for( final CraftingTreeNode pro : this.nodes.keySet() )
		{
			pro.setJob( storage, craftingCPUCluster, src );
		}
	}

	void getPlan( final IItemList<IAEStack> plan )
	{
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			for( IAEStack i : this.details.getChannelOutputs(channel) )
			{
				i = i.copy();
				i.setCountRequestable( i.getStackSize() * this.crafts );
				plan.addRequestable( i );
			}

		for( final CraftingTreeNode pro : this.nodes.keySet() )
		{
			pro.getPlan( plan );
		}
	}
}
