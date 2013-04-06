package distMaker;

import glum.database.QueryItem;


public class Release implements Comparable<Release>, QueryItem<LookUp>
{
	private String appName;
	private String verName;
	private long deployTime;

	public Release(String aAppName, String aVerName, long aDeployTime)
	{
		appName = aAppName;
		verName = aVerName;
		deployTime = aDeployTime;
	}

	/**
	 * Returns the formal name of the application
	 */
	public String getName()
	{
		return appName;
	}

	/**
	 * Returns the version name corresponding to this distribution
	 */
	public String getVersion()
	{
		return verName;
	}

	/**
	 * Returns the time at which this version was deployed (made available to the public/customer)
	 */
	public long getDeployTime()
	{
		return deployTime;
	}

	@Override
	public int compareTo(Release o)
	{
		if (deployTime < o.deployTime)
			return -1;
		else if (deployTime > o.deployTime)
			return 1;

		return 0;
	}

	@Override
	public Object getValue(LookUp aEnum)
	{
		switch (aEnum)
		{
			case DeployTime:
			return deployTime;

			case VersionName:
			return verName;

			default:
			return null;
		}
	}

	@Override
	public void setValue(LookUp aEnum, Object aObj)
	{
		throw new RuntimeException("Unsupported operation");
	}

}
