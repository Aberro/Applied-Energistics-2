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

package appeng.me.storage;


import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEStack;


public class DriveWatcher extends MEInventoryHandler
{

	private int oldStatus = 0;
	private final ItemStack is;
	private final ICellHandler handler;
	private final IChestOrDrive cord;

	public DriveWatcher( final ICellInventoryHandler i, final ItemStack is, final ICellHandler han, final IChestOrDrive cod )
	{
		super( i );
		this.is = is;
		this.handler = han;
		this.cord = cod;
	}

	public int getStatus()
	{
		return this.handler.getStatusForCell( this.is, (ICellInventoryHandler) this.getInternal() );
	}

	@Override
	public IAEStack injectItems( final IAEStack input, final Actionable type, final IActionSource src )
	{
		final long size = input.getStackSize();

		final IAEStack a = super.injectItems( input, type, src );

		if( type == Actionable.MODULATE && ( a == null || a.getStackSize() != size ) )
		{
			final int newStatus = this.getStatus();

			if( newStatus != this.oldStatus )
			{
				this.cord.blinkCell( this.getSlot() );
				this.oldStatus = newStatus;
			}
		}

		return a;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable type, final IActionSource src )
	{
		final IAEStack a = super.extractItems( request, type, src );

		if( type == Actionable.MODULATE && a != null )
		{
			final int newStatus = this.getStatus();

			if( newStatus != this.oldStatus )
			{
				this.cord.blinkCell( this.getSlot() );
				this.oldStatus = newStatus;
			}
		}

		return a;
	}

	public IStorageChannel getChannel()
	{
		IMEInventory i = getInternal();

		if(i instanceof ICellInventoryHandler)
			return ((ICellInventoryHandler)i).getChannel();
		//TODO maybe throw exception? This should be wrong case...
		return null;
	}
}
