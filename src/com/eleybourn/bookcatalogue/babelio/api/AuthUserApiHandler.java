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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.accounts.NetworkErrorException;
import android.util.Log;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;

/**
 * API handler for the authUser call. Just gets the current user details.
 * 
 * @author Guillaume Smaha
 */
public class AuthUserApiHandler extends ApiHandler {


	private long mUserId = 0;
	private String mPhpsessid = null;
	
	/**
	 * Constructor. Setup the filters.
	 * 
	 * @param manager
	 */
	public AuthUserApiHandler(BabelioManager manager) {
		super(manager);
	}

	/**
	 * Call the API.
	 * 
	 * @return		Resulting User ID, 0 if error/none.
	 * @throws UnsupportedEncodingException 
	 */
	public long getAuthUser() throws UnsupportedEncodingException {

		// Setup API call
		HttpPost post = new HttpPost("http://www.babelio.com/connection.php");

		HttpParams params = new BasicHttpParams();
		params.setParameter("http.protocol.handle-redirects", false);
		post.setParams(params);
		
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);  
        nameValuePairs.add(new BasicNameValuePair("Login", mManager.getId()));
		nameValuePairs.add(new BasicNameValuePair("Password", mManager.getPass()));
		UrlEncodedFormEntity urlEntity = new UrlEncodedFormEntity(nameValuePairs);
		post.setEntity(urlEntity);
		
		mUserId = 0;
		try {
	        HttpResponse response = mManager.executeResponse(post, false);

	        String html = EntityUtils.toString(response.getEntity(), "UTF-8");
	        
	        Pattern patternError = Pattern.compile("<span style=\"color:red;\" >Identifiant ou mot de passe incorrect.</span>");
	        Matcher matcherError = patternError.matcher(html);
	        boolean findError = matcherError.find();
	        if(findError)
	        {
	            throw new NetworkErrorException();
	        }
	        else
	        {
		        if(html.indexOf("<div id=\"footer_xl\" style=\"clear:both;\">") == 0)
		        {
			        mPhpsessid = null;
			        
			        Header[] headers = response.getHeaders("Set-Cookie");
			        
			        for(int i = 0 ; i < headers.length ; i ++)
			        {
			        	Header header = headers[i];
			        	header.getValue();
			        	String[] cookies = header.getValue().trim().split(";");
			        	String[] part = cookies[0].trim().split("=");
			        	String name = part[0].trim();
			        	String value = part[1].trim();
	
			        	if(name.equals("PHPSESSID"))
			        	{
			        		mPhpsessid = value;
			        	}
			        }
			        
			        if(mPhpsessid != null)
			        {
			            Log.d("TEST", "PHPSESSID = "+mPhpsessid);

			        	//Get profile
			    		HttpPost postProfil = new HttpPost("http://www.babelio.com/monprofil.php");
			    		
			    		String sessionCookie = "PHPSESSID="+mPhpsessid;
			    		postProfil.setHeader("Cookie", sessionCookie);
			    		
			    		String htmlProfil = mManager.executeRaw(postProfil, false);
			    		
				        Pattern pattern = Pattern.compile("<input type=\"hidden\" id=\"hid_user\" value=\"([0-9]+)\">");
				        Matcher matcher = pattern.matcher(htmlProfil);
				        boolean findMatch = matcher.find();
				        if(findMatch)
				        {
				        	mUserId = Long.parseLong(matcher.group(1));
				        }
				        else
				        {
				            throw new NetworkErrorException();
				        }
			        }
			        else
			        {
			            throw new NetworkErrorException();
			        }
		        }
		        else
		        {
		            throw new NetworkErrorException();
		        }
	        }
	        
	        
	        // Return user found.
	        return mUserId;
		} catch (Exception e) {
			return 0;
		}
	}

	public long getUserid() {
		return mUserId;
	}

	public String getPhpsessid() {
		return mPhpsessid;
	}
	
}
