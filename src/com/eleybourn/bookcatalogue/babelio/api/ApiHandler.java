/*
 * @copyright 2012 Guillaume Smaha
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.babelio.api;

import android.util.Log;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;

/**
 * Base class for all babelio API handler classes.
 * 
 * The job of an API handler is to implement a method to run the API (eg. 'search' in 
 * SearchBooksApiHandler) and to process the output.
 * 
 * @author Guillaume Smaha
 *
 */
public abstract class ApiHandler {
	BabelioManager mManager;

	/** XmlFilter root object. Used in extracting data file XML results. */
	protected XmlFilter mRootFilter = new XmlFilter("");
	

	public ApiHandler(BabelioManager manager) {
		mManager = manager;
	}
}
