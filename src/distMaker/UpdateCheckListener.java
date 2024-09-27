// Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
