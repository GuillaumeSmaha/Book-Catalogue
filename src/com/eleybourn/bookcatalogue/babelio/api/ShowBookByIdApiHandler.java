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

import java.io.IOException;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;

/**
 * Class to call the search.books api (using a babelio work ID).
 * 
 * @author Guillaume Smaha
 */
public class ShowBookByIdApiHandler extends ShowBookApiHandler {
	
	public ShowBookByIdApiHandler(BabelioManager manager) {
		super(manager);
	}

	/**
	 * Perform a search and handle the results.
	 *
	 * @param workId
	 * @param fetchThumbnail
	 * @return	the array of BabelioWork objects.
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 * @throws ClientProtocolException 
	 * @throws NetworkException 
	 */
	public Bundle get(long workId, boolean fetchThumbnail) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		// Setup API call
		final String url = "http://www.babelio.com/livres/%20/"+workId;
		HttpGet get = new HttpGet(url);

		return sendRequest(get, fetchThumbnail);
	}

}
