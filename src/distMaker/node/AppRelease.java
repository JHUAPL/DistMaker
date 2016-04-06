package distMaker.node;

import distMaker.LookUp;
import glum.database.QueryItem;

/**
 * Immutable object that has information relevant to the packaged software.
 */
public class AppRelease implements Comparable<AppRelease>, QueryItem<LookUp>
{
	private final String appName;
	private final String version;
	private final long buildTime;

	public AppRelease(String aAppName, String aVersion, long aBuildTime)
	{
		appName = aAppName;
		version = aVersion;
		buildTime = aBuildTime;
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
		return version;
	}

	/**
	 * Returns the time at which this version was built.
	 */
	public long getBuildTime()
	{
		return buildTime;
	}

	@Override
	public int compareTo(AppRelease o)
	{
		if (buildTime < o.buildTime)
			return -1;
		else if (buildTime > o.buildTime)
			return 1;

		return 0;
	}

	@Override
	public Object getValue(LookUp aEnum)
	{
		switch (aEnum)
		{
			case BuildTime:
			return buildTime;

			case Version:
			return version;

			default:
			return null;
		}
	}

	@Override
	public void setValue(@SuppressWarnings("unused") LookUp aEnum, @SuppressWarnings("unused") Object aObj)
	{
		throw new RuntimeException("Unsupported operation");
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appName == null) ? 0 : appName.hashCode());
		result = prime * result + (int)(buildTime ^ (buildTime >>> 32));
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AppRelease other = (AppRelease)obj;
		if (appName == null)
		{
			if (other.appName != null)
				return false;
		}
		else if (!appName.equals(other.appName))
			return false;
		if (buildTime != other.buildTime)
			return false;
		if (version == null)
		{
			if (other.version != null)
				return false;
		}
		else if (!version.equals(other.version))
			return false;
		return true;
	}

}
