
package appeng.me.storage;


import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.util.item.AEStack;


public class BasicCellInventory<T extends IAEStack> extends AbstractCellInventory<T>
{
	private final IStorageChannel<T> channel;

	private BasicCellInventory( final IStorageCell<T> cellType, final ItemStack o, final ISaveProvider container )
	{
		super( cellType, o, container );
		this.channel = cellType.getChannel();
	}

	public static <T extends IAEStack> ICellInventory<T> createInventory( final ItemStack o, final ISaveProvider container )
	{
		try
		{
			if( o == null )
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			final Item type = o.getItem();
			final IStorageCell<T> cellType;
			if( type instanceof IStorageCell )
			{
				cellType = (IStorageCell<T>) type;
			}
			else
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			if( !cellType.isStorageCell( o ) )
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			return new BasicCellInventory<T>( cellType, o, container );
		}
		catch( final AppEngException e )
		{
			AELog.error( e );
			return null;
		}
	}

	public static <T extends AEStack<T>> boolean isCellOfType( final ItemStack input, IStorageChannel<?> channel )
	{
		final IStorageCell<?> type = getStorageCell( input );

		return type != null && type.getChannel() == channel;
	}

	public static boolean isCell( final ItemStack input )
	{
		return getStorageCell( input ) != null;
	}

	private boolean isStorageCell( final T input )
	{
		if( input instanceof IAEItemStack )
		{
			final IAEItemStack stack = (IAEItemStack) input;
			final IStorageCell<?> type = getStorageCell( stack.getDefinition() );

			return type != null && !type.storableInStorageCell();
		}

		return false;
	}

	private static IStorageCell<?> getStorageCell( final ItemStack input )
	{
		if( input != null )
		{
			final Item type = input.getItem();

			if( type instanceof IStorageCell )
			{
				return (IStorageCell<?>) type;
			}
		}

		return null;
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private static boolean isCellEmpty( ICellInventory inv )
	{
		if( inv != null )
		{
			IStorageChannel channel = inv.getChannel();
			return inv.getAvailableItems( channel, channel.createList() ).isEmpty();
		}
		return true;
	}

	@Override
	public IAEStack injectItems( IAEStack input, Actionable mode, IActionSource src )
	{
		if( input == null )
			return null;
		if( input.getStackSize() == 0 )
			return null;
		if(input.getChannel() != this.getChannel())
			return input;

		if( this.cellType.isBlackListed( this.getItemStack(), (T)input ) )
			return input;
		// This is slightly hacky as it expects a read-only access, but fine for now.
		// TODO: Guarantee a read-only access. E.g. provide an isEmpty() method and ensure CellInventory does not write
		// any NBT data for empty cells instead of relying on an empty IItemContainer
		if( this.isStorageCell( (T)input ) )
		{
			final ICellInventory<?> meInventory = createInventory( ( (IAEItemStack) input ).createItemStack(), null );
			if( !isCellEmpty( meInventory ) )
			{
				return input;
			}
		}

		final T l = this.getCellItems().findPrecise( (T)input );
		if( l != null )
		{
			final long remainingItemCount = this.getRemainingItemCount();
			if( remainingItemCount <= 0 )
			{
				return input;
			}

			if( input.getStackSize() > remainingItemCount )
			{
				final T r = (T)input.copy();
				r.setStackSize( r.getStackSize() - remainingItemCount );
				if( mode == Actionable.MODULATE )
				{
					l.setStackSize( l.getStackSize() + remainingItemCount );
					this.saveChanges();
				}
				return r;
			}
			else
			{
				if( mode == Actionable.MODULATE )
				{
					l.setStackSize( l.getStackSize() + input.getStackSize() );
					this.saveChanges();
				}
				return null;
			}
		}

		if( this.canHoldNewItem() ) // room for new type, and for at least one item!
		{
			final int remainingItemCount = (int) this.getRemainingItemCount() - this.getBytesPerType() * this.itemsPerByte;
			if( remainingItemCount > 0 )
			{
				if( input.getStackSize() > remainingItemCount )
				{
					final T toReturn = (T)input.copy();
					toReturn.setStackSize( input.getStackSize() - remainingItemCount );
					if( mode == Actionable.MODULATE )
					{
						final T toWrite = (T)input.copy();
						toWrite.setStackSize( remainingItemCount );

						this.cellItems.add( toWrite );
						this.saveChanges();
					}
					return toReturn;
				}

				if( mode == Actionable.MODULATE )
				{
					this.cellItems.add( (T)input );
					this.saveChanges();
				}

				return null;
			}
		}

		return input;
	}

	@Override
	public IAEStack extractItems( IAEStack request, Actionable mode, IActionSource src )
	{
		if( request == null )
			return null;
		if( request.getChannel() != this.getChannel())
			return null;

		final long size = Math.min( Integer.MAX_VALUE, request.getStackSize() );

		T Results = null;

		final T l = this.getCellItems().findPrecise( (T)request );
		if( l != null )
		{
			Results = (T)l.copy();

			if( l.getStackSize() <= size )
			{
				Results.setStackSize( l.getStackSize() );
				if( mode == Actionable.MODULATE )
				{
					l.setStackSize( 0 );
					this.saveChanges();
				}
			}
			else
			{
				Results.setStackSize( size );
				if( mode == Actionable.MODULATE )
				{
					l.setStackSize( l.getStackSize() - size );
					this.saveChanges();
				}
			}
		}

		return Results;
	}

	@Override
	public IStorageChannel<T> getChannel()
	{
		return this.channel;
	}

	@Override
	protected boolean loadCellItem( NBTTagCompound compoundTag, int stackSize )
	{
		// Now load the item stack
		final T t;
		try
		{
			t = this.getChannel().createFromNBT( compoundTag );
			if( t == null )
			{
				AELog.warn( "Removing item " + compoundTag + " from storage cell because the associated item type couldn't be found." );
				return false;
			}
		}
		catch( Throwable ex )
		{
			if( AEConfig.instance().isRemoveCrashingItemsOnLoad() )
			{
				AELog.warn( ex, "Removing item " + compoundTag + " from storage cell because loading the ItemStack crashed." );
				return false;
			}
			throw ex;
		}

		t.setStackSize( stackSize );

		if( stackSize > 0 )
		{
			this.cellItems.add( t );
		}

		return true;
	}
}
