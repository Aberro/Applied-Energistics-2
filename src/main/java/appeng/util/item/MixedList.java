package appeng.util.item;


import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.util.MeaningfulFluidIterator;

import java.util.*;

public final class MixedList implements IItemList<IAEStack>
{
    private final Map<IAEStack, IAEStack> records = new HashMap<>();

    @Override
    public void add( final IAEStack option )
    {
        if( option == null )
            return;

        final IAEStack st = this.getRecord( option );

        if( st != null )
        {
            st.add( option );
            return;
        }

        final IAEStack opt = option.copy();

        this.putRecord( opt );
    }

    @Override
    public IAEStack findPrecise( final IAEStack stack )
    {
        if( stack == null )
        {
            return null;
        }

        return this.getRecord( stack );
    }

    @Override
    public Collection<IAEStack> findFuzzy(final IAEStack filter, final FuzzyMode fuzzy )
    {
        if( filter == null )
        {
            return Collections.emptyList();
        }

        return Collections.singletonList( this.findPrecise( filter ) );
    }

    @Override
    public boolean isEmpty()
    {
        return !this.iterator().hasNext();
    }

    @Override
    public void addStorage( final IAEStack option )
    {
        if( option == null )
        {
            return;
        }

        final IAEStack st = this.getRecord( option );

        if( st != null )
        {
            st.incStackSize( option.getStackSize() );
            return;
        }

        final IAEStack opt = option.copy();

        this.putRecord( opt );
    }

    @Override
    public void addCrafting( final IAEStack option )
    {
        if( option == null )
        {
            return;
        }

        final IAEStack st = this.getRecord( option );

        if( st != null )
        {
            st.setCraftable( true );
            return;
        }

        final IAEStack opt = option.copy();
        opt.setStackSize( 0 );
        opt.setCraftable( true );

        this.putRecord( opt );
    }

    @Override
    public void addRequestable( final IAEStack option )
    {
        if( option == null )
        {
            return;
        }

        final IAEStack st = this.getRecord( option );

        if( st != null )
        {
            st.setCountRequestable( st.getCountRequestable() + option.getCountRequestable() );
            return;
        }

        final IAEStack opt = option.copy();
        opt.setStackSize( 0 );
        opt.setCraftable( false );
        opt.setCountRequestable( option.getCountRequestable() );

        this.putRecord( opt );
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
        return this.records.values().size();
    }

    @Override
    public Iterator<IAEStack> iterator()
    {
        return new MeaningfulFluidIterator<>( this.records.values().iterator() );
    }

    @Override
    public void resetStatus()
    {
        for( final IAEStack i : this )
        {
            i.reset();
        }
    }

    private IAEStack getRecord( final IAEStack item )
    {
        return this.records.get( item );
    }

    private IAEStack putRecord( final IAEStack item )
    {
        return this.records.put( item, item );
    }
}