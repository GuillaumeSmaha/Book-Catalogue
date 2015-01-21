package com.eleybourn.bookcatalogue.babelio;

import net.philipwarner.taskqueue.QueueManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.BcQueueManager;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.compat.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;

public class BabelioUtils {
	/**
	 * Show the babelio options list
	 */
	public static void showBabelioOptions(final BookCatalogueActivity activity) {
		LayoutInflater inf = activity.getLayoutInflater();
		View root = inf.inflate(R.layout.babelio_options_list, null);

		final AlertDialog baDialog = new AlertDialog.Builder(activity).setView(root).create();
		baDialog.setTitle(R.string.select_an_action);
		baDialog.show();
		
		/* Babelio SYNC Link */
		{
			View v = baDialog.findViewById(R.id.sync_with_babelio_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					//BabelioUtils.importAllFromBabelio(activity, true);
					baDialog.dismiss();
				}
			});
		}

		/* Babelio IMPORT Link */
		{
			View v = baDialog.findViewById(R.id.import_all_from_babelio_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					//BabelioUtils.importAllFromBabelio(activity, false);
					baDialog.dismiss();
				}
			});
		}

		/* Babelio EXPORT Link */
		{
			View v = baDialog.findViewById(R.id.send_books_to_babelio_label);
			// Make line flash when clicked.
			v.setBackgroundResource(android.R.drawable.list_selector_background);
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					//sendBooksToBabelio(activity);
					baDialog.dismiss();
				}
			});
		}
	}
	
	/**
	 * Start a background task that imports books from babelio.
	 * 
	 * We use a FragmentTask so that network access does not occur in the UI thread.
	 */
	public static void importAllFromBabelio(final BookCatalogueActivity context, final boolean isSync) {
		
		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {

				if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_BABELIO_IMPORT_ALL)) {
					fragment.showToast(R.string.requested_task_is_already_queued);
					return;
				}
				if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_BABELIO_EXPORT_ALL)) {
					fragment.showToast(R.string.export_task_is_already_queued);
					return;
				}

				int msg = checkBabelioAuth();
				if (msg == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.babelioAuthAlert(context);
						}});
					return;
				} else if (msg != 0) {
					fragment.showToast(msg);
					return;
				}

				if (!fragment.isCancelled()) {
					QueueManager.getQueueManager().enqueueTask(new BabelioImportAllTask(isSync), BcQueueManager.QUEUE_MAIN, 0);
					fragment.showToast(R.string.task_has_been_queued_in_background);					
				}
			}
		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
	}

	/**
	 * Check that babelio is authorized for this app, and optionally allow user to request auth or more info
	 * 
	 * This does network comms and should not be called in the UI thread.
	 * 
	 * @return	Flag indicating OK
	 */
	private static int checkBabelioAuth() {
		// Make sure GR is authorized for this app
		BabelioManager baMgr = new BabelioManager(true);

		if (!BabelioManager.hasCredentials() || !baMgr.getValidCredentials()) {
			return -1;
		}

		return 0;		
	}
	
	/**
	 * Check that no other sync-related jobs are queued, and that babelio is authorized for this app.
	 * 
	 * This does network comms and should not be called in the UI thread.
	 * 
	 * @return	Flag indicating OK
	 */
	private static int checkCanSendToBabelio() {
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_BABELIO_EXPORT_ALL)) {
			return R.string.requested_task_is_already_queued;
		}
		if (BcQueueManager.getQueueManager().hasActiveTasks(BcQueueManager.CAT_BABELIO_IMPORT_ALL)) {
			return R.string.import_task_is_already_queued;
		}

		return checkBabelioAuth();
	}

	/**
	 * Start a background task that exports all books to babelio.
	 */
	private static void sendToBabelio(final FragmentActivity context, final boolean updatesOnly) {
		FragmentTask task = new FragmentTaskAbstract() {
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				int msg = checkCanSendToBabelio();
				if (msg == 0) {
					QueueManager.getQueueManager().enqueueTask(new BabelioSendAllBooksTask(updatesOnly), BcQueueManager.QUEUE_MAIN, 0);
					msg = R.string.task_has_been_queued_in_background;
				}
				setState(msg);
			}

			@Override
			public void onFinish(final SimpleTaskQueueProgressFragment fragment, Exception exception) {
				final int msg = getState();
				if (msg == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.babelioAuthAlert(fragment.getActivity());
						}
					});
					return;
				} else {
					fragment.showToast(msg);
				}

			}
		};
		SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
	}
	
	/**
	 * Ask the user which books to send, then send them.
	 * 
	 * Optionally, display a dialog warning the user that babelio authentication is required; gives them
	 * the options: 'request now', 'more info' or 'cancel'.
	 */
	
	public static void sendBooksToBabelio(final BookCatalogueActivity ctx) {

		FragmentTaskAbstract task = new FragmentTaskAbstract() {
			/**
			 * Just check we can send. If so, onFinish() will be called.
			 */
			@Override
			public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
				int msg = BabelioUtils.checkCanSendToBabelio();
				setState(msg);
			}

			@Override
			public void onFinish(final SimpleTaskQueueProgressFragment fragment, Exception exception) {
				if (getState() == 0) {
					final FragmentActivity context = fragment.getActivity();
					if (context != null) {
						// Get the title		
						final AlertDialog alertDialog = new AlertDialog.Builder(context).setTitle(R.string.send_books_to_babelio).setMessage(R.string.send_books_to_babelio_blurb).create();
		
						alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
						alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getResources().getString(R.string.send_updated), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
								BabelioUtils.sendToBabelio(context, true);
							}
						});
						
						alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getResources().getString(R.string.send_all), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
								BabelioUtils.sendToBabelio(context, false);
							}
						});
		
						alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								alertDialog.dismiss();
							}
						}); 
		
						alertDialog.show();						
					}
				} else if (getState() == -1) {
					fragment.post(new Runnable() {

						@Override
						public void run() {
							StandardDialogs.babelioAuthAlert(fragment.getActivity());
						}
					});
					return;
				} else {
					fragment.showToast(getState());
				}
			}
		};
		// Run the task
		SimpleTaskQueueProgressFragment.runTaskWithProgress(ctx, R.string.connecting_to_web_site, task, true, 0);

	}
	

}
