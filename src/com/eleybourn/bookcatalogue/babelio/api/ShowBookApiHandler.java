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

import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.BOOK_ID;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.BOOK_URL;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.IMAGE;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.ISBN13;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.IS_EBOOK;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_DAY;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_TITLE;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_DAY;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.RATING;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.REVIEW_ID;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.SHELVES;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.SMALL_IMAGE;
import static com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames.WORK_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.os.Bundle;
import android.util.Log;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.babelio.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.babelio.api.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.babelio.api.XmlResponseParser;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to query and response to search.books api call. This is an abstract class
 * designed to be used by other classes that implement specific search methods. It does
 * the heavy lifting of parsing the results etc.
 * 
 * @author Guillaume Smaha
 */
public abstract class ShowBookApiHandler extends ApiHandler {

	/**
	 * Field names we add to the bundle based on parsed XML data
	 * 
	 * @author Guillaume Smaha
	 */
	public static final class ShowBookFieldNames {
		public static final String BOOK_ID = "__book_id";
		public static final String REVIEW_ID = "__review_id";
		public static final String ISBN13 = "__isbn13";
		public static final String IMAGE = "__image";
		public static final String SMALL_IMAGE = "__smallImage";
		public static final String PUBLICATION_YEAR = "__pub_year";
		public static final String PUBLICATION_MONTH = "__pub_month";
		public static final String PUBLICATION_DAY = "__pub_day";
		public static final String IS_EBOOK = "__is_ebook";
		public static final String WORK_ID = "__work_id";
		public static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
		public static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
		public static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";
		public static final String ORIG_TITLE = "__orig_title";
		public static final String RATING = "__rating";
		public static final String SHELVES = "__shelves";
		public static final String BOOK_URL = "__url";
	}

	/** Transient global data for current work in search results. */
	private Bundle mBook;
	/** Local storage for series book appears in */
	private ArrayList<Series> mSeries = null;
	/** Local storage for series book appears in */
	private ArrayList<Author> mAuthors = null;
	
	/** Local storage for shelf names */
	private ArrayList<String> mShelves = null;
	
	/** Current author being processed */
	private String mCurrAuthorName = null;
	/** Current author being processed */
	//private long mCurrAuthorId = 0;

	/** Current series being processed */
	private String mCurrSeriesName = null;
	/** Current series being processed */
	private Integer mCurrSeriesPosition = null;
	/** Current series being processed */
	//private int mCurrSeriesId = 0;
	
	public ShowBookApiHandler(BabelioManager manager) {
		super(manager);
	}

	/**
	 * Perform a search and handle the results.
	 * 
	 * @param request			HttpGet request to use
	 * @param fetchThumbnail 	Indicates if thumbnail file should be retrieved
	 * 
	 * @return	the Bundl of data.
	 * 
	 * @throws IOException 
	 * @throws BookNotFoundException 
	 * @throws NotAuthorizedException 
	 * @throws ClientProtocolException 
	 * @throws NetworkException 
	 */
	public Bundle sendRequest(HttpGet request, boolean fetchThumbnail) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

		mBook = new Bundle();
		mShelves = null;
		
        // We sign the GET request so we get shelves
        String html = mManager.executeRaw(request, true);

        Pattern pattern = Pattern.compile("(?s)(<div class=\"module_t4\".+)(<table>(.+)<div class=\"module_t1\">[\t\r\n]+<h2 class=\"etiquettes\")");
        Matcher matcher = pattern.matcher(html);
    	boolean matchFound = matcher.find();
        
    	if(!matchFound)
    	{
    		return mBook;
    	}
    	
        String blocData = matcher.group(1);

        // Use ISBN13 by preference
        pattern = Pattern.compile("ISBN : ([0-9]+)");
        matcher = pattern.matcher(blocData);
    	matchFound = matcher.find();
        String extract = matcher.group(1);
    	if(matchFound)
    	{
        	if (extract.length() == 13)
        		mBook.putString(CatalogueDBAdapter.KEY_ISBN, extract);
    	}
        Log.d("TEST", "ISBN = "+extract);


        // Image
        if (fetchThumbnail) {
            pattern = Pattern.compile("<link rel=\"image_src\" href=\"(.+)\"/>");
            matcher = pattern.matcher(html);
        	matchFound = matcher.find();
        	if(matchFound) {
	            extract = matcher.group(1);
	            Log.d("TEST", "IMAGE = "+extract);
	            
	            String bestImage = extract;
	            if (bestImage != null) {
	            	if(bestImage.charAt(0) == '/') {
	            		bestImage = "http://www.babelio.com"+bestImage;
	            	}
	    			String filename = Utils.saveThumbnailFromUrl(bestImage, "_BR");
		            Log.d("TEST", "IMAGE FILE = "+filename);
	    			if (filename.length() > 0)
	    				Utils.appendOrAdd(mBook, "__thumbnail", filename);
	            }
	        }
        }

        // Publisher & Date
        pattern = Pattern.compile("(?s)diteur : (.*)([\r\n]+).([0-9]+)");
        matcher = pattern.matcher(blocData);
    	matchFound = matcher.find();
    	if(matchFound) {
	        extract = matcher.group(1);
	        Log.d("TEST", "PUBLISHER = "+extract);
    		mBook.putString(CatalogueDBAdapter.KEY_PUBLISHER, extract);

	        extract = matcher.group(3);
	        Log.d("TEST", "YEAR = "+extract);
    		mBook.putString(CatalogueDBAdapter.KEY_DATE_PUBLISHED, extract);
	    }
    	
    	//My Rating
    	pattern = Pattern.compile("<li class=\"current-rating\"(.+)>Livres ([0-9.]+)/5</li>");
        matcher = pattern.matcher(blocData);
    	matchFound = matcher.find();
    	if(matchFound) {
	        extract = matcher.group(2);
	        Log.d("TEST", "MY RATING = "+extract);
	        Float rating = Float.parseFloat(extract);
	        if(rating == 0) {
	        	matchFound = false;
	        }
	        else {
	        	mBook.putFloat(CatalogueDBAdapter.KEY_RATING, rating);
	        }
	    }

    	if(!matchFound)
    	{
        	//Global Rating
	        pattern = Pattern.compile("itemprop=\"ratingValue\">([0-9.]+)</span>");
	        matcher = pattern.matcher(blocData);
	    	matchFound = matcher.find();
	    	if(matchFound) {
		        extract = matcher.group(1);
		        Log.d("TEST", "GLOBAL RATING = "+extract);
	    		mBook.putFloat(CatalogueDBAdapter.KEY_RATING, Float.parseFloat(extract));
		    }
    	}
    	
    	//Is readed & Is loaned
        pattern = Pattern.compile("(?u)\">(Lu|A lire|En cours|Pense-bête)(, A échanger)?( , Emprunté)?( )?</div></b>([\t\r\n]+)<span class=\"lien_t1\"");
        matcher = pattern.matcher(html);
    	matchFound = matcher.find();
    	if(matchFound) {
	        extract = matcher.group(1);
	        if(extract != null && extract.contains("Lu"))
	        {
		        Log.d("TEST", "IS READED !");
	        	mBook.putBoolean(CatalogueDBAdapter.KEY_READ, true);
	        }

	        extract = matcher.group(3);
	        if(extract != null)
	        {
		        Log.d("TEST", "IS LOANED !");
		        
	        	mBook.putString(CatalogueDBAdapter.KEY_LOCATION, "This book has been loaned");
	        }
	    }
    	
    	//Author
        pattern = Pattern.compile("(?s)<a class=\"libelle\" href=\"/auteur/(.+)/([0-9]+)\" itemprop=\"url\" style=\"font-weight:normal;\">([\t\r\n]+)<span itemprop=\"name\">(.+)<b>(.+)</b></span>");
        matcher = pattern.matcher(html);
    	matchFound = matcher.find();
    	if(matchFound) {
	        extract = matcher.group(2);
	        Long authorid = Long.parseLong(extract);
	        Log.d("TEST", "AUTHOR ID = "+extract);
	        //mBook.putLong(DatabaseDefinitions.DOM_BABELIO_BOOK_ID.name, authorid);

	        extract = matcher.group(4) + matcher.group(5);
			mAuthors = new ArrayList<Author>();
	        mAuthors.add(new Author(extract));
	        Log.d("TEST", "AUTHOR NAME = "+extract);
	        
    		mBook.putString(CatalogueDBAdapter.KEY_AUTHOR_DETAILS, Utils.getAuthorUtils().encodeList(mAuthors, '|'));
	    }
    	
    	//Description
        pattern = Pattern.compile("(?s)itemprop=\"description\">(.+)<p class=\"footer\"");
        matcher = pattern.matcher(html);
    	matchFound = matcher.find();
    	if(matchFound) {
	        extract = matcher.group(1).replaceAll("(<br\\s*\\/*?>)", "\n");
	        Log.d("TEST", "DESCRIPTION = "+extract);
    		mBook.putString(CatalogueDBAdapter.KEY_DESCRIPTION, extract);
	    }
    	
    	

        // Title
        pattern = Pattern.compile("(?s)class=\"couv1\"/>([\t\r\n]+)(.+)</a> </h1>");
        matcher = pattern.matcher(html);
    	matchFound = matcher.find();
        Log.d("TEST", "TITLE CHECK");
    	if(matchFound) {
	        extract = matcher.group(2);
	        mBook.putString(CatalogueDBAdapter.KEY_TITLE, extract);
	        Log.d("TEST", "TITLE = "+extract);

			Series.SeriesDetails details = Series.findSeries(extract);
			if (details != null && details.name.length() > 0) {
				if (mSeries == null)
					mSeries = new ArrayList<Series>();
				mSeries.add(new Series(details.name, details.position));
				// Tempting to replace title with ORIG_TITLE, but that does bad things to translations (it used the original language)
				mBook.putString(CatalogueDBAdapter.KEY_TITLE, extract.substring(0, details.startChar-1));	
		        Log.d("TEST", "TITLE SERIES = "+extract);	 
	        }       	
	    }

        if (mSeries != null && mSeries.size() > 0)
			mBook.putString(CatalogueDBAdapter.KEY_SERIES_DETAILS, Utils.getSeriesUtils().encodeList(mSeries, '|'));

        if (mShelves != null && mShelves.size() > 0)
        	mBook.putStringArrayList(SHELVES, mShelves);
        
        // Return parsed results.
        return mBook;
	}
}
