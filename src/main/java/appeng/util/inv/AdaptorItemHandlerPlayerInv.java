/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2017, AlgorithmX2, All rights reserved.
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

package appeng.util.inv;


import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;


public class AdaptorItemHandlerPlayerInv extends AdaptorItemHandler
{
	public AdaptorItemHandlerPlayerInv( final EntityPlayer playerInv )
	{
		super( new PlayerMainInvWrapper( playerInv.inventory ) );
	}

	/**
	 * Tries to fill existing stacks first
	 */
	@Override
	protected IAEStack addItems(final IAEStack itemsToAdd, final boolean simulate )
	{
		if( itemsToAdd == null || itemsToAdd.isEmpty())
		{
			return null;
		}
		if(itemsToAdd.getChannel() != AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ))
			return itemsToAdd;

		IAEStack left = itemsToAdd.copy();

		for( int slot = 0; slot < this.itemHandler.getSlots(); slot++ )
		{
			IAEStack is = AEItemStack.fromItemStack( this.itemHandler.getStackInSlot( slot ) );

			if( is.equals( left ) )
			{
				left = AEItemStack.fromItemStack( this.itemHandler.insertItem( slot, (ItemStack) left.getStack(), simulate ) );
			}
			if( left.isEmpty() )
			{
				return null;
			}
		}

		for( int slot = 0; slot < this.itemHandler.getSlots(); slot++ )
		{
			left = AEItemStack.fromItemStack( this.itemHandler.insertItem( slot, (ItemStack)left.getStack(), simulate ) );
			if( left.isEmpty() )
			{
				return null;
			}
		}

		return left;
	}

}
