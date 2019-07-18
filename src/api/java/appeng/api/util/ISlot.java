package appeng.api.util;


import appeng.api.storage.data.IAEStack;

public interface ISlot<TStack, TAEStack extends IAEStack>
{
	TStack getStack();
	void setStack(final TStack stack);
	TAEStack getAEStack();
	void setAEStack( final TAEStack is );

	int getSlot();
	void setSlot( final int slot );
	boolean isExtractable();
    void setExtractable( final boolean isExtractable );
}
