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
 * Generic runtime exception thrown by various DistMaker modules/routines.
 * 
 * @author lopeznr1
 */
public class ErrorDM extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	// State vars
	private final String subject;

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
