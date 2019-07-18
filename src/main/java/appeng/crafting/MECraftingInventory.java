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


import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.inv.ItemListIgnoreCrafting;
import appeng.util.item.MixedList;
import com.sun.org.apache.bcel.internal.generic.ISTORE;

import java.util.HashMap;
import java.util.Map;


public class MECraftingInventory implements IMEInventory
{

	private final MECraftingInventory par;

	private final IMEInventory target;
	private final Map<IStorageChannel, IItemList<IAEStack>> localCache;

	private final boolean logExtracted;
	private final Map<IStorageChannel, IItemList<IAEStack>> extractedCache;

	private final boolean logInjections;
	private final Map<IStorageChannel, IItemList<IAEStack>> injectedCache;

	private final boolean logMissing;
	private final Map<IStorageChannel, IItemList<IAEStack>> missingCache;

	public MECraftingInventory()
	{
		this.localCache = new HashMap<>();
		this.extractedCache = null;
		this.injectedCache = null;
		this.missingCache = null;
		this.logExtracted = false;
		this.logInjections = false;
		this.logMissing = false;
		this.target = null;
		this.par = null;
	}

	public MECraftingInventory( final MECraftingInventory parent )
	{
		this.target = parent;
		this.logExtracted = parent.logExtracted;
		this.logInjections = parent.logInjections;
		this.logMissing = parent.logMissing;
		this.localCache = new HashMap<>();

		if( this.logMissing )
		{
			this.missingCache = new HashMap<>();
		}
		else
		{
			this.missingCache = null;
		}

		if( this.logExtracted )
		{
			this.extractedCache = new HashMap<>();
		}
		else
		{
			this.extractedCache = null;
		}

		if( this.logInjections )
		{
			this.injectedCache = new HashMap<>();
		}
		else
		{
			this.injectedCache = null;
		}

		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			this.localCache.put(channel, this.target.getAvailableItems( channel, new ItemListIgnoreCrafting<>( channel.createList() ) ) );

		this.par = parent;
	}

	public MECraftingInventory( final IMEMonitor target, final IActionSource src, final boolean logExtracted, final boolean logInjections, final boolean logMissing )
	{
		this.target = target;
		this.logExtracted = logExtracted;
		this.logInjections = logInjections;
		this.logMissing = logMissing;

		if( logMissing )
		{
			this.missingCache = new HashMap<>();
		}
		else
		{
			this.missingCache = null;
		}

		if( logExtracted )
		{
			this.extractedCache = new HashMap<>();
		}
		else
		{
			this.extractedCache = null;
		}

		if( logInjections )
		{
			this.injectedCache = new HashMap<>();
		}
		else
		{
			this.injectedCache = null;
		}

		this.localCache = new HashMap<>();
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
		{
			ItemListIgnoreCrafting list = new ItemListIgnoreCrafting<>(channel.createList());
			for( final IAEStack is : target.getStorageList(channel) )
			{
				IAEStack result = target.extractItems( is, Actionable.SIMULATE, src );
			}
			this.localCache.put(channel, list);
		}
		this.par = null;
	}

	public MECraftingInventory( final IMEInventory target, final boolean logExtracted, final boolean logInjections, final boolean logMissing )
	{
		this.target = target;
		this.logExtracted = logExtracted;
		this.logInjections = logInjections;
		this.logMissing = logMissing;

		if( logMissing )
		{
			this.missingCache = new HashMap<>();
		}
		else
		{
			this.missingCache = null;
		}

		if( logExtracted )
		{
			this.extractedCache = new HashMap<>();
		}
		else
		{
			this.extractedCache = null;
		}

		if( logInjections )
		{
			this.injectedCache = new HashMap<>();
		}
		else
		{
			this.injectedCache = null;
		}

		this.localCache = new HashMap<>();
		for(IStorageChannel channel : AEApi.instance().storage().storageChannels())
			this.localCache.put( channel, target.getAvailableItems( channel, channel.createList() ) );

		this.par = null;
	}

	@Override
	public IAEStack injectItems(final IAEStack input, final Actionable mode, final IActionSource src )
	{
		if( input == null )
		{
			return null;
		}

		if( mode == Actionable.MODULATE )
		{
			IStorageChannel channel = input.getChannel();
			if( this.logInjections )
			{
				IItemList<IAEStack> injCache = this.injectedCache.get(channel);
				if(injCache  == null)
					this.injectedCache.put(channel, injCache  = channel.createList());
				injCache .add( input );
			}

			IItemList<IAEStack> locCache = this.localCache.get(channel);
			if(locCache == null)
				this.localCache.put(channel, locCache = channel.createList());
			locCache.add(input);
		}

		return null;
	}

	@Override
	public IAEStack extractItems( final IAEStack request, final Actionable mode, final IActionSource src )
	{
		if( request == null )
		{
			return null;
		}

		IStorageChannel channel = request.getChannel();
		IItemList<IAEStack> locCache = this.localCache.get(channel);
		IAEStack list = null;
		if(locCache != null)
			list = locCache.findPrecise(request);

		if( list == null || list.getStackSize() == 0 )
		{
			return null;
		}

		if( list.getStackSize() >= request.getStackSize() )
		{
			if( mode == Actionable.MODULATE )
			{
				list.decStackSize( request.getStackSize() );
				if( this.logExtracted )
				{
					IItemList<IAEStack> extCache = this.extractedCache.get(channel);
					if(extCache == null)
						this.extractedCache.put(channel, extCache = channel.createList());
					extCache.add( request );
				}
			}

			return request;
		}

		final IAEStack ret = request.copy();
		ret.setStackSize( list.getStackSize() );

		if( mode == Actionable.MODULATE )
		{
			list.reset();
			if( this.logExtracted )
			{
				IItemList<IAEStack> extCache = this.extractedCache.get(channel);
				if(extCache == null)
					this.extractedCache.put(channel, extCache = channel.createList());
				extCache.add( ret );
			}
		}

		return ret;
	}

	@Override
	public  IItemList<IAEStack> getAvailableItems(IStorageChannel channel, final IItemList<IAEStack> out )
	{

		IItemList<IAEStack> locCache = this.localCache.get(channel);
		if(locCache != null)
			for( final IAEStack is : locCache)
			{
				out.add( is );
			}

		return out;
	}

	public IItemList<IAEStack> getItemList(IStorageChannel channel)
	{
		return this.localCache.get(channel);
	}

	public boolean commit( final IActionSource src )
	{
		final IItemList<IAEStack> added = new MixedList();
		final IItemList<IAEStack> pulled = new MixedList();
		boolean failed = false;

		if( this.logInjections )
		{
			for( IStorageChannel channel : this.injectedCache.keySet())
				for( final IAEStack inject : this.injectedCache.get(channel) )
				{
					IAEStack result = null;
					added.add( result = this.target.injectItems( inject, Actionable.MODULATE, src ) );

					if( result != null )
					{
						failed = true;
						break;
					}
				}
		}

		if( failed )
		{
			for( final IAEStack is : added )
			{
				this.target.extractItems( is, Actionable.MODULATE, src );
			}

			return false;
		}

		if( this.logExtracted )
		{
			for( IStorageChannel channel : this.extractedCache.keySet())
				for( final IAEStack extra : this.extractedCache.get(channel) )
				{
					IAEStack result = null;
					pulled.add( result = this.target.extractItems( extra, Actionable.MODULATE, src ) );

					if( result == null || result.getStackSize() != extra.getStackSize() )
					{
						failed = true;
						break;
					}
				}
		}

		if( failed )
		{
			for( final IAEStack is : added )
			{
				this.target.extractItems( is, Actionable.MODULATE, src );
			}

			for( final IAEStack is : pulled )
			{
				this.target.injectItems( is, Actionable.MODULATE, src );
			}

			return false;
		}

		if( this.logMissing && this.par != null )
		{

			for(IStorageChannel channel : this.missingCache.keySet())
				for( final IAEStack extra : this.missingCache.get(channel) )
				{
					this.par.addMissing( extra );
				}
		}

		return true;
	}

	private void addMissing( final IAEStack extra )
	{
		IStorageChannel channel = extra.getChannel();
		IItemList<IAEStack> misCache = this.missingCache.get(channel);
		if(misCache == null)
			this.missingCache.put(channel, misCache = channel.createList());
		misCache.add( extra );
	}

	void ignore( final IAEStack what )
	{
		IStorageChannel channel = what.getChannel();
		IItemList locCache = this.localCache.get(channel);
		if(locCache != null)
		{
			final IAEStack list = locCache.findPrecise( what );
			if( list != null )
			{
				list.setStackSize( 0 );
			}
		}
	}
}
