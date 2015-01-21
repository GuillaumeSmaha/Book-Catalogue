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

import org.apache.http.client.methods.HttpGet;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;

/**
 * API call to get a babelio ID from an ISBN.
 * 
 * NOTE: THIS API DOES NOT RETURN XML. The text output is the ID.
 * 
 * @author Guillaume Smaha
 */
public class IsbnToId extends ApiHandler {

	public IsbnToId(BabelioManager manager) {
		super(manager);
	}

	/*
	 * Get the Babelio book ID given an ISBN. Response contains the ID without any markup.
	 *	URL: http://www.babelio.com/resrecherche.php?item_recherche=isbn&Recherche=    (sample url)
	 *	HTTP method: GET
	 *	Parameters:
	 *	    isbn: The ISBN of the book to lookup.
	 */
	public long isbnToId(String isbn) 
			throws IOException, NotAuthorizedException, BookNotFoundException, NetworkException 
	{
		HttpGet get = new HttpGet("http://www.babelio.com/resrecherche.php?item_recherche=isbn&Recherche=" + isbn.trim());

		try {
	        String html = mManager.executeRaw(get, false);

	        Pattern pattern = Pattern.compile("<a href=\"/livres/([a-zA-Z0-9_-]+)/([0-9]+)\" class=\"titre1\"");
	        Matcher matcher = pattern.matcher(html);

	        boolean find = matcher.find();
	        if(find)
	        {
		      	return Long.parseLong(matcher.group(2));
	        }
		} catch (Exception e) {
        	throw new BookNotFoundException(e);
		}
		
		return 0;
	}
	
}
