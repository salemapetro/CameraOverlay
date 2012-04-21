package salema.cameraoverlay;

import java.io.IOException;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class CameraOverlayActivity extends Activity implements
	SurfaceHolder.Callback, OnLongClickListener, OnTouchListener {

	private static final String TAG = "CameraOverlay";
	private static final String[] actionNames = { "DOWN",
	                                              "UP",
	                                              "MOVE",
	                                              "CANCEL",
	                                              "OUTSIDE",
	                                              "POINTER_DOWN",
	                                              "POINTER_UP",
	                                              "7?",
	                                              "8?",
	                                              "9?" };
	private Camera camera;
	private Camera.Parameters camParams;
	private TextView overlay;
	private boolean isPreviewRunning;
	private boolean isPinching;
	private boolean isZoomable;
	private int maxZoom;
	private int screenSize;
	private int screenHeight;
	private float startSpacing;

	/**
	 * @TODO: See http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window win = getWindow();

		win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		win.setFormat(PixelFormat.TRANSLUCENT);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.main);

		overlay = (TextView) findViewById(R.id.overlay);
		if (overlay != null) {
			overlay.setTextColor(overlay.getTextColors().withAlpha(150));
			overlay.bringToFront();
			trace("starting trace");
		}

		SurfaceView camView = (SurfaceView) findViewById(R.id.camera);
		if (camView != null) {
			Display display = getWindowManager().getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);
			screenHeight = size.y;
			screenSize = Math.min(size.x, size.y);

			// Listen for `create', `change', and `delete' notifications on
			// underlying surface.
			camView.getHolder().addCallback(this);
			bindMotionListeners((View) camView);
		}
	}

	public void bindMotionListeners(View view) {
		if (view != null) {
			view.setLongClickable(true);
			view.setOnLongClickListener(this);
			view.setOnTouchListener(this);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		try {
			camera = Camera.open();
			camParams = camera.getParameters();
			isZoomable = camParams.isZoomSupported();
			maxZoom = camParams.getMaxZoom();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w,
	                           int h) {
		if (camera == null) {
			return;
		}

		// Calling stopPreview will cause a crash if preview is not running.
		if (isPreviewRunning) {
			camera.stopPreview();
		}

		camera.getParameters().setPreviewSize(w, h);

		try {
			camera.setPreviewDisplay(holder);
		} catch (IOException e) {
			e.printStackTrace();
		}

		camera.startPreview();

		isPreviewRunning = true;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if (camera != null) {
			if (isPreviewRunning) {
				camera.stopPreview();
			}
			camera.release();
			camera = null;
		}

		isPreviewRunning = false;
	}

	public boolean onLongClick(View view) {
		if (!isPinching) {
			trace("Long click called on " + view.toString());
		}

		return false;
	}

	public boolean onTouch(View view, MotionEvent ev) {
		dumpEvent(ev);

		if (isPointerDown(ev)) {
			startSpacing = getSpacing(ev);
		}

		isPinching = isPinching(ev);

		if (isPinching) {
			float spacingDelta = getSpacing(ev) - startSpacing;
			float factor = (screenSize == 0) ? 0 : spacingDelta / screenSize;
			factor = mathBounds(factor, -1, 1);
			int change = (int) (5 * factor);

			trace(String.valueOf(change));
			// changeZoom(change);
		}
		
		// Permit the event to continue on to longClick.
		return false;
	}
	
	private void changeZoom(int change) {
		int zoom = camParams.getZoom();
		int min = 0;
		int max = 5;

		try {
			camParams.setZoom((int) mathBounds((float) zoom + change, min,
				max));
			camera.setParameters(camParams);
		} catch (Exception e) {
			trace(e.getMessage());
		}
	}

	private void logCat(Object ... objects) {
		StringBuilder str = new StringBuilder();
		
		for (Object o : objects) {
			str.append(o.toString()).append(" ");
		}
		
		Log.v(TAG, str.append("\n").toString());
	}
	
	private StringBuilder traceBuffer = new StringBuilder();

	private void trace(String msg) {
		if (overlay != null) {
			msg = msg + "\n";
			traceBuffer.append(msg);
			
			//logCat(overlay.getHeight(), screenHeight);
			
			if (overlay.getHeight() >= screenHeight) {
				overlay.setText(traceBuffer.toString());
				traceBuffer.delete(0, traceBuffer.length());
			} else {
				overlay.setText(msg + overlay.getText());
			}
		}
	}

	private void dumpEvent(MotionEvent ev) {
		int code = getActionCode(ev);

		StringBuilder str = new StringBuilder();

		str.append("ACTION_").append(actionNames[code]);

		// Handle detection of motion of second pointer.
		if (code == MotionEvent.ACTION_POINTER_UP ||
		    code == MotionEvent.ACTION_POINTER_DOWN) {
			int pointerId = MotionEvent.ACTION_POINTER_INDEX_MASK;
			str.append(" (").append(pointerId).append(")");
		}

		str.append(" [");

		int numPointers = ev.getPointerCount();

		for (int i = 0; i < numPointers; i++) {
			str.append(" p").append(ev.getPointerId(i));
			str.append("=").append((int) ev.getX(i));
			str.append(",").append((int) ev.getY(i));
		}

		str.append(" ]");

		trace(str.toString());
	}

	private static float getSpacing(MotionEvent ev) {
		float dx = ev.getX(0) - ev.getX(1);
		float dy = ev.getY(0) - ev.getY(1);

		return FloatMath.sqrt(dx * dx + dy * dy);
	}

	private static int getActionCode(MotionEvent ev) {
		return ev.getAction() & MotionEvent.ACTION_MASK;
	}

	private static boolean isPointerDown(MotionEvent ev) {
		return getActionCode(ev) == MotionEvent.ACTION_POINTER_DOWN;
	}

	private static boolean isPinching(MotionEvent ev) {
		return ev.getPointerCount() > 1;
	}

	private static float mathBounds(float val, int min, int max) {
		return Math.max(Math.min(val, max), min);
	}
}
