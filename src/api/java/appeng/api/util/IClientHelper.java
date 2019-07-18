
package appeng.api.util;


import java.util.List;

import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEStack;


public interface IClientHelper
{
	/**
	 * Add cell information to the provided list. Used for tooltip content.
	 * 
	 * @param handler Cell handler.
	 * @param lines List of lines to add to.
	 */
	<TAEStack extends IAEStack, TSlot extends ISlot<TStack, TAEStack>, TStack> void addCellInformation( ICellInventoryHandler<TAEStack, TSlot, TStack> handler, List<String> lines );

}
