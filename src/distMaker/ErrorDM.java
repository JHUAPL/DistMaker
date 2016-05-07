package distMaker;

/**
 * Generic runtime exception thrown by various DistMaker modules/routines.
 */
public class ErrorDM extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	// State vars
	private String subject;

	public ErrorDM(Throwable aCause, String aMessage, String aSubject)
	{
		super(aMessage, aCause);
		subject = aSubject;
	}

	public ErrorDM(Throwable aCause, String aMessage)
	{
		this(aCause, aMessage, null);
	}

	public ErrorDM(String aMessage)
	{
		this(null, aMessage, null);
	}

	/**
	 * Returns the subject specific to this error. May be null if this is just a generic error.
	 */
	public String getSubject()
	{
		return subject;
	}

}
