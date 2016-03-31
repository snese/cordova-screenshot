/**
 * Copyright (C) 2012 30ideas (http://30ide.as)
 * MIT licensed
 *
 * @author Josemando Sobral
 * @created Jul 2nd, 2012.
 * improved by Hongbo LU
 */
package com.darktalker.cordova.screenshot;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class Screenshot extends CordovaPlugin {
    
    private TextureView findXWalkTextureView(ViewGroup group) {

        int childCount = group.getChildCount();
        for(int i=0;i<childCount;i++) {
            View child = group.getChildAt(i);
            if(child instanceof TextureView) {
                String parentClassName = child.getParent().getClass().toString();
                boolean isRightKindOfParent = (parentClassName.contains("XWalk"));
                if(isRightKindOfParent) {
                    return (TextureView) child;
                }
            } else if(child instanceof ViewGroup) {
                TextureView textureView = findXWalkTextureView((ViewGroup) child);
                if(textureView != null) {
                    return textureView;
                }
            }
        }
        
        return null;
    }
    
    private Bitmap getBitmap() {
        Bitmap bitmap = null;
        
        boolean isCrosswalk = false;
        try {
            Class.forName("org.crosswalk.engine.XWalkWebViewEngine");
            isCrosswalk = true;
        } catch (Exception e) {
        }
        
        if(isCrosswalk) {
            try {
                
                TextureView textureView = findXWalkTextureView((ViewGroup)webView.getView());
                                if (textureView != null) {
                    bitmap = textureView.getBitmap();
                                    return bitmap;
                                }
            } catch(Exception e) {
            }
        } 

            View view = webView.getView().getRootView();
        view.setDrawingCacheEnabled(true);
        bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);

        
        return bitmap;
    }

    private void scanPhoto(String imageFileName)
    {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imageFileName);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    private Bitmap videoFrame(String uri, long msec) {       
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {                       
            if(uri.indexOf("http") == 0){
                retriever.setDataSource( uri, new HashMap<String,String>());
            }else{
                retriever.setDataSource( uri );
            }                       
            return retriever.getFrameAtTime( msec, MediaMetadataRetriever.OPTION_CLOSEST );
        } catch (IllegalArgumentException ex) {
        } catch (RuntimeException ex) {
        } catch( Exception ex ) {
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
            }
        }
        return null;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.e("====","onActivityResult");
        if (resultCode ==  super.cordova.getActivity().RESULT_OK) {
            if (requestCode == PERMISSION_CODE_MEDIA_PROJECTION) {
                try {
                    screenShot(resultCode, intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                    cCallbackContext.error("args error");
                }
            }

        }
        super.onActivityResult(requestCode, resultCode, intent);

    }
    CallbackContext cCallbackContext;

    String cAction;
    JSONArray cArgs;
    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // starting on ICS, some WebView methods
        // can only be called on UI threads

        if (Build.VERSION.SDK_INT >= 21) {
            this.cordova.setActivityResultCallback(this);
            cCallbackContext = callbackContext;
            cAction = action;
            cArgs = args;
            startScreenShot();
        }
            else {
            if (action.equals("saveScreenshot")) {
                final String format = (String) args.get(0);
                final Integer quality = (Integer) args.get(1);
                final String fileName = (String) args.get(2);
                final String uri = (String) args.get(3);
                final Integer msec = (Integer) args.get(4);

                super.cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            if (format.equals("png") || format.equals("jpg")) {
                                // callbackContext.error( videoFrame( uri, (long)msec ) );
                                // Bitmap bitmap = getBitmap();

                                Bitmap bitmap = videoFrame(uri, (long) msec);
                                if (bitmap == null) {
                                    callbackContext.error("bitmap create error");
                                    return;
                                }
                                File folder = new File(Environment.getExternalStorageDirectory(), "Pictures");
                                if (!folder.exists()) {
                                    folder.mkdirs();
                                }

                                File f = new File(folder, fileName + "." + format);

                                FileOutputStream fos = new FileOutputStream(f);
                                if (format.equals("png")) {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                }
                                if (format.equals("jpg")) {
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality == null ? 100 : quality, fos);
                                }
                                JSONObject jsonRes = new JSONObject();
                                jsonRes.put("filePath", f.getAbsolutePath());
                                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                                callbackContext.sendPluginResult(result);

                                scanPhoto(f.getAbsolutePath());
                            } else {
                                callbackContext.error("format " + format + " not found");

                            }

                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());

                        } catch (IOException e) {
                            callbackContext.error(e.getMessage());

                        }
                    }
                });
                return true;
            } else if (action.equals("getScreenshotAsURI")) {
                final Integer quality = (Integer) args.get(0);

                super.cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Bitmap bitmap = getBitmap();

                            ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();

                            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpeg_data)) {
                                byte[] code = jpeg_data.toByteArray();
                                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                                String js_out = new String(output);
                                js_out = "data:image/jpeg;base64," + js_out;
                                JSONObject jsonRes = new JSONObject();
                                jsonRes.put("URI", js_out);
                                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                                callbackContext.sendPluginResult(result);

                                js_out = null;
                                output = null;
                                code = null;
                            }

                            jpeg_data = null;

                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());

                        } catch (Exception e) {
                            callbackContext.error(e.getMessage());

                        }
                    }
                });

                return true;
            }
            callbackContext.error("action not found");
        }
        return true;
    }



    final int PERMISSION_CODE_MEDIA_PROJECTION = 1009;

    private void startScreenShot () {
        MediaProjectionManager projectionManager = (MediaProjectionManager)  super.cordova.getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = projectionManager.createScreenCaptureIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        super.cordova.getActivity().startActivityForResult(intent, PERMISSION_CODE_MEDIA_PROJECTION);
    }


    private void screenShot (int resultCode, Intent data) throws  JSONException {










        final MediaProjectionManager projectionManager = (MediaProjectionManager)  super.cordova.getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        final MediaProjection mProjection = projectionManager.getMediaProjection(resultCode, data);
        Point size = getScreenSize(super.cordova.getActivity());
        final int mWidth = size.x;
        final int mHeight = size.y;

        final ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        final VirtualDisplay display = mProjection.createVirtualDisplay("screen-mirror", mWidth, mHeight, DisplayMetrics.DENSITY_MEDIUM,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, mImageReader.getSurface(), null, null);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader mImageReader) {

                Image image = null;
                try {
                    image = mImageReader.acquireLatestImage();
                    if (image != null) {
                        final Image.Plane[] planes = image.getPlanes();
                        if (planes.length > 0) {
                            final ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * mWidth;

                            Bitmap bmp = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                            bmp.copyPixelsFromBuffer(buffer);

                            Bitmap croppedBitmap = Bitmap.createBitmap(bmp, 0, 0, mWidth, mHeight);



                            if (cAction.equals("saveScreenshot")) {
                                final String format = (String) cArgs.get(0);
                                final Integer quality = (Integer) cArgs.get(1);
                                final String fileName = (String) cArgs.get(2);
                                final String uri = (String) cArgs.get(3);
                                final Integer msec = (Integer) cArgs.get(4);

                                try {
                                    if (format.equals("png") || format.equals("jpg")) {
                                        // callbackContext.error( videoFrame( uri, (long)msec ) );
                                        // Bitmap bitmap = getBitmap();

                                        Bitmap bitmap = croppedBitmap;
                                        if (bitmap == null) {
                                            cCallbackContext.error("bitmap create error");
                                            return;
                                        }
                                        File folder = new File(Environment.getExternalStorageDirectory(), "Pictures");
                                        if (!folder.exists()) {
                                            folder.mkdirs();
                                        }

                                        File f = new File(folder, fileName + "." + format);

                                        FileOutputStream fos = new FileOutputStream(f);
                                        if (format.equals("png")) {
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                        }
                                        if (format.equals("jpg")) {
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality == null ? 100 : quality, fos);
                                        }
                                        JSONObject jsonRes = new JSONObject();
                                        jsonRes.put("filePath", f.getAbsolutePath());
                                        PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                                        cCallbackContext.sendPluginResult(result);

                                        scanPhoto(f.getAbsolutePath());
                                    } else {
                                        cCallbackContext.error("format " + format + " not found");

                                    }

                                } catch (JSONException e) {
                                    cCallbackContext.error(e.getMessage());

                                } catch (IOException e) {
                                    cCallbackContext.error(e.getMessage());

                                }
                            }else if(cAction.equals("getScreenshotAsURI")){
                                final Integer quality = (Integer) cArgs.get(0);


                                try {
                                    Bitmap bitmap = croppedBitmap;

                                    ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();

                                    if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpeg_data)) {
                                        byte[] code = jpeg_data.toByteArray();
                                        byte[] output = Base64.encode(code, Base64.NO_WRAP);
                                        String js_out = new String(output);
                                        js_out = "data:image/jpeg;base64," + js_out;
                                        JSONObject jsonRes = new JSONObject();
                                        jsonRes.put("URI", js_out);
                                        PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                                        cCallbackContext.sendPluginResult(result);

                                        js_out = null;
                                        output = null;
                                        code = null;
                                    }

                                    jpeg_data = null;

                                } catch (JSONException e) {
                                    cCallbackContext.error(e.getMessage());

                                } catch (Exception e) {
                                    cCallbackContext.error(e.getMessage());

                                }
                            } else {


                                cCallbackContext.error("action not found");
                            }

//                            int i = 0;
//
//                            File myDrawFile = new File("/sdcard/test"+i+".jpg");
//                            while (myDrawFile.exists()) {
//                                i++;
//                                myDrawFile = new File("/sdcard/test"+i+".jpg");
//                            }
//                            try {
//                                BufferedOutputStream bos = new BufferedOutputStream
//                                        (new FileOutputStream(myDrawFile));
//
//                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
//                                bos.flush();
//                                bos.close();
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                                Log.d(null, "Save file error!");
//                            }
                            if (croppedBitmap != null) {
                                croppedBitmap.recycle();
                            }
                            if (bmp != null) {
                                bmp.recycle();
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (mImageReader != null) {
                        mImageReader.close();
                    }
                    if (display != null) {
                        display.release();
                    }

                    mImageReader.setOnImageAvailableListener(null, null);
                    mProjection.stop();
                }

            }
        }, getBackgroundHandler());
    }

    private Handler getBackgroundHandler() {
        if (backgroundHandler == null) {
            HandlerThread backgroundThread =
                    new HandlerThread("easyscreenshot", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }

        return backgroundHandler;
    }
    Handler backgroundHandler;

    public Point getScreenSize(Context context) {

        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        final int width = displayMetrics.widthPixels;
        final int height = displayMetrics.heightPixels;
        return new Point(width, height);
    }

}
