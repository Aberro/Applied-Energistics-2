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

package appeng.helpers;


import java.util.*;
import java.util.stream.Collectors;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.api.ApiStorage;
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.fluids.util.AEFluidStack;
import com.google.common.collect.Iterables;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;


public class PatternHelper implements ICraftingPatternDetails, Comparable<PatternHelper>
{

	private final ItemStack patternItem;
	private final InventoryCrafting crafting = new InventoryCrafting( new ContainerNull(), 3, 3 );
	private final InventoryCrafting testFrame = new InventoryCrafting( new ContainerNull(), 3, 3 );
	private final ItemStack correctOutput; // This is for crafting mode only.
	private final IRecipe standardRecipe;
	private final Map<IStorageChannel, IAEStack[]> inputs;
	private final Map<IStorageChannel, IAEStack[]> outputs;
	private final Map<IStorageChannel, IAEStack[]> condensedInputs;
	private final Map<IStorageChannel, IAEStack[]> condensedOutputs;
	private final boolean isCrafting;
	private final boolean canSubstitute;
	private final Set<TestLookup> failCache = new HashSet<>();
	private final Set<TestLookup> passCache = new HashSet<>();
	private final IAEItemStack pattern;
	private int priority = 0;

	public PatternHelper( final ItemStack is, final World w )
	{
		final NBTTagCompound encodedValue = is.getTagCompound();

		if( encodedValue == null )
		{
			throw new IllegalArgumentException( "No pattern here!" );
		}

		this.isCrafting = encodedValue.getBoolean( "crafting" );
		this.canSubstitute = this.isCrafting && encodedValue.getBoolean( "substitute" );

		inputs = new HashMap<>();
		outputs = new HashMap<>();

		Collection<IStorageChannel<?>> channels = AEApi.instance().storage().storageChannels();
		NBTTagList tagList;
		for( IStorageChannel channel : channels)
		{
			// Crafting recipes should read only item inputs, not even an output.
			if(isCrafting && channel != AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class))
				continue;
			tagList = encodedValue.getTagList(channel.getPatternNBTInputTag(), 10);
			if(tagList != null)
			{
				List<IAEStack> in = new ArrayList<>();
				for( int x = 0; x < tagList.tagCount(); x++ )
				{
					NBTTagCompound ingredient = tagList.getCompoundTagAt( x );

					final IAEStack gs = channel.createFromNBT(ingredient);

					if( !ingredient.hasNoTags() && gs == null || gs.isEmpty() )
					{
						throw new IllegalArgumentException( "No pattern here!" );
					}
					in.add( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createStack( gs ) );
					if(channel.getChannelType() == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class))
					{
						ItemStack item = ((IAEItemStack)gs).createItemStack();
						this.crafting.setInventorySlotContents(x, item);

						if (!gs.isEmpty() && (!this.isCrafting || !item.hasTagCompound())) {
							this.markItemAs(x, item, TestStatus.ACCEPT);
						}

						this.testFrame.setInventorySlotContents(x, item);
					}
				}
				inputs.put(channel, in.toArray(new IAEStack[in.size()]));
			}
			// if it's crafting, skip reading outputs.
			if(this.isCrafting)
				continue;
			tagList = encodedValue.getTagList(channel.getPatternNBTOutputTag(), 10);
			if(tagList != null) {
				List<IAEStack> out = new ArrayList<>();
				for( int x = 0; x < tagList.tagCount(); x++)
				{
					NBTTagCompound ingredient = tagList.getCompoundTagAt( x );
					final IAEStack gs = channel.createFromNBT(ingredient);

					if( !ingredient.hasNoTags() && gs.isEmpty() )
						throw new IllegalArgumentException( "No pattern here!" );

					out.add( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createStack( gs ) );
				}
				outputs.put(channel, out.toArray(new IAEStack[out.size()]));
			}
		}

		this.patternItem = is;
		this.pattern = AEItemStack.fromItemStack( is );
		// If this is crafting, no IItemStorageChannel should be added into map at this point, so add it with correct output.
		if( this.isCrafting )
		{
			this.standardRecipe = CraftingManager.findMatchingRecipe( this.crafting, w );

			if( this.standardRecipe != null )
			{
				this.correctOutput = this.standardRecipe.getCraftingResult( this.crafting );
				outputs.put( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ), new IAEItemStack[] { AEItemStack.fromItemStack(this.correctOutput) } );
			}
			else
			{
				throw new IllegalStateException( "No pattern here!" );
			}
		}
		else
		{
			this.standardRecipe = null;
			this.correctOutput = ItemStack.EMPTY;
		}

		// Now, condense all inputs and outputs.
		Map<IStorageChannel, Map<IAEStack, IAEStack>> tmpInputs = new HashMap<>();
		Map<IStorageChannel, Map<IAEStack, IAEStack>> tmpOutputs = new HashMap<>();
		this.condensedInputs = new HashMap<>();
		this.condensedOutputs = new HashMap<>();
		for( final IAEStack[] io : this.inputs.values() )
		{
			for( final IAEStack item : io ) {
				if (item == null || item.isEmpty()) {
					continue;
				}
				Map<IAEStack, IAEStack> list = tmpInputs.get(item.getChannel());
				if(list == null)
					tmpInputs.put(item.getChannel(), list = new HashMap<>());

				IAEStack g = list.get(item);
				if (g == null) {
					list.put(item, item.copy());
				} else {
					g.add(item);
				}
			}
		}
		for( final IAEStack[] io : this.outputs.values() )
		{
			for( final IAEStack item : io ) {
				if (item == null || item.isEmpty()) {
					continue;
				}
				Map<IAEStack, IAEStack> list = tmpOutputs.get(item.getChannel());
				if(list == null)
					tmpOutputs.put(item.getChannel(), list = new HashMap<>());

				IAEStack g = list.get(item);
				if (g == null) {
					list.put(item, item.copy());
				} else {
					g.add(item);
				}
			}
		}

		if( tmpInputs.isEmpty() || tmpOutputs.isEmpty() )
			throw new IllegalStateException( "No pattern here!" );

		for( final IStorageChannel channel : tmpInputs.keySet() )
		{
			Collection<IAEStack> values = tmpInputs.get(channel).values();
			this.condensedInputs.put(channel, values.toArray(new IAEStack[0]));
		}
		for( final IStorageChannel channel : tmpOutputs.keySet() )
		{
			Collection<IAEStack> values = tmpOutputs.get(channel).values();
			this.condensedOutputs.put(channel, values.toArray(new IAEStack[0]));
		}
	}

	private void markItemAs( final int slotIndex, final ItemStack i, final TestStatus b )
	{
		if( b == TestStatus.TEST || i.hasTagCompound() )
		{
			return;
		}

		( b == TestStatus.ACCEPT ? this.passCache : this.failCache ).add( new TestLookup( slotIndex, i ) );
	}

	@Override
	public ItemStack getPattern()
	{
		return this.patternItem;
	}

	@Override
	public synchronized boolean isValidItemForSlot( final int slotIndex, final ItemStack i, final World w )
	{
		if( !this.isCrafting )
		{
			throw new IllegalStateException( "Only crafting recipes supported." );
		}

		final TestStatus result = this.getStatus( slotIndex, i );

		switch( result )
		{
			case ACCEPT:
				return true;
			case DECLINE:
				return false;
			case TEST:
			default:
				break;
		}

		for( int x = 0; x < this.crafting.getSizeInventory(); x++ )
		{
			this.testFrame.setInventorySlotContents( x, this.crafting.getStackInSlot( x ) );
		}

		this.testFrame.setInventorySlotContents( slotIndex, i );

		if( this.standardRecipe.matches( this.testFrame, w ) )
		{
			final ItemStack testOutput = this.standardRecipe.getCraftingResult( this.testFrame );

			if( Platform.itemComparisons().isSameItem( this.correctOutput, testOutput ) )
			{
				this.testFrame.setInventorySlotContents( slotIndex, this.crafting.getStackInSlot( slotIndex ) );
				this.markItemAs( slotIndex, i, TestStatus.ACCEPT );
				return true;
			}
		}
		else if( AEConfig.instance().isFeatureEnabled( AEFeature.CRAFTING_MANAGER_FALLBACK ) )
		{
			final ItemStack testOutput = CraftingManager.findMatchingResult( this.testFrame, w );

			if( Platform.itemComparisons().isSameItem( this.correctOutput, testOutput ) )
			{
				this.testFrame.setInventorySlotContents( slotIndex, this.crafting.getStackInSlot( slotIndex ) );
				this.markItemAs( slotIndex, i, TestStatus.ACCEPT );

				if( AELog.isCraftingDebugLogEnabled() )
				{
					this.warnAboutCraftingManager( true );
				}

				return true;
			}

			this.warnAboutCraftingManager( false );
		}

		this.markItemAs( slotIndex, i, TestStatus.DECLINE );
		return false;
	}

	@Override
	public boolean isCraftable()
	{
		return this.isCrafting;
	}

	@Override
	public IAEItemStack[] getInputs()
	{
		IAEStack[] result = getChannelInputs( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ) );
		return Arrays.copyOf(result, result.length, IAEItemStack[].class);
	}
	@Override
	public IAEItemStack getOutput()
	{
		IAEStack[] result = getChannelOutputs( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ) );
		if(result != null && result.length > 0)
			return (IAEItemStack)result[0];
		return null;
	}

	@Override
	public IAEStack[] getChannelInputs(IStorageChannel channel)
	{
		return inputs.get(channel);
	}

	@Override
	public IAEStack[] getChannelOutputs(IStorageChannel channel)
	{
		return outputs.get(channel);
	}
	@Override
	public IAEStack[] getChannelCondensedInputs(IStorageChannel channel)
	{
		return condensedInputs.get(channel);
	}
	@Override
	public IAEStack[] getChannelCondensedOutputs(IStorageChannel channel)
	{
		return condensedOutputs.get(channel);
	}
	@Override
	public IAEStack[] getAllCondensedInputs()
	{
		return this.condensedInputs.values().stream().flatMap(Arrays::stream).collect(Collectors.toList()).toArray(new IAEStack[0]);
	}
	@Override
	public IAEStack[] getAllCondensedOutputs()
	{
		return this.condensedOutputs.values().stream().flatMap(Arrays::stream).collect(Collectors.toList()).toArray(new IAEStack[0]);
	}

	@Override
	public boolean canSubstitute()
	{
		return this.canSubstitute;
	}

	@Override
	public ItemStack getOutput( final InventoryCrafting craftingInv, final World w )
	{
		if( !this.isCrafting )
		{
			throw new IllegalStateException( "Only crafting recipes supported." );
		}

		for( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			if( !this.isValidItemForSlot( x, craftingInv.getStackInSlot( x ), w ) )
			{
				return ItemStack.EMPTY;
			}
		}
		IAEItemStack output = this.getOutput();

		return output != null ? output.createItemStack() : ItemStack.EMPTY;
	}

	private TestStatus getStatus( final int slotIndex, final ItemStack i )
	{
		if( this.crafting.getStackInSlot( slotIndex ).isEmpty() )
		{
			return i.isEmpty() ? TestStatus.ACCEPT : TestStatus.DECLINE;
		}

		if( i.isEmpty() )
		{
			return TestStatus.DECLINE;
		}

		if( i.hasTagCompound() )
		{
			return TestStatus.TEST;
		}

		if( this.passCache.contains( new TestLookup( slotIndex, i ) ) )
		{
			return TestStatus.ACCEPT;
		}

		if( this.failCache.contains( new TestLookup( slotIndex, i ) ) )
		{
			return TestStatus.DECLINE;
		}

		return TestStatus.TEST;
	}

	@Override
	public int getPriority()
	{
		return this.priority;
	}

	@Override
	public void setPriority( final int priority )
	{
		this.priority = priority;
	}

	@Override
	public int compareTo( final PatternHelper o )
	{
		return Integer.compare( o.priority, this.priority );
	}

	@Override
	public int hashCode()
	{
		return this.pattern.hashCode();
	}

	private void warnAboutCraftingManager( boolean foundAlternative )
	{
		final String foundAlternativeRecipe = foundAlternative ? "Found alternative recipe." : "NOT FOUND, please report.";

		final StringJoiner joinActualInputs = new StringJoiner( ", " );
		for( int j = 0; j < this.testFrame.getSizeInventory(); j++ )
		{
			final ItemStack stack = this.testFrame.getStackInSlot( j );
			if( !stack.isEmpty() )
			{
				joinActualInputs.add( stack.toString() );
			}
		}

		AELog.warn( "Using CraftingManager fallback: Recipe <%s> for output <%s> rejected inputs [%s]. %s",
				this.standardRecipe.getRegistryName(), this.standardRecipe.getRecipeOutput(), joinActualInputs, foundAlternativeRecipe );
	}

	@Override
	public boolean equals( final Object obj )
	{
		if( obj == null )
		{
			return false;
		}
		if( this.getClass() != obj.getClass() )
		{
			return false;
		}

		final PatternHelper other = (PatternHelper) obj;

		if( this.pattern != null && other.pattern != null )
		{
			return this.pattern.equals( other.pattern );
		}
		return false;
	}

	private enum TestStatus
	{
		ACCEPT, DECLINE, TEST
	}

	private static final class TestLookup
	{

		private final int slot;
		private final int ref;
		private final int hash;

		public TestLookup( final int slot, final ItemStack i )
		{
			this( slot, i.getItem(), i.getItemDamage() );
		}

		public TestLookup( final int slot, final Item item, final int dmg )
		{
			this.slot = slot;
			this.ref = ( dmg << Platform.DEF_OFFSET ) | ( Item.getIdFromItem( item ) & 0xffff );
			final int offset = 3 * slot;
			this.hash = ( this.ref << offset ) | ( this.ref >> ( offset + 32 ) );
		}

		@Override
		public int hashCode()
		{
			return this.hash;
		}

		@Override
		public boolean equals( final Object obj )
		{
			final boolean equality;

			if( obj instanceof TestLookup )
			{
				final TestLookup b = (TestLookup) obj;

				equality = b.slot == this.slot && b.ref == this.ref;
			}
			else
			{
				equality = false;
			}

			return equality;
		}
	}
}
