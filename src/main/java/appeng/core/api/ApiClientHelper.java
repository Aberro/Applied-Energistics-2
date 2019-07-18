
package appeng.core.api;


import java.util.List;

import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IClientHelper;
import appeng.api.util.ISlot;
import appeng.core.localization.GuiText;


public class ApiClientHelper implements IClientHelper
{
	@Override
	public <TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack> void addCellInformation(ICellInventoryHandler<TAEStack, TSlot, TStack> handler, List<String> lines )
	{
		if( handler == null )
		{
			return;
		}

		final ICellInventory<TAEStack, TSlot, TStack> cellInventory = handler.getCellInv();

		if( cellInventory != null )
		{
			lines.add( cellInventory.getUsedBytes() + " " + GuiText.Of.getLocal() + ' ' + cellInventory.getTotalBytes() + ' ' + GuiText.BytesUsed.getLocal() );

			lines.add( cellInventory.getStoredItemTypes() + " " + GuiText.Of.getLocal() + ' ' + cellInventory.getTotalItemTypes() + ' ' + GuiText.Types
					.getLocal() );
		}

		if( handler.isPreformatted() )
		{
			final String list = ( handler.getIncludeExcludeMode() == IncludeExclude.WHITELIST ? GuiText.Included : GuiText.Excluded ).getLocal();

			if( handler.isFuzzy() )
			{
				lines.add( GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Fuzzy.getLocal() );
			}
			else
			{
				lines.add( GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Precise.getLocal() );
			}
		}

	}

}
