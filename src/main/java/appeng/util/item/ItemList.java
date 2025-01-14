/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.util.item;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import appeng.api.storage.data.IAEStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AESharedItemStack.Bounds;

public final class ItemList implements IItemList<IAEStack>
{

	private final NavigableMap<AESharedItemStack, IAEItemStack> records = new ConcurrentSkipListMap<>();

	@Override
	public void add( final IAEStack option )
	{
		if( option == null || !(option instanceof IAEItemStack))
		{
			return;
		}

		final IAEItemStack st = this.records.get( ( (AEItemStack) option ).getSharedStack() );

		if( st != null )
		{
			st.add( option );
			return;
		}

		final IAEItemStack opt = (IAEItemStack)option.copy();

		this.putItemRecord( opt );
	}

	@Override
	public IAEItemStack findPrecise( final IAEStack itemStack )
	{
		if( itemStack == null || !(itemStack instanceof IAEItemStack))
		{
			return null;
		}

		return this.records.get( ( (AEItemStack) itemStack ).getSharedStack() );
	}

	@Override
	public Collection<IAEStack> findFuzzy( final IAEStack filter, final FuzzyMode fuzzy )
	{
		if( filter == null || !(filter instanceof IAEItemStack))
		{
			return Collections.emptyList();
		}

		final AEItemStack ais = (AEItemStack) filter;

		return ais.getOre().map( or ->
		{
			if( or.getAEEquivalents().size() == 1 )
			{
				final IAEItemStack is = or.getAEEquivalents().get( 0 );

				return this.findFuzzyDamage( is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE );
			}
			else
			{
				final Collection<IAEStack> output = new ArrayList<>();

				for( final IAEItemStack is : or.getAEEquivalents() )
				{
					output.addAll( this.findFuzzyDamage( is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE ) );
				}

				return output;
			}
		} ).orElse( this.findFuzzyDamage( ais, fuzzy, false ) );
	}

	@Override
	public boolean isEmpty()
	{
		return !this.iterator().hasNext();
	}

	@Override
	public void addStorage( final IAEStack option )
	{
		if( option == null || !(option instanceof IAEItemStack))
		{
			return;
		}

		final IAEItemStack st = this.records.get( ( (AEItemStack) option ).getSharedStack() );

		if( st != null )
		{
			st.incStackSize( option.getStackSize() );
			return;
		}

		final IAEItemStack opt = (IAEItemStack)option.copy();

		this.putItemRecord( opt );
	}

	/*
	 * public void clean() { Iterator<StackType> i = iterator(); while (i.hasNext()) { StackType AEI =
	 * i.next(); if ( !AEI.isMeaningful() ) i.remove(); } }
	 */

	@Override
	public void addCrafting( final IAEStack option )
	{
		if( option == null || !(option instanceof IAEItemStack))
		{
			return;
		}

		final IAEItemStack st = this.records.get( ( (AEItemStack) option ).getSharedStack() );

		if( st != null )
		{
			st.setCraftable( true );
			return;
		}

		final IAEItemStack opt = (IAEItemStack) option.copy();
		opt.setStackSize( 0 );
		opt.setCraftable( true );

		this.putItemRecord( opt );
	}

	@Override
	public void addRequestable( final IAEStack option )
	{
		if( option == null || !(option instanceof IAEItemStack) )
		{
			return;
		}

		final IAEItemStack st = this.records.get( ( (AEItemStack) option ).getSharedStack() );

		if( st != null )
		{
			st.setCountRequestable( st.getCountRequestable() + option.getCountRequestable() );
			return;
		}

		final IAEItemStack opt = (IAEItemStack)option.copy();
		opt.setStackSize( 0 );
		opt.setCraftable( false );
		opt.setCountRequestable( option.getCountRequestable() );

		this.putItemRecord( opt );
	}

	@Override
	public IAEStack getFirstItem()
	{
		for( final IAEStack stackType : this )
		{
			return stackType;
		}

		return null;
	}

	@Override
	public int size()
	{
		return this.records.size();
	}

	@Override
	public Iterator<IAEStack> iterator()
	{
		return new MeaningfulItemIterator<>( this.records.values().stream().map(x -> (IAEStack)x).iterator() );
	}

	@Override
	public void resetStatus()
	{
		for( final IAEStack i : this )
		{
			i.reset();
		}
	}

	private IAEItemStack putItemRecord( final IAEItemStack itemStack )
	{
		return this.records.put( ( (AEItemStack) itemStack ).getSharedStack(), itemStack );
	}

	private Collection<IAEStack> findFuzzyDamage( final IAEStack filter, final FuzzyMode fuzzy, final boolean ignoreMeta )
	{
		final AEItemStack itemStack = (AEItemStack) filter;
		final Bounds bounds = itemStack.getSharedStack().getBounds( fuzzy, ignoreMeta );

		return this.records.subMap( bounds.lower(), true, bounds.upper(), true )
				.descendingMap().values()
				.stream().map(x -> (IAEStack)x).collect(Collectors.toCollection(ArrayList::new));
	}
}
