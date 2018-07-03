package de.tobi.slideshowwallpaper;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import de.tobi.slideshowwallpaper.utilities.ImageInfo;
import de.tobi.slideshowwallpaper.utilities.ImageLoader;


public class SlideshowWallpaperService extends WallpaperService {
    private static final String PREFERENCE_KEY_LAST_UPDATE = "last_update";
    private static final String PREFERENCE_KEY_LAST_INDEX = "last_index";

    @Override
    public Engine onCreateEngine() {
        return new SlideshowWallpaperEngine();
    }


    public class SlideshowWallpaperEngine extends Engine {

        private Handler handler;

        private int width;
        private  int height;

        private Runnable drawRunner;
        private Paint paint;
        private Paint clearPaint;
        private boolean visible;
        private ArrayList<String> uris;

        public SlideshowWallpaperEngine() {
            handler = new Handler(Looper.getMainLooper());
            drawRunner = new DrawRunner();
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(10f);

            clearPaint = new Paint();
            clearPaint.setAntiAlias(true);
            clearPaint.setColor(Color.WHITE);
            clearPaint.setStyle(Paint.Style.FILL);
            handler.post(drawRunner);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            handler.removeCallbacks(drawRunner);
            visible = false;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            this.visible = visible;
            if (visible) {
                handler.post(drawRunner);
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        private SharedPreferences getSharedPreferences() {
            return SlideshowWallpaperService.this.getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        }

        private class DrawRunner implements Runnable {
            @Override
            public void run() {
                //Debug.waitForDebugger();
                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = null;
                SharedPreferences preferences = getSharedPreferences();
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        canvas.drawRect(0, 0, width, height, clearPaint);

                        uris = new ArrayList<>(preferences.getStringSet(getResources().getString(R.string.preference_pick_folder_key), new HashSet<String>()));
                        Bitmap bitmap = getNextImage();
                        if (bitmap != null) {
                            canvas.drawBitmap(bitmap, ImageLoader.calculateMatrixScaleToFit(bitmap, width, height), null);
                        }
                    }
                } catch (IOException e) {
                    Log.e(SlideshowWallpaperService.class.getSimpleName(), getResources().getString(R.string.error_reading_file), e);
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas);
                        } catch (IllegalArgumentException e) {
                            Log.e(SlideshowWallpaperService.class.getSimpleName(), "Error unlocking canvas", e);
                        }
                    }
                }
                handler.removeCallbacks(drawRunner);
                if (visible) {

                    handler.postDelayed(drawRunner, calculateNextUpdateInSeconds() * 1000);
                }
            }

            private Bitmap getNextImage() throws IOException {
                String uri = getNextUri();
                if (uri != null) {
                    ImageInfo info = ImageLoader.loadImage(uri, SlideshowWallpaperService.this, width, height);
                    return info.getImage();
                } else {
                    return null;
                }
            }

            private String getNextUri() {
                String result = null;
                if (!uris.isEmpty()) {
                    int currentImageIndex = getSharedPreferences().getInt(PREFERENCE_KEY_LAST_INDEX, 0);
                    int nextUpdate = calculateNextUpdateInSeconds();
                    if (nextUpdate <= 0) {
                        int delay = getDelaySeconds();
                        while (nextUpdate <= 0) {
                            String ordering = getSharedPreferences().getString(getResources().getString(R.string.preference_ordering_key), getResources().getStringArray(R.array.ordering_values)[0]);
                            if (ordering.equals("random")) {
                                currentImageIndex = new Random().nextInt(uris.size());
                            } else {
                                currentImageIndex++;

                                if (currentImageIndex >= uris.size()) {
                                    currentImageIndex = 0;
                                }
                            }
                            nextUpdate += delay;
                        }
                        SharedPreferences.Editor editor = getSharedPreferences().edit();
                        editor.putLong(PREFERENCE_KEY_LAST_UPDATE, System.currentTimeMillis());
                        editor.putInt(PREFERENCE_KEY_LAST_INDEX, currentImageIndex);
                        editor.apply();
                    }
                    result = uris.get(currentImageIndex);
                }

                return result;
            }

            private int getDelaySeconds() {
                int seconds = 5;
                try {
                    String secondsString = getSharedPreferences().getString(getResources().getString(R.string.preference_seconds_key), "5");
                    seconds = Integer.parseInt(secondsString);
                } catch (NumberFormatException e) {
                    Log.e(SlideshowWallpaperEngine.class.getSimpleName(), "Invalid number", e);
                    Toast toast = Toast.makeText(getApplicationContext(), e.getClass().getSimpleName() + " " + e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
                }
                return seconds;
            }

            private int calculateNextUpdateInSeconds() {
                long lastUpdate = getSharedPreferences().getLong(PREFERENCE_KEY_LAST_UPDATE, 0);
                int result = 0;
                if (lastUpdate > 0) {
                    int delaySeconds = getDelaySeconds();
                    long current = System.currentTimeMillis();
                    result = delaySeconds - (int)((current - lastUpdate) / 1000);
                }
                return result;
            }

        }
    }
}
