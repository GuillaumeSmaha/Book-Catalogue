/*
 * @copyright 2012 Philip Warner
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

import android.R.bool;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.utils.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

/**
 * Activity to allow the user to authorize the application to access their babelio account and
 * to explain babelio.
 * 
 * @author Philip Warner
 *
 */
public class BabelioRegister extends BookCatalogueActivity {

	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.babelio_register);
			setupViews();
			Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		

			EditText idText = (EditText) findViewById(R.id.username_email);
			EditText passText = (EditText) findViewById(R.id.password);

			BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
			
			idText.setText(prefs.getString("Babelio.AccessToken.Id", ""));
			passText.setText(prefs.getString("Babelio.AccessToken.Pass", ""));
			
			if(BabelioManager.hasCredentials())
			{
				((Button) findViewById(R.id.login)).setText(R.string.update);
			}
			else
			{
				((Button) findViewById(R.id.login)).setText(R.string.login);
			}
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		Utils.initBackground(R.drawable.bc_background_gradient_dim, this, false);		
	}

	public void setupViews() {
		final Resources res = this.getResources();
		/* GR Reg Link */
		TextView register = (TextView) findViewById(R.id.babelio_url);
		register.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String url = res.getString(R.string.babelio_url);
				Uri uri = Uri.parse(url);
				Intent loadweb = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(loadweb); 
				return;
			}
		});
		
		/* Auth button */
		Button devkeyLink = (Button) findViewById(R.id.login);
		devkeyLink.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				EditText idText = (EditText) findViewById(R.id.username_email);
				EditText passText = (EditText) findViewById(R.id.password);
				String id = idText.getText().toString();
				String pass = passText.getText().toString();
				
				requestAuthorizationInBackground(BabelioRegister.this, id, pass);
				return;
			}
		});

		/* Forget credentials */
		boolean hasCred = BabelioManager.hasCredentials();
		View blurb = findViewById(R.id.forget_blurb);
		Button blurb_button = (Button)findViewById(R.id.forget);
		if (hasCred) {
			blurb.setVisibility(View.VISIBLE);
			blurb_button.setVisibility(View.VISIBLE);
			blurb_button.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					BabelioManager.forgetCredentials();

					finish();
					startActivity(getIntent());
				}});
		} else {
			blurb.setVisibility(View.GONE);
			blurb_button.setVisibility(View.GONE);
		}
	}

	/**
	 * Called by button click to start a non-UI-thread task to do the work.
	 */
	public static void requestAuthorizationInBackground(final FragmentActivity activity, final String id, final String pass) {
		
		FragmentTask task = new FragmentTask() {
			private int mMessage = 0;

			/**
			 * Call the static method to start the web page; this can take a few seconds
			 */
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				mMessage = requestAuthorizationImmediate(activity, id, pass);
			}

			/**
			 * Display any error message
			 */
			@Override
			public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
				if (mMessage != 0)
				{
					fragment.showToast(mMessage);
				
					activity.finish();
					activity.startActivity(activity.getIntent());
				}
			}

		};

		// Get the fragment to display task progress
		SimpleTaskQueueProgressFragment.runTaskWithProgress(activity, R.string.connecting_to_web_site, task, true, 0);
	}

	/**
	 * Static method to request authorization from babelio.
	 */
	private static int requestAuthorizationImmediate(Context context, String id, String pass) {
		
		BabelioManager baMgr = new BabelioManager(false);
		
		boolean hasCredit = BabelioManager.hasCredentials();
		baMgr.registerUsernamePassword(id, pass);
		
		Log.d("TEST", "m_hasValidCredentials = "+(BabelioManager.m_hasValidCredentials?"ok":"ko"));
		// This next step can take several seconds....
		try {
			baMgr.validateCredentials();
			if(baMgr.hasValidCredentials())
			{
				if(hasCredit)
				{
					return R.string.username_or_email_update;
				}
				else
				{
					return R.string.username_or_email_saved;
				}
			}
		} catch (com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NetworkException e) {

			return R.string.babelio_auth_failed;
		}
		
		return 0;
	}
}
