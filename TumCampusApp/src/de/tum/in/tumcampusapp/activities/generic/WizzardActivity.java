package de.tum.in.tumcampusapp.activities.generic;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import de.tum.in.tumcampus.R;
import de.tum.in.tumcampusapp.auxiliary.PersonalLayoutManager;

public class WizzardActivity extends Activity {
	private Intent intentForNextActivity;
	private Intent intentForPreviousActivity;

	@Override
	public void onBackPressed() {
		startPreviousActivity();
	}

	public void onClickSkip(View view) {
		startNextActivity();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_activity_wizzard, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_exit:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		PersonalLayoutManager.setColorForId(this, R.id.pager_title_strip);
	}

	public void setIntentForNextActivity(Intent intent) {
		this.intentForNextActivity = intent;
	}

	public void setIntentForPreviousActivity(Intent intent) {
		this.intentForPreviousActivity = intent;
	}

	public void startNextActivity() {
		finish();
		if (intentForNextActivity != null) {
			startActivity(intentForNextActivity);
		}
	}

	public void startPreviousActivity() {
		finish();
		if (intentForPreviousActivity != null) {
			startActivity(intentForPreviousActivity);
		}
	}
}
