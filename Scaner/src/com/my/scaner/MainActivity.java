package com.my.scaner;

import java.io.IOException;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private MainActivity context;

	private String result = "";// 扫描结果

	private long exitTime = 0;// 退出时间

	private boolean isPreview = false;// 标识预览状态

	private Point point = new Point();// 屏幕宽高信息

	private Camera mCamera;
	private Parameters cameraMeters;// 相机参数
	private Size size;// 相机尺寸
	private byte[] buff = new byte[3110400];

	private ImageView cancel, torch, share;
	private RelativeLayout resultArea;
	private TextView barcodeResult;
	private TextView areaBack;

	private SurfaceView barcodeSurface;

	private ImageScanner scanner;// 解析工具
	private Image image;// 存放扫描到的二维码图片

	private Handler autoHandler = new Handler();

	private OnClickListener l = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.barcode_cancel:
				context.finish();
				break;
			case R.id.barcode_torch:
				if (hasFlash()) {
					if (Parameters.FLASH_MODE_OFF.equals(cameraMeters
							.getFlashMode())) {
						cameraMeters.setFlashMode(Parameters.FLASH_MODE_TORCH);
						mCamera.setParameters(cameraMeters);
						torch.setImageDrawable(getResources().getDrawable(
								R.drawable.barcode_torch_on));
					} else if (Parameters.FLASH_MODE_TORCH.equals(cameraMeters
							.getFlashMode())) {
						cameraMeters.setFlashMode(Parameters.FLASH_MODE_OFF);
						mCamera.setParameters(cameraMeters);
						torch.setImageDrawable(getResources().getDrawable(
								R.drawable.barcode_torch_off));
					}
				}
				break;
			case R.id.barcode_share:
				if (!"".equals(result)) {
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, result);
					startActivity(intent);
				} else {
					Toast.makeText(context, "啊哦，内容还素空的哦  ⊙０⊙  ~",
							Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.barcode_result_back:
				reView();
				break;
			}
		}
	};

	static {
		System.loadLibrary("iconv");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = this;
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		init();
		getWindowManager().getDefaultDisplay().getSize(point);
		if (hasCamera()) {
			scanner = new ImageScanner();
			scanner.setConfig(0, Config.X_DENSITY, 3);
			initSurface();
		} else {
			context.finish();
		}
	}

	private void init() {
		cancel = (ImageView) findViewById(R.id.barcode_cancel);
		torch = (ImageView) findViewById(R.id.barcode_torch);
		share = (ImageView) findViewById(R.id.barcode_share);
		barcodeSurface = (SurfaceView) findViewById(R.id.barcode_surface);

		resultArea = (RelativeLayout) findViewById(R.id.barcode_result_area);
		areaBack = (TextView) findViewById(R.id.barcode_result_back);
		barcodeResult = (TextView) findViewById(R.id.barcode_result);
		resultArea.setVisibility(View.INVISIBLE);
		cancel.setOnClickListener(l);
		torch.setOnClickListener(l);
		share.setOnClickListener(l);
		areaBack.setOnClickListener(l);
	}

	@SuppressWarnings("deprecation")
	private void initSurface() {
		mCamera = Camera.open();
		if (mCamera == null) {
			Camera.open().release();
			mCamera = Camera.open();
		}
		mCamera.addCallbackBuffer(buff);
		cameraMeters = mCamera.getParameters();
		size = cameraMeters.getPreviewSize();
		image = new Image(size.width, size.height, "Y800");
		barcodeSurface.getHolder().setFixedSize(point.x, point.y);
		barcodeSurface.getHolder().setType(
				SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		barcodeSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				if (mCamera != null) {
					if (isPreview) {
						mCamera.stopPreview();
						isPreview = false;
					}
				}
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				Log.e("", "surfaceCreated");
				mCamera.setDisplayOrientation(90);
				try {
					mCamera.setPreviewDisplay(barcodeSurface.getHolder());
				} catch (IOException e) {
					e.printStackTrace();
				}
				mCamera.setPreviewCallbackWithBuffer(previewCB);
				mCamera.startPreview();
				mCamera.autoFocus(autoFocus);
				isPreview = true;
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {

			}
		});
	}

	private Runnable focusRun = new Runnable() {
		@Override
		public void run() {
			if (isPreview) {
				mCamera.autoFocus(autoFocus);
			}
		}
	};

	private AutoFocusCallback autoFocus = new AutoFocusCallback() {

		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			autoHandler.postDelayed(focusRun, 2000);
		}
	};

	private PreviewCallback previewCB = new PreviewCallback() {

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			Log.e("", "PreviewCallback");
			image.setData(data);
			if (scanner.scanImage(image) != 0) {
				setResult();
			}
			mCamera.addCallbackBuffer(buff);
		}
	};

	private void reView() {
		result = "";
		resultArea.setVisibility(View.INVISIBLE);
		mCamera.setPreviewCallbackWithBuffer(previewCB);
	}

	private void setResult() {
		mCamera.setPreviewCallback(null);
		SymbolSet syms = scanner.getResults();
		for (Symbol sym : syms) {
			result = sym.getData();
		}
		Vibrator vibra = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		vibra.vibrate(100);
		resultArea.setVisibility(View.VISIBLE);
		barcodeResult.setText(result);
		if (matchType(result)) {
			barcodeResult.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
			barcodeResult.setOnClickListener(browserListener);
		}
	}

	private boolean matchType(String s) {
		if ("http://".equals(result.substring(0, 7))) {
			return true;
		}
		if ("https://".equals(result.substring(0, 8))) {
			return true;
		}
		if ("ftp://".equals(result.substring(0, 6))) {
			return true;
		}
		return false;
	}

	private OnClickListener browserListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse(result));
				startActivity(intent);
			} catch (Exception e) {
				Log.e("" + e.getLocalizedMessage(), e.getMessage());
			}
		}
	};

	private boolean hasCamera() {
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			if (!getPackageManager().hasSystemFeature(
					PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
				Toast.makeText(context, "您的摄像头不支持自动对焦，扫描效果可能有偏差",
						Toast.LENGTH_SHORT).show();
			}
			return true;
		} else {
			Toast.makeText(context, "未检测到摄像头", Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	private boolean hasFlash() {
		if (getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA_FLASH)) {
			return true;
		}
		Toast.makeText(context, "您的相机没有闪光灯", Toast.LENGTH_SHORT).show();
		return false;
	}

	private void releaseAll() {
		if (mCamera != null) {
			isPreview = false;
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
	}

	private Window window;// 菜单Window
	private View v;// 菜单View
	private AlertDialog dialog;// 菜单Dialog
	private TextView about, menuBack;// 菜单按钮

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (View.VISIBLE == resultArea.getVisibility()) {
				reView();
				return true;
			}
			if ((System.currentTimeMillis() - exitTime) < 2000) {
				context.finish();
			} else {
				exitTime = System.currentTimeMillis();
				Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
			}
			break;
		case KeyEvent.KEYCODE_MENU:
			dialog = new AlertDialog.Builder(context).show();
			window = dialog.getWindow();
			v = LayoutInflater.from(context).inflate(R.layout.menu, null);
			window.setContentView(v);
			window.setWindowAnimations(R.style.AnimBottom);
			window.setGravity(Gravity.BOTTOM);
			window.setLayout(LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT);
			about = (TextView) v.findViewById(R.id.menu_about);
			menuBack = (TextView) v.findViewById(R.id.menu_back);
			about.setOnClickListener(menuListener);
			menuBack.setOnClickListener(menuListener);
			break;
		}
		return true;
	}

	private OnClickListener menuListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.menu_about:
				try {
					new AlertDialog.Builder(context)
							.setMessage(
									"版本号："
											+ context
													.getPackageManager()
													.getPackageInfo(
															context.getPackageName(),
															0).versionName
											+ "\n作者：杨威")
							.setPositiveButton("OK", null).show();
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
				dialog.dismiss();
				break;
			case R.id.menu_back:
				dialog.dismiss();
				break;
			}
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		releaseAll();
		super.onDestroy();
	}
}
