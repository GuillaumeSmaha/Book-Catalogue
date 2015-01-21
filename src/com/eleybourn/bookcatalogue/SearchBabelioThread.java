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

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.babelio.BabelioManager;
import com.eleybourn.bookcatalogue.babelio.BabelioManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.babelio.BabelioWork;
import com.eleybourn.bookcatalogue.utils.Logger;

/**
 * SearchManager for babelio.
 * 
 * @author Philip Warner
 */
public class SearchBabelioThread extends SearchThread {

	/**
	 * Constructor.
	 * 
	 * @param manager
	 * @param taskHandler
	 * @param author
	 * @param title
	 * @param isbn
	 * @param fetchThumbnail
	 */
	public SearchBabelioThread(TaskManager manager,
			String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		//
		//	babelio
		//
		this.doProgress(getString(R.string.searching_babelio), 0);

		BabelioManager baMgr = new BabelioManager(true);
		try {
			if (mIsbn != null && mIsbn.trim().length() > 0) {
				mBookData = baMgr.getBookByIsbn(mIsbn);
			} else {
				ArrayList<BabelioWork> list = baMgr.search(mAuthor + " " + mTitle);
				if (list != null && list.size() > 0) {
					BabelioWork w = list.get(0);
					mBookData = baMgr.getBookById(w.bookId);
				}
			}
		} catch (BookNotFoundException bnf) {
			// Ignore; not a problem here
		} catch (Exception e) {
			Logger.logError(e);
			showException(R.string.searching_babelio, e);
		}
	}

	/**
	 * Get the global ID for the babelio search manager
	 */
	@Override
	public int getSearchId() {
		return SearchManager.SEARCH_BABELIO;
	}

}
