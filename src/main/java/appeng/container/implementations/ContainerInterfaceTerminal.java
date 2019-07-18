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

package appeng.container.implementations;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import appeng.api.storage.data.IAEStack;
import appeng.api.util.ItemInventoryAdaptor;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.parts.misc.PartInterface;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TileInterface;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.WrapperRangeItemHandler;
import appeng.util.inv.filter.IAEItemFilter;


public final class ContainerInterfaceTerminal extends AEBaseContainer
{

	/**
	 * this stuff is all server side..
	 */

	private static long autoBase = Long.MIN_VALUE;
	private final Map<IInterfaceHost, InvTracker> diList = new HashMap<>();
	private final Map<Long, InvTracker> byId = new HashMap<>();
	private IGrid grid;
	private NBTTagCompound data = new NBTTagCompound();

	public ContainerInterfaceTerminal( final InventoryPlayer ip, final PartInterfaceTerminal anchor )
	{
		super( ip, anchor );

		if( Platform.isServer() )
		{
			this.grid = anchor.getActionableNode().getGrid();
		}

		this.bindPlayerInventory( ip, 0, 222 - /* height of player inventory */82 );
	}

	@Override
	public void detectAndSendChanges()
	{
		if( Platform.isClient() )
		{
			return;
		}

		super.detectAndSendChanges();

		if( this.grid == null )
		{
			return;
		}

		int total = 0;
		boolean missing = false;

		final IActionHost host = this.getActionHost();
		if( host != null )
		{
			final IGridNode agn = host.getActionableNode();
			if( agn != null && agn.isActive() )
			{
				for( final IGridNode gn : this.grid.getMachines( TileInterface.class ) )
				{
					if( gn.isActive() )
					{
						final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
						if( ih.getInterfaceDuality().getConfigManager().getSetting( Settings.INTERFACE_TERMINAL ) == YesNo.NO )
						{
							continue;
						}

						final InvTracker t = this.diList.get( ih );

						if( t == null )
						{
							missing = true;
						}
						else
						{
							final DualityInterface dual = ih.getInterfaceDuality();
							if( !t.unlocalizedName.equals( dual.getTermName() ) )
							{
								missing = true;
							}
						}

						total++;
					}
				}

				for( final IGridNode gn : this.grid.getMachines( PartInterface.class ) )
				{
					if( gn.isActive() )
					{
						final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
						if( ih.getInterfaceDuality().getConfigManager().getSetting( Settings.INTERFACE_TERMINAL ) == YesNo.NO )
						{
							continue;
						}

						final InvTracker t = this.diList.get( ih );

						if( t == null )
						{
							missing = true;
						}
						else
						{
							final DualityInterface dual = ih.getInterfaceDuality();
							if( !t.unlocalizedName.equals( dual.getTermName() ) )
							{
								missing = true;
							}
						}

						total++;
					}
				}
			}
		}

		if( total != this.diList.size() || missing )
		{
			this.regenList( this.data );
		}
		else
		{
			for( final Entry<IInterfaceHost, InvTracker> en : this.diList.entrySet() )
			{
				final InvTracker inv = en.getValue();
				for( int x = 0; x < inv.server.getSlots(); x++ )
				{
					if( this.isDifferent( inv.server.getStackInSlot( x ), inv.client.getStackInSlot( x ) ) )
					{
						this.addItems( this.data, inv, x, 1 );
					}
				}
			}
		}

		if( !this.data.hasNoTags() )
		{
			try
			{
				NetworkHandler.instance().sendTo( new PacketCompressedNBT( this.data ), (EntityPlayerMP) this.getPlayerInv().player );
			}
			catch( final IOException e )
			{
				// :P
			}

			this.data = new NBTTagCompound();
		}
	}

	@Override
	public void doAction( final EntityPlayerMP player, final InventoryAction action, final int slot, final long id )
	{
		final InvTracker inv = this.byId.get( id );
		if( inv != null )
		{
			final ItemStack is = inv.server.getStackInSlot( slot );
			final boolean hasItemInHand = !player.inventory.getItemStack().isEmpty();

			final ItemInventoryAdaptor playerHand = new AdaptorItemHandler( new WrapperCursorItemHandler( player.inventory ) );

			final IItemHandler theSlot = new WrapperFilteredItemHandler( new WrapperRangeItemHandler( inv.server, slot, slot + 1 ), new PatternSlotFilter() );
			final ItemInventoryAdaptor interfaceSlot = new AdaptorItemHandler( theSlot );

			switch( action )
			{
				case PICKUP_OR_SET_DOWN:

					if( hasItemInHand )
					{
						ItemStack inSlot = theSlot.getStackInSlot( 0 );
						if( inSlot.isEmpty() )
						{
							player.inventory.setItemStack( (ItemStack)interfaceSlot.addItems(AEItemStack.fromItemStack( player.inventory.getItemStack() ) ).getStack() );
						}
						else
						{
							inSlot = inSlot.copy();
							final ItemStack inHand = player.inventory.getItemStack().copy();

							ItemHandlerUtil.setStackInSlot( theSlot, 0, ItemStack.EMPTY );
							player.inventory.setItemStack( ItemStack.EMPTY );

							player.inventory.setItemStack( (ItemStack)interfaceSlot.addItems(AEItemStack.fromItemStack( inHand.copy() ) ).getStack() );

							if( player.inventory.getItemStack().isEmpty() )
							{
								player.inventory.setItemStack( inSlot );
							}
							else
							{
								player.inventory.setItemStack( inHand );
								ItemHandlerUtil.setStackInSlot( theSlot, 0, inSlot );
							}
						}
					}
					else
					{
						ItemHandlerUtil.setStackInSlot( theSlot, 0, (ItemStack)playerHand.addItems( AEItemStack.fromItemStack( theSlot.getStackInSlot( 0 ) ) ).getStack() );
					}

					break;
				case SPLIT_OR_PLACE_SINGLE:

					if( hasItemInHand )
					{
						IAEStack extra = playerHand.removeItems( 1, null, null );
						if( !extra.isEmpty() )
						{
							extra = interfaceSlot.addItems( extra );
						}
						if( !extra.isEmpty() )
						{
							playerHand.addItems( extra );
						}
					}
					else if( !is.isEmpty() )
					{
						IAEStack extra = interfaceSlot.removeItems( ( is.getCount() + 1 ) / 2, null, null );
						if( !extra.isEmpty() )
						{
							extra = playerHand.addItems( extra );
						}
						if( !extra.isEmpty() )
						{
							interfaceSlot.addItems( extra );
						}
					}

					break;
				case SHIFT_CLICK:

					final ItemInventoryAdaptor playerInv = ItemInventoryAdaptor.getAdaptor( player );

					ItemHandlerUtil.setStackInSlot( theSlot, 0, (ItemStack)playerInv.addItems( AEItemStack.fromItemStack( theSlot.getStackInSlot( 0 ) ) ).getStack() );

					break;
				case MOVE_REGION:

					final ItemInventoryAdaptor playerInvAd = ItemInventoryAdaptor.getAdaptor( player );
					for( int x = 0; x < inv.server.getSlots(); x++ )
					{
						ItemHandlerUtil.setStackInSlot( inv.server, x, (ItemStack)playerInvAd.addItems( AEItemStack.fromItemStack( inv.server.getStackInSlot( x ) ) ).getStack() );
					}

					break;
				case CREATIVE_DUPLICATE:

					if( player.capabilities.isCreativeMode && !hasItemInHand )
					{
						player.inventory.setItemStack( is.isEmpty() ? ItemStack.EMPTY : is.copy() );
					}

					break;
				default:
					return;
			}

			this.updateHeld( player );
		}
	}

	private void regenList( final NBTTagCompound data )
	{
		this.byId.clear();
		this.diList.clear();

		final IActionHost host = this.getActionHost();
		if( host != null )
		{
			final IGridNode agn = host.getActionableNode();
			if( agn != null && agn.isActive() )
			{
				for( final IGridNode gn : this.grid.getMachines( TileInterface.class ) )
				{
					final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
					final DualityInterface dual = ih.getInterfaceDuality();
					if( gn.isActive() && dual.getConfigManager().getSetting( Settings.INTERFACE_TERMINAL ) == YesNo.YES )
					{
						this.diList.put( ih, new InvTracker( dual, dual.getPatterns(), dual.getTermName() ) );
					}
				}

				for( final IGridNode gn : this.grid.getMachines( PartInterface.class ) )
				{
					final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
					final DualityInterface dual = ih.getInterfaceDuality();
					if( gn.isActive() && dual.getConfigManager().getSetting( Settings.INTERFACE_TERMINAL ) == YesNo.YES )
					{
						this.diList.put( ih, new InvTracker( dual, dual.getPatterns(), dual.getTermName() ) );
					}
				}
			}
		}

		data.setBoolean( "clear", true );

		for( final Entry<IInterfaceHost, InvTracker> en : this.diList.entrySet() )
		{
			final InvTracker inv = en.getValue();
			this.byId.put( inv.which, inv );
			this.addItems( data, inv, 0, inv.server.getSlots() );
		}
	}

	private boolean isDifferent( final ItemStack a, final ItemStack b )
	{
		if( a.isEmpty() && b.isEmpty() )
		{
			return false;
		}

		if( a.isEmpty() || b.isEmpty() )
		{
			return true;
		}

		return !ItemStack.areItemStacksEqual( a, b );
	}

	private void addItems( final NBTTagCompound data, final InvTracker inv, final int offset, final int length )
	{
		final String name = '=' + Long.toString( inv.which, Character.MAX_RADIX );
		final NBTTagCompound tag = data.getCompoundTag( name );

		if( tag.hasNoTags() )
		{
			tag.setLong( "sortBy", inv.sortBy );
			tag.setString( "un", inv.unlocalizedName );
		}

		for( int x = 0; x < length; x++ )
		{
			final NBTTagCompound itemNBT = new NBTTagCompound();

			final ItemStack is = inv.server.getStackInSlot( x + offset );

			// "update" client side.
			ItemHandlerUtil.setStackInSlot( inv.client, x + offset, is.isEmpty() ? ItemStack.EMPTY : is.copy() );

			if( !is.isEmpty() )
			{
				is.writeToNBT( itemNBT );
			}

			tag.setTag( Integer.toString( x + offset ), itemNBT );
		}

		data.setTag( name, tag );
	}

	private static class InvTracker
	{

		private final long sortBy;
		private final long which = autoBase++;
		private final String unlocalizedName;
		private final IItemHandler client;
		private final IItemHandler server;

		public InvTracker( final DualityInterface dual, final IItemHandler patterns, final String unlocalizedName )
		{
			this.server = patterns;
			this.client = new AppEngInternalInventory( null, this.server.getSlots() );
			this.unlocalizedName = unlocalizedName;
			this.sortBy = dual.getSortValue();
		}
	}

	private static class PatternSlotFilter implements IAEItemFilter
	{
		@Override
		public boolean allowExtract( IItemHandler inv, int slot, int amount )
		{
			return true;
		}

		@Override
		public boolean allowInsert( IItemHandler inv, int slot, ItemStack stack )
		{
			return !stack.isEmpty() && stack.getItem() instanceof ItemEncodedPattern;
		}
	}
}
