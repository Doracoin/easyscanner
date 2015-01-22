package com.my.scaner;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

public class MToast extends Toast {

	public MToast(Context context) {
		super(context);
	}
	
	@Override
	public void setView(View view) {
		super.setView(view);
	}
	
}
