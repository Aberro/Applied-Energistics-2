/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.container;


import java.util.Iterator;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.util.IAEFluidTank;
import appeng.util.Platform;
import appeng.util.iterators.NullIterator;


/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class ContainerFluidStorageBus extends ContainerFluidConfigurable
{

	private final PartFluidStorageBus storageBus;

	@GuiSync( 3 )
	public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

	@GuiSync( 4 )
	public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

	public ContainerFluidStorageBus( InventoryPlayer ip, PartFluidStorageBus te )
	{
		super( ip, te );
		this.storageBus = te;
	}

	@Override
	protected int getHeight()
	{
		return 251;
	}

	@Override
	protected void setupConfig()
	{
		final IItemHandler upgrades = this.getUpgradeable().getInventoryByName( "upgrades" );
		this.addSlotToContainer( ( new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0, 187, 8, this.getInventoryPlayer() ) )
				.setNotDraggable() );
		this.addSlotToContainer(
				( new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 8 + 18, this.getInventoryPlayer() ) )
						.setNotDraggable() );
		this.addSlotToContainer(
				( new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 8 + 18 * 2, this.getInventoryPlayer() ) )
						.setNotDraggable() );
		this.addSlotToContainer(
				( new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 8 + 18 * 3, this.getInventoryPlayer() ) )
						.setNotDraggable() );
		this.addSlotToContainer(
				( new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 4, 187, 8 + 18 * 4, this.getInventoryPlayer() ) )
						.setNotDraggable() );
	}

	@Override
	protected boolean isValidForConfig( int slot, IAEFluidStack fs )
	{
		if( this.supportCapacity() )
		{
			final int upgrades = this.getUpgradeable().getInstalledUpgrades( Upgrades.CAPACITY );

			final int y = slot / 9;

			if( y >= upgrades + 2 )
			{
				return false;
			}
		}

		return true;
	}

	@Override
	protected boolean supportCapacity()
	{
		return true;
	}

	@Override
	public int availableUpgrades()
	{
		return 5;
	}

	@Override
	public void detectAndSendChanges()
	{
		this.verifyPermissions( SecurityPermissions.BUILD, false );

		if( Platform.isServer() )
		{
			this.setFuzzyMode( (FuzzyMode) this.getUpgradeable().getConfigManager().getSetting( Settings.FUZZY_MODE ) );
			this.setReadWriteMode( (AccessRestriction) this.getUpgradeable().getConfigManager().getSetting( Settings.ACCESS ) );
			this.setStorageFilter( (StorageFilter) this.getUpgradeable().getConfigManager().getSetting( Settings.STORAGE_FILTER ) );
		}

		this.standardDetectAndSendChanges();
	}

	@Override
	public boolean isSlotEnabled( final int idx )
	{
		final int upgrades = this.getUpgradeable().getInstalledUpgrades( Upgrades.CAPACITY );

		return upgrades > idx;
	}

	public void clear()
	{
		IAEFluidTank h = this.storageBus.getConfig();
		for( int i = 0; i < h.getSlots(); ++i )
		{
			h.setFluidInSlot( i, null );
		}
		this.detectAndSendChanges();
	}

	public void partition()
	{
		IAEFluidTank h = this.storageBus.getConfig();

		final IMEInventory cellInv = this.storageBus.getInternalHandler();

		Iterator<IAEStack> i = new NullIterator<>();
		if( cellInv != null )
		{
			IStorageChannel channel = AEApi.instance().storage().getStorageChannel( IFluidStorageChannel.class );
			final IItemList<IAEStack> list = cellInv
					.getAvailableItems( channel, channel.createList() );
			i = list.iterator();
		}

		for( int x = 0; x < h.getSlots(); x++ )
		{
			if( i.hasNext() && this.isSlotEnabled( ( x / 9 ) - 2 ) )
			{
				h.setFluidInSlot( x, (IAEFluidStack)i.next() );
			}
			else
			{
				h.setFluidInSlot( x, null );
			}
		}
		this.detectAndSendChanges();
	}

	public AccessRestriction getReadWriteMode()
	{
		return this.rwMode;
	}

	private void setReadWriteMode( final AccessRestriction rwMode )
	{
		this.rwMode = rwMode;
	}

	public StorageFilter getStorageFilter()
	{
		return this.storageFilter;
	}

	private void setStorageFilter( final StorageFilter storageFilter )
	{
		this.storageFilter = storageFilter;
	}

	@Override
	public IAEFluidTank getFluidConfigInventory()
	{
		return this.storageBus.getConfig();
	}
}
