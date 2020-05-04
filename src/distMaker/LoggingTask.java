package distMaker;

import glum.task.SilentTask;

import java.util.ArrayList;
import java.util.List;

public class LoggingTask extends SilentTask
{
	private final List<String> messageL = new ArrayList<>();

	@Override
	public void logReg(String aMsg)
	{
		messageL.add(aMsg);
		super.logReg(aMsg);
	}

	@Override
	public void logRegln(String aMsg)
	{
		messageL.add(aMsg);
		super.logRegln(aMsg);
	}

	@Override
	public void logRegUpdate(String aMsg)
	{
		messageL.add(aMsg);
		super.logRegUpdate(aMsg);
	}

	List<String> getMessages()
	{
		return messageL;
	}
}
