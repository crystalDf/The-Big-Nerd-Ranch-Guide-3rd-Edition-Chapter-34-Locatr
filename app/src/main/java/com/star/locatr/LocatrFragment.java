package com.star.locatr;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class LocatrFragment extends SupportMapFragment {

    private static final String TAG = "LocatrFragment";

    private static final int REQUEST_LOCATION_PERMISSIONS = 0;

    private static final String[] LOCATION_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private GoogleApiClient mGoogleApiClient;

    private GoogleMap mGoogleMap;

    private Bitmap mMapBitmap;
    private GalleryItem mMapGalleryItem;

    private Location mCurrentLocation;

    public static LocatrFragment newInstance() {
        return new LocatrFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        if (getActivity() == null) {
            return;
        }

        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        getActivity().invalidateOptionsMenu();
                        Log.i(TAG, "Connected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(connectionResult -> Log.i(TAG, "Connection Failed"))
                .build();

        getMapAsync(googleMap -> {
            mGoogleMap = googleMap;

            updateUI();
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getActivity() == null) {
            return;
        }

        getActivity().invalidateOptionsMenu();

        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_locatr, menu);

        MenuItem menuItem = menu.findItem(R.id.action_locate);
        menuItem.setEnabled(mGoogleApiClient.isConnected());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_locate:
                findImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void findImage() {

        if (getActivity() == null) {
            return;
        }

        List<String> permissionList = new ArrayList<>();

        for (String permission : LOCATION_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }

        if (!permissionList.isEmpty()) {
            requestPermissions(
                    permissionList.toArray(new String[permissionList.size()]),
                    REQUEST_LOCATION_PERMISSIONS);
        } else {
            doFindImage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSIONS:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(getActivity(), permissions[i] + " denied. ",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    doFindImage();
                }
                break;
            default:
                break;
        }
    }

    private void doFindImage() {

        try {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setNumUpdates(1);
            locationRequest.setInterval(0);

            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, locationRequest, location -> {
                        Log.i(TAG, "Got a fix: " + location);
                        new SearchTask().execute(location);
                    });
        } catch (SecurityException se) {
            se.printStackTrace();
        }
    }

    private void updateUI() {
        if (mGoogleMap == null || mMapBitmap == null) {
            return;
        }

        LatLng itemPoint = new LatLng(mMapGalleryItem.getLat(), mMapGalleryItem.getLon());
        LatLng myPoint = new LatLng(
                mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());

        BitmapDescriptor itemBitmap = BitmapDescriptorFactory.fromBitmap(mMapBitmap);
        MarkerOptions itemMarker = new MarkerOptions()
                .position(itemPoint)
                .icon(itemBitmap);
        MarkerOptions myMarker = new MarkerOptions()
                .position(myPoint);

        mGoogleMap.clear();
        mGoogleMap.addMarker(itemMarker);
        mGoogleMap.addMarker(myMarker);

        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(itemPoint)
                .include(myPoint)
                .build();

        int margin = getResources().getDimensionPixelSize(R.dimen.map_inset_margin);
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, margin);
        mGoogleMap.animateCamera(update);
    }

    private class SearchTask extends AsyncTask<Location, Void, List<GalleryItem>> {

        @Override
        protected List<GalleryItem> doInBackground(Location... params) {

            mCurrentLocation = params[0];

            return new FlickrFetchr().searchPhotos(params[0]);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {

            if (!items.isEmpty()) {
                mMapGalleryItem = items.get(0);

                bindGalleryItem(items.get(0));

                updateUI();
            }
        }
    }

    private void bindGalleryItem(GalleryItem galleryItem) {

        if (getActivity() == null) {
            return;
        }

        Glide.with(getActivity())
                .load(galleryItem.getUrl())
                .apply(new RequestOptions().placeholder(R.drawable.emma))
                .into(new SimpleTarget<Drawable>() {
                    @Override
                    public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                        mMapBitmap = ((BitmapDrawable)resource).getBitmap();
                    }
                });
    }
}
