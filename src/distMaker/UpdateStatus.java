package distMaker;

public class UpdateStatus
{
	private final boolean isUpToDate;
	private final boolean errorDeterminingState;
	private final String errorMessage;

	public UpdateStatus(boolean isUpToDate)
	{
		this.isUpToDate = isUpToDate;
		this.errorDeterminingState = false;
		this.errorMessage = "";
	}

	public UpdateStatus(String errorMessage)
	{
		this.isUpToDate = false;
		this.errorDeterminingState = true;
		this.errorMessage = errorMessage;
	}

	public boolean isUpToDate()
	{
		return isUpToDate;
	}

	public boolean isErrorDeterminingState()
	{
		return errorDeterminingState;
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}
}
