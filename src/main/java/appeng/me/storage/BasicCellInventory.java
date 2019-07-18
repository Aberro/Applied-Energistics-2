
package appeng.me.storage;


import appeng.api.util.ISlot;
import appeng.util.item.AEItemStack;
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


public class BasicCellInventory<TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack> extends AbstractCellInventory<TAEStack, TSlot, TStack>
{
	private final IStorageChannel channel;

	private BasicCellInventory(IStorageChannel channel, final IStorageCell<TAEStack, TSlot, TStack> cellType, final ItemStack o, final ISaveProvider container )
	{
		super( channel, cellType, o, container );
		this.channel = cellType.getChannel();
	}

	public static <TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack> ICellInventory<TAEStack, TSlot, TStack> createInventory( IStorageChannel channel, final ItemStack o, final ISaveProvider container )
	{
		try
		{
			if( o == null )
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			final Item type = o.getItem();
			final IStorageCell<TAEStack, TSlot, TStack> cellType;
			if( type instanceof IStorageCell )
			{
				cellType = (IStorageCell<TAEStack, TSlot, TStack>) type;
			}
			else
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			if( !cellType.isStorageCell( o ) )
			{
				throw new AppEngException( "ItemStack was used as a cell, but was not a cell!" );
			}

			return new BasicCellInventory( channel, cellType, o, container );
		}
		catch( final AppEngException e )
		{
			AELog.error( e );
			return null;
		}
	}

	public static boolean isCellOfType( final ItemStack input, IStorageChannel channel )
	{
		final IStorageCell type = getStorageCell( input );

		return type != null && type.getChannel() == channel;
	}

	public static boolean isCell( final ItemStack input, IStorageChannel channel )
	{
		IStorageCell cell = getStorageCell( input );
		return cell != null && cell.getChannel() == channel;
	}

	private boolean isStorageCell( final TAEStack input )
	{
		if( input instanceof IAEItemStack )
		{
			final IAEItemStack stack = (IAEItemStack) input;
			final IStorageCell type = getStorageCell( stack.getDefinition() );

			return type != null && !type.storableInStorageCell();
		}

		return false;
	}

	private static IStorageCell getStorageCell( final ItemStack input )
	{
		if( input != null )
		{
			final Item type = input.getItem();

			if( type instanceof IStorageCell )
			{
				return (IStorageCell) type;
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

		if( this.cellType.isBlackListed( this.getStack(), (TAEStack)input ) )
			return input;
		// This is slightly hacky as it expects a read-only access, but fine for now.
		// TODO: Guarantee a read-only access. E.g. provide an isEmpty() method and ensure CellInventory does not write
		// any NBT data for empty cells instead of relying on an empty IItemContainer
		IStorageCell cell = this.getStorageCell((ItemStack)input.getStack());
		if( cell != null )
		{

			final ICellInventory meInventory = createInventory( cell.getChannel(), (ItemStack)input.getStack(), null );
			if( !isCellEmpty( meInventory ) )
			{
				return input;
			}
		}

		final TAEStack l = this.getCellItems().findPrecise( (TAEStack)input );
		if( l != null )
		{
			final long remainingItemCount = this.getRemainingItemCount();
			if( remainingItemCount <= 0 )
			{
				return input;
			}

			if( input.getStackSize() > remainingItemCount )
			{
				final TAEStack r = (TAEStack)input.copy();
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
					final TAEStack toReturn = (TAEStack)input.copy();
					toReturn.setStackSize( input.getStackSize() - remainingItemCount );
					if( mode == Actionable.MODULATE )
					{
						final TAEStack toWrite = (TAEStack)input.copy();
						toWrite.setStackSize( remainingItemCount );

						this.cellItems.add( toWrite );
						this.saveChanges();
					}
					return toReturn;
				}

				if( mode == Actionable.MODULATE )
				{
					this.cellItems.add( (TAEStack)input );
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

		TAEStack Results = null;

		final TAEStack l = this.getCellItems().findPrecise( (TAEStack)request );
		if( l != null )
		{
			Results = (TAEStack)l.copy();

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
	public IStorageChannel<TAEStack, TSlot, TStack> getChannel()
	{
		return this.channel;
	}

	@Override
	protected boolean loadCellItem( NBTTagCompound compoundTag, int stackSize )
	{
		// Now load the item stack
		final TAEStack t;
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
