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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;

/**
 * Class to call the search.books api (using an ISBN).
 * 
 * @author Guillaume Smaha
 */
public class ShowBookByIsbnApiHandler extends ShowBookApiHandler {

	
	public ShowBookByIsbnApiHandler(BabelioManager manager) {
		super(manager);
	}

	/**
	 * Perform a search and handle the results.
	 * 
	 * @param query
	 * @return	the array of BabelioWork objects.
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws ClientProtocolException 
	 * @throws NetworkException 
	 */
	public Bundle get(String isbn, boolean fetchThumbnail) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		if (isbn == null)
			throw new RuntimeException("Null ISBN specified in search");
		isbn = isbn.trim();
		if (!IsbnUtils.isValid(isbn))
			throw new RuntimeException(BookCatalogueApp.getResourceString(R.string.invalid_isbn_x_specified_in_search, isbn));

		// Setup API call //
		HttpGet get = new HttpGet("http://www.babelio.com/resrecherche.php?item_recherche=isbn&Recherche=" + isbn.trim());

		try {
	        String html = mManager.executeRaw(get, false);

	        Pattern pattern = Pattern.compile("<a href=\"/livres/([a-zA-Z0-9_-]+)/([0-9]+)\" class=\"titre1\"");
	        Matcher matcher = pattern.matcher(html);

	        boolean find = matcher.find();
	        if(find)
	        {
	    		HttpGet getBook = new HttpGet("http://www.babelio.com/livres/%20/" + matcher.group(2)+"/41portee=editeur&desc_smenu=l");
	        	return sendRequest(getBook, fetchThumbnail);
	        }
		} catch (Exception e) {
		}

    	return new Bundle();   
	}

}
