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


import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.IPartitionList;


public class MEInventoryHandler implements IMEInventoryHandler
{

	private final IMEInventoryHandler internal;
	private int myPriority;
	private IncludeExclude myWhitelist;
	private AccessRestriction myAccess;
	private IPartitionList myPartitionList;

	private AccessRestriction cachedAccessRestriction;
	private boolean hasReadAccess;
	private boolean hasWriteAccess;

	public MEInventoryHandler( final IMEInventory i )
	{
		if( i instanceof IMEInventoryHandler )
		{
			this.internal = (IMEInventoryHandler) i;
		}
		else
		{
			this.internal = new MEPassThrough( i );
		}

		this.myPriority = 0;
		this.myWhitelist = IncludeExclude.WHITELIST;
		this.setBaseAccess( AccessRestriction.READ_WRITE );
		this.myPartitionList = new DefaultPriorityList();
	}

	IncludeExclude getWhitelist()
	{
		return this.myWhitelist;
	}

	public void setWhitelist( final IncludeExclude myWhitelist )
	{
		this.myWhitelist = myWhitelist;
	}

	public AccessRestriction getBaseAccess()
	{
		return this.myAccess;
	}

	public void setBaseAccess( final AccessRestriction myAccess )
	{
		this.myAccess = myAccess;
		this.cachedAccessRestriction = this.myAccess.restrictPermissions( this.internal.getAccess() );
		this.hasReadAccess = this.cachedAccessRestriction.hasPermission( AccessRestriction.READ );
		this.hasWriteAccess = this.cachedAccessRestriction.hasPermission( AccessRestriction.WRITE );
	}

	IPartitionList getPartitionList()
	{
		return this.myPartitionList;
	}

	public void setPartitionList( final IPartitionList myPartitionList )
	{
		this.myPartitionList = myPartitionList;
	}

	@Override
	public IAEStack injectItems( final IAEStack input, final Actionable type, final IActionSource src )
	{
		if( !this.canAccept( input ) )
		{
			return input;
		}

		return this.internal.injectItems( input, type, src );
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable type, final IActionSource src )
	{
		if( !this.hasReadAccess )
		{
			return null;
		}

		return this.internal.extractItems( request, type, src );
	}

	@Override
	public IItemList<IAEStack> getAvailableItems(IStorageChannel channel, final IItemList<IAEStack> out )
	{
		if( !this.hasReadAccess )
		{
			return out;
		}

		return this.internal.getAvailableItems( channel, out );
	}

	@Override
	public AccessRestriction getAccess()
	{
		return this.cachedAccessRestriction;
	}

	@Override
	public boolean isPrioritized( final IAEStack input )
	{
		if( this.myWhitelist == IncludeExclude.WHITELIST )
		{
			return this.myPartitionList.isListed( input ) || this.internal.isPrioritized( input );
		}
		return false;
	}

	@Override
	public boolean canAccept( final IAEStack input )
	{
		if( !this.hasWriteAccess )
		{
			return false;
		}

		if( this.myWhitelist == IncludeExclude.BLACKLIST && this.myPartitionList.isListed( input ) )
		{
			return false;
		}
		if( this.myPartitionList.isEmpty() || this.myWhitelist == IncludeExclude.BLACKLIST )
		{
			return this.internal.canAccept( input );
		}
		return this.myPartitionList.isListed( input ) && this.internal.canAccept( input );
	}

	@Override
	public int getPriority()
	{
		return this.myPriority;
	}

	public void setPriority( final int myPriority )
	{
		this.myPriority = myPriority;
	}

	@Override
	public int getSlot()
	{
		return this.internal.getSlot();
	}

	@Override
	public boolean validForPass( final int i )
	{
		return true;
	}

	public IMEInventory getInternal()
	{
		return this.internal;
	}
}
