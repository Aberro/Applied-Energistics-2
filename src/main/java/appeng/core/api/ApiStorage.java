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

package appeng.core.api;


import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import appeng.api.util.ISlot;
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.util.inv.ItemSlot;
import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageHelper;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingLink;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.FluidList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class ApiStorage implements IStorageHelper
{

	private final ClassToInstanceMap<IStorageChannel<?, ?, ?>> channels;

	public ApiStorage()
	{
		this.channels = MutableClassToInstanceMap.create();
		this.registerStorageChannel( IItemStorageChannel.class, new ItemStorageChannel() );
		this.registerStorageChannel( IFluidStorageChannel.class, new FluidStorageChannel() );
	}

	@Override

	public <TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack, C extends IStorageChannel<TAEStack, TSlot, TStack>> void registerStorageChannel(Class<C> channel, C factory )
	{
		Preconditions.checkNotNull( channel );
		Preconditions.checkNotNull( factory );
		Preconditions.checkArgument( channel.isInstance( factory ) );
		Preconditions.checkArgument( !this.channels.containsKey( channel ) );

		this.channels.putInstance( channel, factory );
	}

	@Override
	public <TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack, C extends IStorageChannel<TAEStack, TSlot, TStack>> C getStorageChannel( Class<C> channel )
	{
		Preconditions.checkNotNull( channel );

		final C type = this.channels.getInstance( channel );

		Preconditions.checkNotNull( type );

		return type;
	}

	@Override
	public Collection<IStorageChannel<? extends IAEStack, ?, ?>> storageChannels()
	{
		return Collections.unmodifiableCollection( this.channels.values() );
	}

	@Override
	public ICraftingLink loadCraftingLink( final NBTTagCompound data, final ICraftingRequester req )
	{
		Preconditions.checkNotNull( data );
		Preconditions.checkNotNull( req );

		return new CraftingLink( data, req );
	}

	@Override
	public <T extends IAEStack> T poweredInsert( IEnergySource energy, IMEInventory inv, T input, IActionSource src, Actionable mode )
	{
		return Platform.poweredInsert( energy, inv, input, src, mode );
	}

	@Override
	public <T extends IAEStack> T poweredExtraction( IEnergySource energy, IMEInventory inv, T request, IActionSource src, Actionable mode )
	{
		return Platform.poweredExtraction( energy, inv, request, src, mode );
	}

	@Override
	public void postChanges( IStorageGrid gs, ItemStack removedCell, ItemStack addedCell, IActionSource src )
	{
		Preconditions.checkNotNull( gs );
		Preconditions.checkNotNull( removedCell );
		Preconditions.checkNotNull( addedCell );
		Preconditions.checkNotNull( src );

		Platform.postChanges( gs, removedCell, addedCell, src );
	}

	private static final class ItemStorageChannel implements IItemStorageChannel
	{

		@Override
		public IStorageChannel getChannelType() {
			return this;
		}

		@Override
		public String getPatternNBTInputTag() { return "in"; }

		@Override
		public String getPatternNBTOutputTag() { return "out"; }

		@Override
		public String getNBTName() { return "items"; }

		public ItemStack getEMPTY() { return ItemStack.EMPTY; }

		public ItemSlot createSlot()
		{
			return new ItemSlot();
		}

		@Override
		public IItemList<IAEStack> createList()
		{
			return new ItemList();
		}

		@Override
		public IAEItemStack createStack( Object input )
		{
			Preconditions.checkNotNull( input );

			if( input instanceof ItemStack )
			{
				return AEItemStack.fromItemStack( (ItemStack) input );
			}

			return null;
		}

		@Override
		public IAEItemStack createFromNBT( NBTTagCompound nbt )
		{
			Preconditions.checkNotNull( nbt );
			return AEItemStack.fromNBT( nbt );
		}

		@Override
		public int getDefaultStackLimit() {
			return 64;
		}

		@Override
		public IAEItemStack readFromPacket( ByteBuf input ) throws IOException
		{
			Preconditions.checkNotNull( input );

			return AEItemStack.fromPacket( input );
		}
	}

	private static final class FluidStorageChannel implements IFluidStorageChannel
	{
		@Override
		public IStorageChannel getChannelType() {
			return this;
		}

		@Override
		public String getPatternNBTInputTag() { return "inFluids"; }

		@Override
		public String getPatternNBTOutputTag() { return "outFluids"; }

		@Override
		public String getNBTName() { return "fluids"; }

		@Override
		public FluidStack getEMPTY() { return null; }

		@Override
		public ISlot createSlot() {
			throw new NotImplementedException();
		}

		@Override
		public int transferFactor()
		{
			return 125;
		}

		@Override
		public int getUnitsPerByte()
		{
			return 8000;
		}

		@Override
		public IItemList<IAEStack> createList()
		{
			return new FluidList();
		}

		@Override
		public IAEFluidStack createStack( Object input )
		{
			Preconditions.checkNotNull( input );

			if( input instanceof FluidStack )
			{
				return AEFluidStack.fromFluidStack( (FluidStack) input );
			}
			if( input instanceof ItemStack )
			{
				final ItemStack is = (ItemStack) input;
				if( is.getItem() instanceof FluidDummyItem )
				{
					return AEFluidStack.fromFluidStack( ( (FluidDummyItem) is.getItem() ).getFluidStack( is ) );
				}
				else
				{
					return AEFluidStack.fromFluidStack( FluidUtil.getFluidContained( is ) );
				}
			}

			return null;
		}

		@Override
		public IAEFluidStack readFromPacket( ByteBuf input ) throws IOException
		{
			Preconditions.checkNotNull( input );

			return AEFluidStack.fromPacket( input );
		}

		@Override
		public IAEFluidStack createFromNBT( NBTTagCompound nbt )
		{
			Preconditions.checkNotNull( nbt );
			return AEFluidStack.fromNBT( nbt );
		}

		@Override
		public int getDefaultStackLimit() {
			return 16000;
		}
	}

}
