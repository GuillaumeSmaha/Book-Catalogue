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

package com.eleybourn.bookcatalogue.babelio;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.babelio.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.babelio.api.IsbnToId;
import com.eleybourn.bookcatalogue.babelio.api.ReviewUpdateHandler;
import com.eleybourn.bookcatalogue.babelio.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.babelio.api.ShelfAddBookHandler;
import com.eleybourn.bookcatalogue.babelio.api.ShowBookApiHandler.ShowBookFieldNames;
import com.eleybourn.bookcatalogue.babelio.api.ShowBookByIdApiHandler;
import com.eleybourn.bookcatalogue.babelio.api.ShowBookByIsbnApiHandler;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to wrap all Babelio API calls and manage an API connection.
 * 
 * ENHANCE: Add 'send to babelio'/'update from internet' option in book edit menu
 * ENHANCE: Change 'update from internet' to allow source selection and single-book execution
 * ENHANCE: Link an Event to a book, and display in book list with exclamation triangle overwriting cover.
 * ENHANCE: MAYBE Replace Events with something similar in local DB?
 * 
 * @author Guillaume Smaha
 */
public class BabelioManager {

	/** Enum to handle possible results of sending a book to babelio */
	public static enum ExportDisposition { error, sent, noIsbn, notFound, networkError };
	
	private static final String LAST_SYNC_DATE = "BabelioManager.LastSyncDate";

	/**
	 * Exceptions that may be thrown and used to wrap more varied inner exceptions
	 */
	public static class Exceptions {
		public static class GeneralException extends Exception {
			private static final long serialVersionUID = 1122718109154866810L;
			Throwable m_inner;
			public GeneralException(Throwable inner) { m_inner = inner; };
		}
		public static class NotAuthorizedException extends GeneralException {
			private static final long serialVersionUID = -3253140293072591411L;

			public NotAuthorizedException(Throwable inner) { super(inner); }
		};
		public static class BookNotFoundException extends GeneralException {
			private static final long serialVersionUID = -5463653101245756234L;

			public BookNotFoundException(Throwable inner) { super(inner); }
		};
		public static class NetworkException extends GeneralException {
			private static final long serialVersionUID = 1916125886760145709L;

			public NetworkException(Throwable inner) { super(inner); }
		};		
	}

	// Set to true when the credentials have been successfully verified.
	protected static boolean m_hasValidCredentials = false;
	// Cached when credentials have been verified.
	protected static String m_accessId = null;
	protected static String m_accessPass = null;
	// Local copies of user data retrieved when the credentials were verified
	protected static String m_accessPhpsessid = null;
	protected static String m_username = null;
	protected static long m_userId = 0;
	// Stores the last time an API request was made to avoid breaking API rules.
	private static Long m_LastRequestTime = 0L;

	/**
	 * Standard constructor; call common code.
	 * 
	 * @author Guillaume Smaha
	 */
	public BabelioManager(boolean check) {
		if(check && !m_hasValidCredentials)
			validateCredentials();
	}
	
	/**
	 * Clear the credentials from the preferences and local cache
	 */
	public static void forgetCredentials() {
        m_userId = 0;
		m_accessId = "";
		m_accessPass = "";
		m_accessPhpsessid = "";
		m_hasValidCredentials = false;
		// Get the stored token values from prefs, and setup the consumer if present
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		prefs.setString("Babelio.AccessToken.Id", "");
		prefs.setString("Babelio.AccessToken.Pass", "");
		prefs.setString("Babelio.AccessToken.Phpsessid", "");
		prefs.setLong("Babelio.AccessToken.Userid", 0);
	}

	/**
	 * Utility method to check if the access tokens are available (not if they are valid).
	 * 
	 * @return
	 */
	public static boolean hasCredentials() {
		
		if (m_accessId != null && m_accessPass != null
				&& !m_accessId.equals("") && !m_accessPass.equals("") && m_userId != 0)
		{
	        Log.d("hasCredentials", "CHECK OK");
			return true;
		}

		// Get the stored token values from prefs, and setup the consumer if present
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

		m_accessId = prefs.getString("Babelio.AccessToken.Id", "");
		m_accessPass = prefs.getString("Babelio.AccessToken.Pass", "");
		m_accessPhpsessid = prefs.getString("Babelio.AccessToken.Phpsessid", "");
		m_userId = prefs.getLong("Babelio.AccessToken.Userid", 0);

        Log.d("hasCredentials", "CHECK FAILED");
		return m_accessId != null && m_accessPass != null && 
				!m_accessId.equals("") && !m_accessPass.equals("") && m_userId != 0;
	}

	/**
	 * Save the Id and the password
	 * 
	 * @author Guillaume Smaha
	 */
	public void registerUsernamePassword(String id, String pass)
	{
		if(id.length() > 0 && pass.length() > 0)
		{
			BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

			String idSaved = prefs.getString("Babelio.AccessToken.Id", "");
			String passSaved = prefs.getString("Babelio.AccessToken.Pass", "");
			
			if(id != idSaved && pass != passSaved)
			{
				m_hasValidCredentials = false;
			}
			
			prefs.setString("Babelio.AccessToken.Id", id);
			prefs.setString("Babelio.AccessToken.Pass", pass);
		}
	}

	/**
	 * Check if the current credentials (either cached or in prefs) are valid. If they
	 * have been previously checked and were valid, just use that result.
	 * 
	 * @author Guillaume Smaha
	 * @throws NetworkException 
	 */
	public boolean hasValidCredentials() throws NetworkException {
		// If credentials have already been accepted, don't re-check.
		if (m_hasValidCredentials)
			return true;

    	throw new NetworkException(null);
	}

	/**
	 * Check if the current credentials (either cached or in prefs) are valid. If they
	 * have been previously checked and were valid, just use that result.
	 * 
	 * @author Guillaume Smaha
	 */
	public boolean getValidCredentials() {

		return m_hasValidCredentials;
	}

	/**
	 * Check if the current credentials (either cached or in prefs) are valid, and
	 * cache the result.
	 * 
	 * If cached credentials were used, call recursively after clearing the cached
	 * values.
	 * 
	 * @author Guillaume Smaha
	 */
	public boolean validateCredentials() {
		// Get the stored token values from prefs, and setup the consumer
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();

		m_accessId = prefs.getString("Babelio.AccessToken.Id", "");
		m_accessPass = prefs.getString("Babelio.AccessToken.Pass", "");
		prefs.setString("Babelio.AccessToken.Phpsessid", "");
		prefs.setLong("Babelio.AccessToken.Userid", 0);
		m_hasValidCredentials = false;

        Log.d("TEST", "get PHPSESSID");
		try {
			AuthUserApiHandler authUserApi = new AuthUserApiHandler(this);
			
			if (authUserApi.getAuthUser() == 0)
			{
	            m_userId = 0;
				m_accessPhpsessid = null;
				return false;
			}

            // Save result...
            m_userId = authUserApi.getUserid();
            m_accessPhpsessid = authUserApi.getPhpsessid();
            Log.d("TEST", "PHPSESSID="+m_accessPhpsessid);

    		prefs.setString("Babelio.AccessToken.Phpsessid", m_accessPhpsessid);
			prefs.setLong("Babelio.AccessToken.Userid", m_userId);

			// Cache the result to avoid web checks later
			m_hasValidCredentials = true;

		} catch (Exception e) {
			// Something went wrong. Clear the access token, mark credentials as bad, and if we used
			// cached values, retry by getting them from prefs.
            m_userId = 0;
			m_accessPhpsessid = null;
			return false;
		}

		return true;
	}

	/**
	 * Create an HttpClient with specifically set buffer sizes to deal with
	 * potentially exorbitant settings on some HTC handsets.
	 * 
	 * @return
	 */
	private HttpClient newHttpClient() {
		
		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(params, 30000);
		HttpConnectionParams.setSocketBufferSize(params, 32768);
		HttpConnectionParams.setLinger(params, 0);
		HttpConnectionParams.setTcpNoDelay(params, false);
		HttpClient httpClient = new DefaultHttpClient();	
		
		return httpClient;
	}

	/**
	 * Utility routine called to sign a request and submit it then pass it off to a parser.
	 *
	 * @author Guillaume Smaha
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 * @throws NetworkException 
	 */
	public HttpResponse execute(HttpUriRequest request, DefaultHandler requestHandler, boolean requiresSignature) throws ClientProtocolException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

		// Get a new client
		HttpClient httpClient = newHttpClient();

		// Sign the request and wait until we can submit it legally.
		if(requiresSignature && m_accessPhpsessid != null && m_accessPhpsessid.length() > 0)
		{
			String sessionCookie = "PHPSESSID="+m_accessPhpsessid;
			request.setHeader("Cookie", sessionCookie);
		}
		
    	waitUntilRequestAllowed();

    	// Submit the request and process result.
    	HttpResponse response;
    	try {
    		response = httpClient.execute(request);
    	} catch (Exception e) {
        	Log.d("ERROR", e.getMessage());
    		throw new NetworkException(e);
    	}

    	int code = response.getStatusLine().getStatusCode();
    	if (code == 200 || code == 201 || code == 302)
    		parseResponse(response, requestHandler);
    	else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());

    	return response;
	}

	/**
	 * Utility routine called to sign a request and submit it then return the raw text output.
	 *
	 * @author Guillaume Smaha
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 * @throws NetworkException 
	 */
	public Header[] executeHeaders(HttpUriRequest request, String header, boolean requiresSignature) throws ClientProtocolException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

		// Get a new client
		HttpClient httpClient = newHttpClient();

		// Sign the request and wait until we can submit it legally.
		if(requiresSignature && m_accessPhpsessid != null && m_accessPhpsessid.length() > 0)
		{
			String sessionCookie = "PHPSESSID="+m_accessPhpsessid;
			request.setHeader("Cookie", sessionCookie);
		}
		
    	waitUntilRequestAllowed();

    	// Submit the request then process result.
    	HttpResponse response = null;
    	try {
    		response = httpClient.execute(request);
    	} catch (Exception e) {
        	Log.d("ERROR", e.getMessage());
    		throw new NetworkException(e);
    	}

    	int code = response.getStatusLine().getStatusCode();

        if (code == 200 || code == 201 || code == 302) {
        	if(header.equals(""))
        	{
        		return response.getAllHeaders();
        	}
        	else
        	{
        		return response.getHeaders(header);
        	}
    	} else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else {
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
    	}
	}

	/**
	 * Utility routine called to sign a request and submit it then return the raw text output.
	 *
	 * @author Guillaume Smaha
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 * @throws NetworkException 
	 */
	public HttpResponse executeResponse(HttpUriRequest request, boolean requiresSignature) throws ClientProtocolException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

		// Get a new client
		HttpClient httpClient = newHttpClient();

		// Sign the request and wait until we can submit it legally.
		if(requiresSignature && m_accessPhpsessid != null && m_accessPhpsessid.length() > 0)
		{
			String sessionCookie = "PHPSESSID="+m_accessPhpsessid;
			request.setHeader("Cookie", sessionCookie);
		}

    	waitUntilRequestAllowed();

    	
    	// Submit the request then process result.
    	HttpResponse response = null;
    	try {
    		response = httpClient.execute(request);
    	} catch (Exception e) {
        	Log.d("ERROR", e.getMessage());
    		throw new NetworkException(e);
    	}

    	int code = response.getStatusLine().getStatusCode();
    	
    	Log.d("TEST", "code="+code);

        if (code == 200 || code == 201 || code == 302) {
        	return response;
    	} else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else {
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
    	}
	}

	/**
	 * Check if the user is connected or not
	 *
	 * @author Guillaume Smaha
	 */
	public boolean checkConnection(String html) {

		if(html.contains("<a href=\"/?closeSession=1\" class=\"lien_t1\" rel=\"nofollow\"")) {
			return true;
		}
		
		return false;
	}

	/**
	 * Utility routine called to sign a request and submit it then return the raw text output.
	 *
	 * @author Guillaume Smaha
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 * @throws NotAuthorizedException 
	 * @throws BookNotFoundException 
	 * @throws NetworkException 
	 */
	public String executeRaw(HttpUriRequest request, boolean requiresSignature) throws ClientProtocolException, IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

		// Get a new client
		HttpClient httpClient = newHttpClient();

		// Sign the request and wait until we can submit it legally.
		if(m_accessPhpsessid != null && m_accessPhpsessid.length() > 0)
		{
			String sessionCookie = "PHPSESSID="+m_accessPhpsessid;
			request.setHeader("Cookie", sessionCookie);
		}
		
    	waitUntilRequestAllowed();

        Log.d("TEST", "executeRaw execute");
    	// Submit the request then process result.
    	HttpResponse response = null;
    	try {
    		response = httpClient.execute(request);
    	} catch (Exception e) {
        	Log.d("ERROR", e.getMessage());
    		throw new NetworkException(e);
    	}

    	int code = response.getStatusLine().getStatusCode();
        StringBuilder html = new StringBuilder();
        HttpEntity e = response.getEntity();
        if (e != null) {
            InputStream in = e.getContent();
            if (in != null) {
                while (true) {
                	int i = in.read();
                	if (i == -1) break;
                	html.append((char)(i));
                }        	            	
            }
        }

		Log.d("TEST", "executeRaw code "+code);

        if (code == 200 || code == 201 || code == 302) {
        	
        	String htmlStr = html.toString();
    		if(requiresSignature && m_accessPhpsessid != null && m_accessPhpsessid.length() > 0)
    		{
    			if(!checkConnection(htmlStr))
    			{
    				if(validateCredentials())
    				{
    					htmlStr = executeRaw(request, requiresSignature);
    				}
    				else
    				{
    	        		m_hasValidCredentials = false;
    				}
    			}
    		}
        	
            return htmlStr;
    	} else if (code == 401) {
    		m_hasValidCredentials = false;
    		throw new NotAuthorizedException(null);
    	} else if (code == 404) {
    		throw new BookNotFoundException(null);
    	} else {
    		throw new RuntimeException("Unexpected status code from API: " + response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
    	}
	}

	/**
	 * Utility routine called to pass a response off to a parser.
	 *
	 * @author Guillaume Smaha
	 */
	public boolean parseResponse(HttpResponse response, DefaultHandler requestHandler) throws IllegalStateException, IOException {
		boolean parseOk = false;

		if(requestHandler == null)
		{
			return true;
		}
		
		// Setup the parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;

		InputStream in = response.getEntity().getContent();

		// Dont bother catching general exceptions, they will be caught by the caller.
		try {
			parser = factory.newSAXParser();
			// Make sure we follow LibraryThing ToS (no more than 1 request/second).
			parser.parse(in, requestHandler);
			parseOk = true;
		} catch (MalformedURLException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (ParserConfigurationException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		} catch (java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			Logger.logError(e, s);
		}		
		return parseOk;
	}

	/**
	 * Utility routine called to pass a string response off to a parser.
	 *
	 * @author Guillaume Smaha
	 */
	public boolean parseXml(String xmlResponse, DefaultHandler requestHandler) throws IllegalStateException, IOException {
		boolean parseOk = false;

		if(requestHandler == null)
		{
			return true;
		}
		Log.d("TEST","TRY TO PARSE");
		// Setup the parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		Log.d("TEST","parser");

		// Dont bother catching general exceptions, they will be caught by the caller.
		try {
			Log.d("TEST","try");
			parser = factory.newSAXParser();
			// Make sure we follow LibraryThing ToS (no more than 1 request/second).
			parser.parse(new InputSource(new StringReader(xmlResponse)), requestHandler);
			parseOk = true;
		} catch (MalformedURLException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			//Logger.logError(e, s);
			Log.e("MalformedURLException", s);
		} catch (ParserConfigurationException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			//Logger.logError(e, s);
			Log.e("ParserConfigurationException", s);
		} catch (SAXException e) {
			String s = e.getMessage(); // "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			//Logger.logError(e, s);
			Log.e("SAXException", s);
		} catch (java.io.IOException e) {
			String s = "unknown";
			try { s = e.getMessage(); } catch (Exception e2) {};
			//Logger.logError(e, s);
			Log.e("IOException", s);
		}		
		Log.d("TEST", parseOk?"parseok":"parseko");
		return parseOk;
	}

	/**
	 * Use mLastRequestTime to determine how long until the next request is allowed; and
	 * update mLastRequestTime this needs to be synchroized across threads.
	 *
 	 * Note that as a result of this approach mLastRequestTime may in fact be
	 * in the future; callers to this routine effectively allocate time slots.
	 * 
	 * This method will sleep() until it can make a request; if ten threads call this 
	 * simultaneously, one will return immediately, one will return 1 second later, another
	 * two seconds etc.
	 * 
	 */
	private static void waitUntilRequestAllowed() {
		long now = System.currentTimeMillis();
		long wait;
		synchronized(m_LastRequestTime) {
			wait = 1000 - (now - m_LastRequestTime);
			//
			// mLastRequestTime must be updated while synchronized. As soon as this
			// block is left, another block may perform another update.
			//
			if (wait < 0)
				wait = 0;
			m_LastRequestTime = now + wait;
		}
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
			}
		}
	}

	public String getUsername() {
		if (!m_hasValidCredentials)
			throw new RuntimeException("Babelio credentials need to be validated before accessing user data");

		return m_username;
	}

	public long getUserid() {
		if (!m_hasValidCredentials)
			throw new RuntimeException("Babelio credentials need to be validated before accessing user data");

		return m_userId;
	}

	/** Local API object */
	private IsbnToId m_isbnToId = null;
	/**
	 * Wrapper to call ISBN->ID API
	 */
	public long isbnToId(String isbn) throws NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
		if (m_isbnToId == null)
			m_isbnToId = new IsbnToId(this);
		return m_isbnToId.isbnToId(isbn);
	}

	/** Local API object */
	ShelfAddBookHandler m_addBookHandler = null;
	/**
	 * Wrapper to call API to add book to shelf
	 */
	public long addBookToShelf(String shelfName,long baBookId) throws NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
		if (m_addBookHandler == null)
			m_addBookHandler = new ShelfAddBookHandler(this);
		return m_addBookHandler.add(shelfName, baBookId);
	}
	/**
	 * Wrapper to call API to remove a book from a shelf
	 */
	public void removeBookFromShelf(String shelfName,long baBookId) throws NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
		if (m_addBookHandler == null)
			m_addBookHandler = new ShelfAddBookHandler(this);
		m_addBookHandler.remove(shelfName, baBookId);
	}

	private ReviewUpdateHandler mReviewUpdater = null;
	public void updateReview(long reviewId, boolean isRead, String readAt, String review, int rating) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		if (mReviewUpdater == null) {
			mReviewUpdater = new ReviewUpdateHandler(this);
		}
		mReviewUpdater.update(reviewId, isRead, readAt, review, rating);
	}

	/**
	 * Wrapper to send an entire book, including shelves, to Babelio.
	 * 
	 * @param dbHelper	DB connection
	 * @param books		Cursor pointing to single book to send
	 *
	 * @return			Disposition of book
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws NotAuthorizedException 
	 * @throws OAuthCommunicationException 
	 * @throws OAuthExpectationFailedException 
	 * @throws OAuthMessageSignerException 
	 * @throws NetworkException 
	 */
	public ExportDisposition sendOneBook(CatalogueDBAdapter dbHelper, BooksRowView books) throws InterruptedException, NotAuthorizedException, IOException, NetworkException {
		long bookId = books.getId();
		long baId;
		long reviewId = 0;
		Bundle grBookInfo = null;
		boolean isNew;

		// Get the book ISBN
		String isbn = books.getIsbn();

		// See if the book has a babelio ID and if it is valid.
		try {
			baId = books.getBabelioBookId();
			if (baId != 0) {
				// Get the book details to make sure we have a valid book ID
				grBookInfo = this.getBookById(baId);
				if (grBookInfo == null)
					baId = 0;
			}
		} catch (Exception e) {
			baId = 0;
		}

		isNew = (baId == 0);

		if (baId == 0 && !isbn.equals("")) {

			if (!IsbnUtils.isValid(isbn))
				return ExportDisposition.notFound;

			try {
				// Get the book details using ISBN
				grBookInfo = this.getBookByIsbn(isbn);
				if (grBookInfo != null && grBookInfo.containsKey(ShowBookFieldNames.BOOK_ID))
					baId = grBookInfo.getLong(ShowBookFieldNames.BOOK_ID);

				// If we got an ID, save it against the book
				if (baId != 0) {
					dbHelper.setBabelioBookId(bookId, baId);
				}
			} catch (BookNotFoundException e) {
				return ExportDisposition.notFound;
			} catch (NetworkException e) {
				return ExportDisposition.networkError;
			}			
		}

		// If we found a babelio book, update it
		if (baId != 0) {
			// Get the review ID if we have the book details. For new books, it will not be present.
			if (!isNew && grBookInfo != null && grBookInfo.containsKey(ShowBookFieldNames.REVIEW_ID)) {
				reviewId = grBookInfo.getLong(ShowBookFieldNames.REVIEW_ID);
			}

			// Lists of shelf names and our best guess at the babelio canonical name
			ArrayList<String> shelves = new ArrayList<String>();
			ArrayList<String> canonicalShelves = new ArrayList<String>();

			// Build the list of shelves that we have for the book
			Cursor shelfCsr = dbHelper.getAllBookBookshelvesForBabelioCursor(bookId);
			try {
				int	shelfCol = shelfCsr.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_BOOKSHELF);
				// Collect all shelf names for this book
				while (shelfCsr.moveToNext()) {
					final String shelfName = shelfCsr.getString(shelfCol);
					final String canonicalShelfName = canonicalizeBookshelfName(shelfName);
					shelves.add(shelfName);
					canonicalShelves.add(canonicalShelfName);
				}
			} finally {
				shelfCsr.close();
			}

			// Add pseudo-shelf to match babelio because review.update does not seem to update them properly
			String pseudoShelf;
			if (books.getRead() == 0) {
				pseudoShelf = "To Read";
			} else {				
				pseudoShelf = "Read";
			}
			if (!shelves.contains(pseudoShelf)) {
				shelves.add(pseudoShelf);
				canonicalShelves.add(canonicalizeBookshelfName(pseudoShelf));
			}

			// Get the shelf names the book is currently on in babelio
			ArrayList<String> grShelves;
			if (!isNew && grBookInfo.containsKey(ShowBookFieldNames.SHELVES)) {
				grShelves = grBookInfo.getStringArrayList(ShowBookFieldNames.SHELVES);
			} else {
				grShelves = new ArrayList<String>();
			}

			// Remove from any shelves from babelio that are not in our local list
			for(String grShelf: grShelves) {
				if (!canonicalShelves.contains(grShelf)) {
					try {
						// Babelio does not seem to like removing books from the special shelves.
						if (! ( grShelf.equals("read") || grShelf.equals("to-read") || grShelf.equals("currently-reading") ) )
							this.removeBookFromShelf(grShelf, baId);
					} catch (BookNotFoundException e) {
						// Ignore for now; probably means book not on shelf anyway
					} catch (Exception e) {
						return ExportDisposition.error;
					}
				}
			}

			// Add shelves to babelio if they are not currently there
			for(String shelf: shelves) {
				final String canonicalShelfName = canonicalizeBookshelfName(shelf);
				if (grShelves == null || !grShelves.contains(canonicalShelfName) )
					try {
						reviewId = this.addBookToShelf(shelf, baId);								
					} catch (Exception e) {
						return ExportDisposition.error;
					}					
			}

			/* We should be safe always updating here because:
			 * - all books that are already added have a review ID, which we would have got from the bundle
			 * - all new books will be added to at least one shelf, which will have returned a review ID.
			 * But, just in case, we check the review ID, and if 0, we add the book to the 'Default' shelf.
			 */
			if (reviewId == 0) {
				try {
					reviewId = this.addBookToShelf("Default", baId);
				} catch (Exception e) {
					return ExportDisposition.error;
				}				
			}
			// Now update the remaining review details.
			try {
				// Do not sync Notes<->Review. We will add a 'Review' field later.
				//this.updateReview(reviewId, books.getRead() != 0, books.getReadEnd(), books.getNotes(), ((int)books.getRating()) );
				this.updateReview(reviewId, books.getRead() != 0, books.getReadEnd(), null, ((int)books.getRating()) );
			} catch (BookNotFoundException e) {
				return ExportDisposition.error;
			}

			return ExportDisposition.sent;
		} else {
			return ExportDisposition.noIsbn;
		}
		
	}

	/**
	 * Create canonical representation based on the best guess as to the babelio rules.
	 */
	public static String canonicalizeBookshelfName(String name) {
		StringBuilder canonical = new StringBuilder();
		name = name.toLowerCase();
		for(int i = 0; i < name.length() ; i++) {
			Character c = name.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				canonical.append(c);
			} else {
				canonical.append('-');
			}
		}
		return canonical.toString();
	}

	/**
	 * Wrapper to search for a book.
	 * 
	 * @param query		String to search for
	 * 
	 * @return	Array of BabelioWork objects
	 * 
	 * @throws ClientProtocolException
	 * @throws OAuthMessageSignerException
	 * @throws OAuthExpectationFailedException
	 * @throws OAuthCommunicationException
	 * @throws NotAuthorizedException
	 * @throws BookNotFoundException
	 * @throws IOException
	 * @throws NetworkException 
	 */
	public ArrayList<BabelioWork> search(String query) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

		if (!query.equals("")) {
			SearchBooksApiHandler searcher = new SearchBooksApiHandler(this);
			// Run the search
			return searcher.search(query);
		} else {
			throw new RuntimeException("No search criteria specified");
		}
		
	}

	/**
	 * Wrapper to search for a book.
	 * 
	 * @param query		String to search for
	 * 
	 * @return	Array of BabelioWork objects
	 * 
	 * @throws ClientProtocolException
	 * @throws OAuthMessageSignerException
	 * @throws OAuthExpectationFailedException
	 * @throws OAuthCommunicationException
	 * @throws NotAuthorizedException
	 * @throws BookNotFoundException
	 * @throws IOException
	 * @throws NetworkException 
	 */
	public Bundle getBookById(long bookId) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		if (bookId != 0) {
			ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(this);
			// Run the search
			return api.get(bookId, true);
		} else {
			throw new RuntimeException("No work ID specified");
		}
		
	}

	/**
	 * Wrapper to search for a book.
	 * 
	 * @param query		String to search for
	 * 
	 * @return	Array of BabelioWork objects
	 * 
	 * @throws ClientProtocolException
	 * @throws OAuthMessageSignerException
	 * @throws OAuthExpectationFailedException
	 * @throws OAuthCommunicationException
	 * @throws NotAuthorizedException
	 * @throws BookNotFoundException
	 * @throws IOException
	 * @throws NetworkException 
	 */
	public Bundle getBookByIsbn(String isbn) throws ClientProtocolException, NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
		if (isbn != null && isbn.length() > 0) {
			ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(this);
			// Run the search
			return api.get(isbn, true);
		} else {
			throw new RuntimeException("No work ID specified");
		}
		
	}
	
	/**
	 * Construct a full or partial date string based on the y/m/d fields.
	 * 
	 * @param yearField
	 * @param monthField
	 * @param dayField
	 * @param resultField
	 * @return
	 */
	public static String buildDate(Bundle data, String yearField, String monthField, String dayField, String resultField) {
		String date = null;
	    if (data.containsKey(yearField)) {
	    	date = String.format("%04d", data.getLong(yearField));
	        if (data.containsKey(monthField)) {
	        	date += "-" + String.format("%02d", data.getLong(monthField));
	            if (data.containsKey(dayField)) {
	            	date += "-" + String.format("%02d", data.getLong(dayField));
	            }
	        }
	        if (resultField != null && date != null && date.length() > 0)
	        	data.putString(resultField, date);
	    }
	    return date;
	}
	
	/** 
	 * Get the date at which the last babelio synchronization was run
	 * 
	 * @return	Last date
	 */
	public static Date getLastSyncDate() {
		String last = BookCatalogueApp.getAppPreferences().getString(LAST_SYNC_DATE,null);
		if (last == null || last.equals("")) {
			return null;
		} else {
			try {
				Date d = Utils.parseDate(last);
				return d;
			} catch (Exception e) {
				Logger.logError(e);
				return null;
			}
		}
	}

	/**
	 * Set the date at which the last babelio synchronization was run
	 * 
	 * @param d		Last date
	 */
	public static void setLastSyncDate(Date d) {
		if (d == null) {
			BookCatalogueApp.getAppPreferences().setString(LAST_SYNC_DATE, null);			
		} else {
			BookCatalogueApp.getAppPreferences().setString(LAST_SYNC_DATE, Utils.toSqlDateTime(d));
		}
	}

	/**
	 * Get the Id
	 * 
	 */
	public String getId() {

		return m_accessId;
	}

	/**
	 * Get the Password
	 * 
	 */
	public String getPass() {

		return m_accessPass;
	}

	/**
	 * Get the current PHPSESSID
	 * 
	 */
	public String getPhpsessid() {

		return m_accessPhpsessid;
	}
}

