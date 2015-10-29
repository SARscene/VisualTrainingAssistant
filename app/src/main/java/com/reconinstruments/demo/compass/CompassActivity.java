/*
* Adapted from the DemoCompass sample app for the Recon Jet HUD by Maj W David Oldford
* as SARScene 2015 released into the Public Domain 24 Oct 2015
*
*This app while not very clean displays a compass display in the HUD of the Jet glasses and
* beeps to indicate when a user turns their head (whether part of turning their entire body
* or not,) faster than a specified rate. The rate is hard coded to 30 degrees/second after this
* speed is exceeded the glassess will start to beep. The volume of the beeping will be based
* on how far the specified rate is exceeded.
*
* The intention of this app is to train people to sweep their heads slowly when scanning an
* environment searching for things, for example someone on a ground SAR who naturally wants to
* look quickly and needs to learn to take their time. Due to its annoying nature it is expected
* to be more useful in training people than it is for use during an actual search. It could
* also be used for anyone who needs to be trained to scan slowly such as a UN Military Observer.
*
* */

package com.reconinstruments.demo.compass;

import android.app.Activity;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;




import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

public class CompassActivity extends Activity implements HeadLocationListener {
	private final String TAG = this.getClass().getSimpleName();
	private SoundPool soundPool;
	private int soundID;
	private float prevHeading = 0.0f;
	private long prevTimeMillis = 0;
	boolean plays=false, loaded=false;
	private int playID = 0;
	float actVolume, maxVolume, volume;
	AudioManager audioManager;
	private final float lowVol = 0.1f;
	private final float highVol = 1.0f;
	private final float desiredScanSpeed = 30.0f;



	private HUDHeadingManager mHUDHeadingManager = null;
	
	private static double PIXELS_PER_45_DEGREES = 190.0;

	float mUserHeading = 0.0f;
	ImageView mCompassBar = null;
	ImageView mCompassUnderline = null;

	boolean mIsResumed = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		mHUDHeadingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_compass);

		mCompassBar = (ImageView) findViewById(R.id.compass_bar);
		mCompassUnderline = (ImageView) findViewById(R.id.compass_underline);

		Matrix matrix = new Matrix();
		matrix.reset();
		matrix.postTranslate(-428, 0);

		mCompassBar.setScaleType(ScaleType.MATRIX);
		mCompassBar.setImageMatrix(matrix);

		audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		actVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		volume = actVolume / maxVolume;
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		soundPool = new SoundPool(1,AudioManager.STREAM_MUSIC,0);
		soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				loaded = true;
			}

		});
		soundID = soundPool.load(this, R.raw.beep, 1);


	}

	public void playLoop() {
		// Is the sound loaded does it already play?
		Log.i("playLoop","playing Loop");
		if (loaded && !plays) {
			playID = soundPool.play(soundID, volume, volume, 1, -1, 1f);
			plays = true;
		}
	}

	public void adjustVolume(float newVolume){
		if(newVolume == volume) return;
		volume = newVolume;
		soundPool.setVolume(playID,volume,volume);
	}


	private float calcScanSpeed(float heading,float oldHeading, float deltaSeconds){


		float headingDelta = heading-oldHeading;
		if(deltaSeconds == 0) deltaSeconds = 0.001f;

		if(headingDelta+360 < Math.abs(headingDelta)) headingDelta = headingDelta+360;
		if(Math.abs(headingDelta-360) < Math.abs(headingDelta)) headingDelta = headingDelta-360;
		else
		Log.i("calcScanSpeed","headingDelta:"+Float.toString(headingDelta)+" deltaSeconds:"+Float.toString(deltaSeconds));
		Log.i("calcScanSpeed","scanRate:"+Float.toString(headingDelta/deltaSeconds)+"oldHeading,heading:"+Float.toString(oldHeading)+","+Float.toString(heading));
		return headingDelta/deltaSeconds;
	}

	public void stopSound(){
		Log.i("stopSound","stopping");
		if(plays)
		{
			soundPool.stop(playID);
			//soundID = soundPool.load(this, R.raw.beep, 1);
			plays = false;
		}
	}

	@Override
	public void onResume() {
		Log.i(TAG,"onResume");

		super.onResume();

		mHUDHeadingManager.register(this);

		mIsResumed = true;
	}

	@Override
	public void onPause()  {
		Log.d(TAG,"onPause");
		
		mHUDHeadingManager.unregister(this);

		mIsResumed = false;

		super.onPause();
	}

	@Override
	public void onDestroy(){
		Log.d(TAG,"onDestroy");

		super.onDestroy();
	}


	private void adjustBeep(float adjustValue){

		volume = adjustValue*0.05f;
		if( volume > 1.0f)volume = 1.0f;
		if(plays) adjustVolume(volume);
		else
		{
			if(!plays)playLoop();
		}
	}

	@Override
	public void onHeadLocation(float yaw, float pitch, float roll) {
		if(!this.mIsResumed || (Float.isNaN(yaw))) {
			return;
		}

		float newHeading = yaw;

		if(mUserHeading > 270.0f && newHeading < 90.0f) {
			mUserHeading = mUserHeading - 360.0f;// avoid aliasing in average when crossing North (angle = 0.0)
		} else if (mUserHeading < 90.0f && newHeading > 270.0f) {
			newHeading = newHeading - 360.0f; // avoid aliasing in average when crossing North (angle = 0.0)
		}

		mUserHeading = (float) ((4.0*mUserHeading + newHeading)/5.0); // smooth heading

		if(mUserHeading < 000.0f) mUserHeading += 360.0f;
		if(mUserHeading > 360.0f) mUserHeading -= 360.0f;
		//if(mUserHeading <45.0f || mUserHeading > 325.0f) playLoop();
		//if(mUserHeading >45.0f && mUserHeading < 325.0f) stopSound();
		long currTimeMillis = System.currentTimeMillis();
		float deltaSeconds = (currTimeMillis - prevTimeMillis)/1000.0f;
		boolean update = false;
		if(prevTimeMillis == 0) update = true;
		if(prevTimeMillis != 0 && deltaSeconds > 0.01)//first run it will be 0 so skip beep calcs
		{
			float scanSpeed = calcScanSpeed(mUserHeading, prevHeading, deltaSeconds);

			if (scanSpeed > desiredScanSpeed) adjustBeep(scanSpeed - desiredScanSpeed);
			if (scanSpeed < 0.0f || scanSpeed <= desiredScanSpeed) stopSound();
			update = true;

		}
		int offset = (mUserHeading >= 315f && mUserHeading <= 360) ? -(int)PIXELS_PER_45_DEGREES*7 : (int)PIXELS_PER_45_DEGREES;
		int x = (int)(mUserHeading / 360.0 * (8.0*PIXELS_PER_45_DEGREES)) + offset; // TODO: What is this?

		mCompassBar.getImageMatrix().reset();
		mCompassBar.getImageMatrix().postTranslate(-x, 0);

		mCompassBar.invalidate(); // TODO: find out if we need this
		if(update) {
			prevHeading = mUserHeading;
			prevTimeMillis = currTimeMillis;
		}
	}
}
