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


import appeng.api.storage.data.IAEStack;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.items.IBiometricCard;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.tile.misc.TileSecurityStation;

import java.util.HashMap;
import java.util.Map;


public class SecurityStationInventory implements IMEInventoryHandler
{

	private final Map<IStorageChannel, IItemList<IAEStack>> storedItems = new HashMap<>();
	private final TileSecurityStation securityTile;

	public SecurityStationInventory( final TileSecurityStation ts )
	{
		this.securityTile = ts;
	}

	@Override
	public IAEStack injectItems( final IAEStack input, final Actionable type, final IActionSource src )
	{
		if( this.hasPermission( src ) )
		{
			if( input.getChannel() == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
					&& AEApi.instance().definitions().items().biometricCard().isSameAs( ((IAEItemStack)input).getItemStack() ) )
			{
				if( this.canAccept( input ) )
				{
					if( type == Actionable.SIMULATE )
					{
						return null;
					}

					this.getStoredItems(this.getChannel()).add( input );
					this.securityTile.inventoryChanged();
					return null;
				}
			}
		}
		return input;
	}

	private boolean hasPermission( final IActionSource src )
	{
		if( src.player().isPresent() )
		{
			try
			{
				return this.securityTile.getProxy().getSecurity().hasPermission( src.player().get(), SecurityPermissions.SECURITY );
			}
			catch( final GridAccessException e )
			{
				// :P
			}
		}
		return false;
	}

	@Override
	public IAEStack extractItems(final IAEStack request, final Actionable mode, final IActionSource src )
	{
		if( this.hasPermission( src ) )
		{
			final IAEStack target = this.getStoredItems(request.getChannel()).findPrecise( request );
			if( target != null )
			{
				final IAEStack output = target.copy();

				if( mode == Actionable.SIMULATE )
				{
					return output;
				}

				target.setStackSize( 0 );
				this.securityTile.inventoryChanged();
				return output;
			}
		}
		return null;
	}

	@Override
	public IItemList<IAEStack> getAvailableItems( IStorageChannel channel, final IItemList<IAEStack> out )
	{
		for( final IAEStack ais : this.getStoredItems(channel) )
		{
			out.add( ais );
		}

		return out;
	}

	public IStorageChannel getChannel()
	{
		return AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class );
	}

	@Override
	public AccessRestriction getAccess()
	{
		return AccessRestriction.READ_WRITE;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		return false;
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		if(input.getChannel() != this.getChannel())
			return false;
		if( ((IAEItemStack)input).getItem() instanceof IBiometricCard )
		{
			final IBiometricCard tbc = (IBiometricCard) ((IAEItemStack)input).getItem();
			final GameProfile newUser = tbc.getProfile( ((IAEItemStack)input).getItemStack() );

			final int PlayerID = AEApi.instance().registries().players().getID( newUser );
			if( this.securityTile.getOwner() == PlayerID )
			{
				return false;
			}

			for( final IAEStack ais : this.getStoredItems(this.getChannel()) )
			{
				if( ais.isMeaningful() )
				{
					final GameProfile thisUser = tbc.getProfile( ((IAEItemStack)ais).getItemStack() );
					if( thisUser == newUser )
					{
						return false;
					}

					if( thisUser != null && thisUser.equals( newUser ) )
					{
						return false;
					}
				}
			}

			return true;
		}
		return false;
	}

	@Override
	public int getPriority()
	{
		return 0;
	}

	@Override
	public int getSlot()
	{
		return 0;
	}

	@Override
	public boolean validForPass( final int i )
	{
		return true;
	}

	public IItemList<IAEStack> getStoredItems(IStorageChannel channel)
	{
		IItemList<IAEStack> result = this.storedItems.get(channel);
		if(result == null)
			this.storedItems.put(channel, result = channel.createList());
		return  result;
	}
}
