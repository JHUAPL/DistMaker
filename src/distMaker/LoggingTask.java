package distMaker;

import glum.task.SilentTask;

import java.util.ArrayList;
import java.util.List;

public class LoggingTask extends SilentTask
{
	private final List<String> messages = new ArrayList<>();

	@Override
	public void infoAppend(String aMsg)
	{
		messages.add(aMsg);
		super.infoAppend(aMsg);
	}

	@Override
	public void infoAppendln(String aMsg)
	{
		messages.add(aMsg);
		super.infoAppendln(aMsg);
	}

	@Override
	public void infoUpdate(String aMsg)
	{
		messages.add(aMsg);
		super.infoUpdate(aMsg);
	}

	List<String> getMessages()
	{
		return messages;
	}
}
