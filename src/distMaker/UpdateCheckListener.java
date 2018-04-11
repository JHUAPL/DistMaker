package distMaker;

/**
 * lets any interested party know that a check for for updates has been done
 *
 * @author vandejd1
 */
public interface UpdateCheckListener
{
	/**
	 * UpdateCheckListener that does nothing. Use this (immutable) instance if you do not care about notifications.
	 */
	public final static UpdateCheckListener None = new UpdateCheckListener()
	{
		@Override
		public void checkForNewVersionsPerformed()
		{
			; // Nothing to do
		}
	};

	/**
	 * Notify the listener that an update check has been performed.
	 */
	void checkForNewVersionsPerformed();
}
