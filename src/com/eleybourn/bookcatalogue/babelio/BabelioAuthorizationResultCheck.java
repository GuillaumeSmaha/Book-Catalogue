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

import net.philipwarner.taskqueue.QueueManager;
import android.content.Context;
import android.content.Intent;

import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.NotAuthorizedException;

/**
 * Simple class to run in background and verify babelio credentials then
 * display a notification based on the result.
 * 
 * This task is run as the last part of the babelio auth process.
 * 
 * Runs in background because it can take several seconds.
 * 
 * @author Philip Warner
 */
public class BabelioAuthorizationResultCheck extends BabelioGenericTask {

	private static final long serialVersionUID = -2381684299811130760L;

	public BabelioAuthorizationResultCheck() {
		super(BookCatalogueApp.getResourceString(R.string.babelio_auth_check));
	}

	@Override
	public boolean run(QueueManager manager, Context c) {
		BabelioManager baMgr = new BabelioManager(true);
		// Bring the app to the front using the launcher intent
		Intent i = new Intent(c, StartupActivity.class);
		i.setAction("android.intent.action.MAIN");
		i.addCategory(Intent.CATEGORY_LAUNCHER);
	    try {		    	
		    if (baMgr.hasValidCredentials())
				BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.authorized), 
										c.getString(R.string.babelio_auth_successful), i);
			else
				BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.not_authorized), 
										c.getString(R.string.babelio_auth_failed), i);
	    } catch (Exception e) {
	    	BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
										c.getString(R.string.not_authorized), 
										c.getString(R.string.babelio_auth_error) + " " + c.getString(R.string.if_the_problem_persists), i);		    	
	    }

		return true;
	}

	@Override
	public long getCategory() {
		return BcQueueManager.CAT_BABELIO_AUTH;
	}

}
