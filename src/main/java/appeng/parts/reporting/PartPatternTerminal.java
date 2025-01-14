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

package appeng.parts.reporting;


import java.util.List;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPartModel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.InvOperation;


public class PartPatternTerminal extends AbstractPartTerminal
{

	@PartModels
	public static final ResourceLocation MODEL_OFF = new ResourceLocation( AppEng.MOD_ID, "part/pattern_terminal_off" );
	@PartModels
	public static final ResourceLocation MODEL_ON = new ResourceLocation( AppEng.MOD_ID, "part/pattern_terminal_on" );

	public static final IPartModel MODELS_OFF = new PartModel( MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF );
	public static final IPartModel MODELS_ON = new PartModel( MODEL_BASE, MODEL_ON, MODEL_STATUS_ON );
	public static final IPartModel MODELS_HAS_CHANNEL = new PartModel( MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL );

	private final AppEngInternalInventory crafting = new AppEngInternalInventory( this, 9 );
	private final AppEngInternalInventory output = new AppEngInternalInventory( this, 3 );
	private final AppEngInternalInventory pattern = new AppEngInternalInventory( this, 2 );
	private final AppEngInternalInventory inputFluids = new AppEngInternalInventory( this, 3 );
	private final AppEngInternalInventory outputFluids = new AppEngInternalInventory( this, 3 );

	private boolean craftingMode = true;
	private boolean substitute = false;

	@Reflected
	public PartPatternTerminal( final ItemStack is )
	{
		super( is );
	}

	@Override
	public void getDrops( final List<ItemStack> drops, final boolean wrenched )
	{
		for( final ItemStack is : this.pattern )
		{
			if( !is.isEmpty() )
			{
				drops.add( is );
			}
		}
	}

	@Override
	public void readFromNBT( final NBTTagCompound data )
	{
		super.readFromNBT( data );
		this.setCraftingRecipe( data.getBoolean( "craftingMode" ) );
		this.setSubstitution( data.getBoolean( "substitute" ) );
		this.pattern.readFromNBT( data, "pattern" );
		this.output.readFromNBT( data, "outputList" );
		this.crafting.readFromNBT( data, "craftingGrid" );
		this.inputFluids.readFromNBT(data, "inFluids");
		this.outputFluids.readFromNBT(data, "outFluids");
	}

	@Override
	public void writeToNBT( final NBTTagCompound data )
	{
		super.writeToNBT( data );
		data.setBoolean( "craftingMode", this.craftingMode );
		data.setBoolean( "substitute", this.substitute );
		this.pattern.writeToNBT( data, "pattern" );
		this.output.writeToNBT( data, "outputList" );
		this.crafting.writeToNBT( data, "craftingGrid" );
		this.inputFluids.writeToNBT(data, "inFluids");
		this.outputFluids.writeToNBT(data, "outFluids");
	}

	@Override
	public GuiBridge getGui( final EntityPlayer p )
	{
		int x = (int) p.posX;
		int y = (int) p.posY;
		int z = (int) p.posZ;
		if( this.getHost().getTile() != null )
		{
			x = this.getTile().getPos().getX();
			y = this.getTile().getPos().getY();
			z = this.getTile().getPos().getZ();
		}

		if( GuiBridge.GUI_PATTERN_TERMINAL.hasPermissions( this.getHost().getTile(), x, y, z, this.getSide(), p ) )
		{
			return GuiBridge.GUI_PATTERN_TERMINAL;
		}
		return GuiBridge.GUI_ME;
	}

	@Override
	public void onChangeInventory( final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack )
	{
		if( inv == this.pattern && slot == 1 )
		{
			final ItemStack is = this.pattern.getStackInSlot( 1 );
			if( !is.isEmpty() && is.getItem() instanceof ICraftingPatternItem )
			{
				final ICraftingPatternItem pattern = (ICraftingPatternItem) is.getItem();
				final ICraftingPatternDetails details = pattern.getPatternForItem( is, this.getHost().getTile().getWorld() );
				if( details != null )
				{
					this.setCraftingRecipe( details.isCraftable() );
					this.setSubstitution( details.canSubstitute() );
					IAEStack[] inputs = details.getChannelInputs(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
					IAEStack[] outputs = details.getChannelOutputs(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
					IAEStack[] inputFluids = details.getChannelInputs(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
					IAEStack[] outputFluids = details.getChannelOutputs(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

					for( int x = 0; x < this.crafting.getSlots() && x < inputs.length; x++ )
					{
						final IAEItemStack item = (IAEItemStack)inputs[x];
						this.crafting.setStackInSlot( x, item == null ? ItemStack.EMPTY : item.getItemStack() );
					}

					for( int x = 0; x < this.output.getSlots() && x < outputs.length; x++ )
					{
						final IAEItemStack item = (IAEItemStack)outputs[x];
						this.output.setStackInSlot( x, item == null ? ItemStack.EMPTY : item.getItemStack() );
					}

					for(int x = 0; x < this.inputFluids.getSlots() && x < inputFluids.length; x++)
					{
						final IAEFluidStack fluid = (IAEFluidStack)inputFluids[x];

						if(fluid == null)
							this.inputFluids.setStackInSlot( x, ItemStack.EMPTY );
						else
						{
							ItemStack stack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack( 1 ).get();
							FluidDummyItem item = (FluidDummyItem) stack.getItem();
							item.setFluidStack( stack, fluid.getFluidStack() );
							this.inputFluids.setStackInSlot(x, stack);
						}
					}

					for(int x = 0; x < this.outputFluids.getSlots() && x < outputFluids.length; x++)
					{
						final IAEFluidStack fluid = (IAEFluidStack)outputFluids[x];

						if(fluid == null)
							this.outputFluids.setStackInSlot( x, ItemStack.EMPTY );
						else
						{
							ItemStack stack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack( 1 ).get();
							FluidDummyItem item = (FluidDummyItem) stack.getItem();
							item.setFluidStack( stack, fluid.getFluidStack() );
							this.outputFluids.setStackInSlot(x, stack);
						}
					}
				}
			}
		}
		else if( inv == this.crafting )
		{
			this.fixCraftingRecipes();
		}

		this.getHost().markForSave();
	}

	private void fixCraftingRecipes()
	{
		if( this.craftingMode )
		{
			for( int x = 0; x < this.crafting.getSlots(); x++ )
			{
				final ItemStack is = this.crafting.getStackInSlot( x );
				if( !is.isEmpty() )
				{
					is.setCount( 1 );
				}
			}
		}
	}

	public boolean isCraftingRecipe()
	{
		return this.craftingMode;
	}

	public void setCraftingRecipe( final boolean craftingMode )
	{
		this.craftingMode = craftingMode;
		this.fixCraftingRecipes();
	}

	public boolean isSubstitution()
	{
		return this.substitute;
	}

	public void setSubstitution( final boolean canSubstitute )
	{
		this.substitute = canSubstitute;
	}

	@Override
	public IItemHandler getInventoryByName( final String name )
	{
		switch (name) {
			case "crafting":
				return this.crafting;
			case "output":
				return this.output;
			case "pattern":
				return this.pattern;
			case "inFluids":
				return this.inputFluids;
			case "outFluids":
				return this.outputFluids;
			default:
				return super.getInventoryByName(name);
		}
	}

	@Override
	public IPartModel getStaticModels()
	{
		return this.selectModel( MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL );
	}
}
