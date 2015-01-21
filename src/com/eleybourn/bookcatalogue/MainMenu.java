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

package com.eleybourn.bookcatalogue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oauth.signpost.http.HttpResponse;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioUtils;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils;
import com.eleybourn.bookcatalogue.utils.AlertDialogUtils.AlertDialogItem;
import com.eleybourn.bookcatalogue.utils.HintManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implement the 'Main Menu' for BookCatalogue. This is one of two possible start screens.
 * 
 * - 'My Books' -> preferred bookshelf view 
 * - 'Add Book' -> Add Method Dialog 
 * - 'Loan/Return/Edit Book'
 * - 'Search' 
 * - 'Admin & Preferences'
 * - Help
 * - Export/Import/Sync
 * 
 * @author Philip Warner
 * 
 */
public class MainMenu extends BookCatalogueActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Get the preferences and extras.
		BookCataloguePreferences prefs = BookCatalogueApp.getAppPreferences();
		Bundle extras = this.getIntent().getExtras();

		// Handle startup specially.
		if (extras != null && extras.containsKey("startup") && extras.getBoolean("startup")) {
			// Check if we really want to start this activity.
			if (prefs.getStartInMyBook()) {
				doMyBooks();
				finish();
				return;
			}
		}

		// If we get here, we're meant to be in this activity.
		setContentView(R.layout.main_menu);

		// Display/hide the 'classic' my books item
		int classicVis;
		if (prefs.getBoolean(BookCataloguePreferences.PREF_INCLUDE_CLASSIC_MY_BOOKS, false))
			classicVis = View.VISIBLE;
		else
			classicVis = View.GONE;

		View v = findViewById(R.id.my_books_classic_label);
		v.setVisibility(classicVis);

		// Setup handlers for items. It's just a menu after all.
		setOnClickListener(R.id.my_books_label, mBrowseHandler);
		setOnClickListener(R.id.my_books_classic_label, mMyBooksHandler);
		setOnClickListener(R.id.add_book_label, mAddBookHandler);
		setOnClickListener(R.id.loan_label, mLoanBookHandler);
		setOnClickListener(R.id.search_label, mSearchHandler);
		setOnClickListener(R.id.administration_label, mAdminHandler);
		setOnClickListener(R.id.about_label, mAboutHandler);
		setOnClickListener(R.id.help_label, mHelpHandler);
		setOnClickListener(R.id.donate_label, mDonateHandler);
		// Goodreads will be shown/hidden in onResume()
		setOnClickListener(R.id.goodreads_label, mGoodreadsHandler);
		setOnClickListener(R.id.babelio_label, mBabelioHandler);
		
		
		
		if (savedInstanceState == null)
			HintManager.displayHint(this, R.string.hint_startup_screen, null);

		Utils.initBackground(R.drawable.bc_background_gradient, this, true);
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();

		if (CatalogueDBAdapter.DEBUG_INSTANCES)
			CatalogueDBAdapter.dumpInstances();

		final boolean showGr = GoodreadsManager.hasCredentials();

		View grItem = findViewById(R.id.goodreads_label);
		if (showGr) {
			grItem.setVisibility(View.VISIBLE);
		} else {
			grItem.setVisibility(View.GONE);
		}

		
		final boolean showBa = BabelioManager.hasCredentials();

		View baItem = findViewById(R.id.babelio_label);
		if (showBa) {
			baItem.setVisibility(View.VISIBLE);
		} else {
			baItem.setVisibility(View.GONE);
		}

		Utils.initBackground(R.drawable.bc_background_gradient, this, true);	

		/**
		 * RELEASE: DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
		 * there are still non-fatal anomalies.
		 */
		// CatalogueDBAdapter.printReferenceCount("MainMenu resumed");
	}

	/**
	 * Add Book Menu Handler
	 */
	private OnClickListener mAddBookHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			ArrayList<AlertDialogItem> items = new ArrayList<AlertDialogItem>();
			items.add( new AlertDialogItem(getString(R.string.scan_barcode_isbn), mCreateBookScan) );
			items.add( new AlertDialogItem(getString(R.string.enter_isbn), mCreateBookIsbn) );
			items.add( new AlertDialogItem(getString(R.string.search_internet), mCreateBookName) );
			items.add( new AlertDialogItem(getString(R.string.add_manually), mCreateBookManually) );
			AlertDialogUtils.showContextDialogue(MainMenu.this, getString(R.string.menu_insert), items);
		}
	};

	/**
	 * Loan Book Menu Handler
	 */
	private OnClickListener mLoanBookHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			//Intent i = new Intent(this, BookCatalogue.class);
			//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			//startActivity(i);
		}
	};

	/**
	 * Search Menu Handler
	 */
	private OnClickListener mSearchHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, SearchCatalogue.class);
			//i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
	};

	/**
	 * Admin Menu Handler
	 */
	private OnClickListener mAdminHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationFunctions.class);
			startActivity(i);			
		}
	};

	/**
	 * Browse Handler
	 */
	private OnClickListener mBrowseHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, BooksOnBookshelf.class);
			startActivity(i);
		}
	};

	/**
	 * Goodreads Menu Handler
	 */
	private OnClickListener mGoodreadsHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			GoodreadsUtils.showGoodreadsOptions(MainMenu.this);
		}
	};

	/**
	 * Babelio Menu Handler
	 */
	private OnClickListener mBabelioHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			BabelioUtils.showBabelioOptions(MainMenu.this);
		}
	};

	/**
	 * About Menu Handler
	 */
	private OnClickListener mAboutHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationAbout.class);
			startActivity(i);
		}
	};

	/**
	 * Help Menu Handler
	 */
	private OnClickListener mHelpHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, Help.class);
			startActivity(i);			
		}
	};

	/**
	 * Donate Menu Handler
	 */
	private OnClickListener mDonateHandler = new OnClickListener() {
		@Override public void onClick(View v) {
			Intent i = new Intent(MainMenu.this, AdministrationDonate.class);
			startActivity(i);
		}
	};

	/**
	 * Utility routine to set the OnClickListener for a given view item.
	 * 
	 * @param id		Sub-View ID
	 * @param l			Listener
	 */
	private void setOnClickListener(int id, OnClickListener l) {
		View v = this.findViewById(id);
		v.setOnClickListener(l);
		v.setBackgroundResource(android.R.drawable.list_selector_background);
	}

	/**
	 * 'My Books' menu item.
	 */
	private OnClickListener mMyBooksHandler = new OnClickListener() {
		@Override public void onClick(View v) { doMyBooks(); }
	};
	/**
	 * Method to start the book catalogue activity. Can be called from onCreate as well
	 * as from a menu item.
	 */
	private void doMyBooks() {
		Intent i = new Intent(this, BookCatalogueClassic.class);
		startActivity(i);
	}

	/**
	 * Add Book Sub-Menu: Load the BookEdit Activity
	 */
	private Runnable mCreateBookManually = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookEdit.class);
			startActivity(i);
		}
	};
	
	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity
	 */
	private Runnable mCreateBookIsbn = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "isbn");
			startActivity(i);
		}
	};
	
	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity
	 */
	private Runnable mCreateBookName = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "name");
			startActivity(i);
		}
	};

	/**
	 * Add Book Sub-Menu: Load the Search by ISBN Activity to begin scanning.
	 */
	private Runnable mCreateBookScan = new Runnable() {
		@Override
		public void run() {
			Intent i = new Intent(MainMenu.this, BookISBNSearch.class);
			i.putExtra(BookISBNSearch.BY, "scan");
			startActivity(i);
		}
	};

	/**
	 * Cleanup!
	 */
	@Override 
	public void onDestroy() {
		super.onDestroy();
	}
}
