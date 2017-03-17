package com.esri.arcgisruntime.basicandroidproject;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.symbology.TextSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.util.ListenableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private MapView mMapView;
    private SearchView mSearchView = null;
    private GraphicsOverlay mGraphicsOverlay;
    private LocatorTask mLocatorTask = null;
    private GeocodeParameters mGeocodeParameters = null;

    private LocationDisplay mLocationDisplay;
    Button buttonNavigate;
    ListView suggestResult;
    ArrayAdapter adapter;

    private void setupMap() {
        if (mMapView != null) {
            /*
            Basemap.Type basemapType = Basemap.Type.STREETS_VECTOR;
            double latitude = 34.05293;
            double longitude = -118.24368;
            int levelOfDetail = 11;
            ArcGISMap map = new ArcGISMap(basemapType, latitude, longitude, levelOfDetail);
            */

            ArcGISMap map = new ArcGISMap(Basemap.createTopographic());
            mMapView.setViewpointScaleAsync(10);
            mMapView.setMap(map);

            //requires permission from manifest coarse location and fine location
            mLocationDisplay = mMapView.getLocationDisplay();
            mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
            if (!mLocationDisplay.isStarted())
                mLocationDisplay.startAsync();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = (MapView) findViewById(R.id.mapView);
        buttonNavigate = (Button) findViewById(R.id.btnStartNavigation);
        suggestResult = (ListView) findViewById(R.id.lstSuggestResults);
        setupMap();
        setupLocator();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        MenuItem searchMenuItem = menu.findItem(R.id.search);
        if (searchMenuItem != null) {
            mSearchView = (SearchView) searchMenuItem.getActionView();
            if (mSearchView != null) {
                SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
                mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                mSearchView.setIconifiedByDefault(false);
            }
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            queryLocator(intent.getStringExtra(SearchManager.QUERY));
        }
    }

    private void queryLocator(final String query) {
        if (query != null && query.length() > 0) {
            mLocatorTask.cancelLoad();
            final ListenableFuture<List<GeocodeResult>> geocodeFuture = mLocatorTask.geocodeAsync(query, mGeocodeParameters);
            geocodeFuture.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                         List<GeocodeResult> geocodeResults = geocodeFuture.get();
                        if (geocodeResults.size() > 0) {
                            displaySearchResult(geocodeResults.get(0));

                            //retrieve raw geocoded data and populate to useful data
                            int i = 0;
                            ArrayList translatedResultArray;
                            translatedResultArray = new ArrayList<>();
                            while(geocodeResults.size() > i){
                                String translatedResult = translateRawResult(geocodeResults.get(i));
                                translatedResultArray.add(translatedResult);
                                i++;
                            }

                            adapter = new ArrayAdapter(MainActivity.this, android.R.layout.select_dialog_item, translatedResultArray);
                            suggestResult.setAdapter(adapter);
                            buttonNavigate.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(getApplicationContext(), getString(R.string.nothing_found) + " " + query, Toast.LENGTH_LONG).show();
                            buttonNavigate.setVisibility(View.INVISIBLE);
                            //reset to current position

                            mLocationDisplay = mMapView.getLocationDisplay();
                            mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                            if (!mLocationDisplay.isStarted())
                                mLocationDisplay.startAsync();

                            ListenableList allGraphics = mGraphicsOverlay.getGraphics();
                            allGraphics.clear();

                        }
                    } catch (InterruptedException | ExecutionException e) {
                        // ... determine how you want to handle an error
                        mLocationDisplay = mMapView.getLocationDisplay();
                        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                        if (!mLocationDisplay.isStarted())
                            mLocationDisplay.startAsync();
                    }
                    geocodeFuture.removeDoneListener(this); // Done searching, remove the listener.
                }
            });
        }

        suggestResult.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }
    public String translateRawResult(GeocodeResult geocodedLocation) {
        String displayLabel = geocodedLocation.getLabel();

        return displayLabel;
    }

    private void displaySearchResult(GeocodeResult geocodedLocation) {
        String displayLabel = geocodedLocation.getLabel();
        TextSymbol textLabel = new TextSymbol(18, displayLabel, Color.rgb(192, 32, 32), TextSymbol.HorizontalAlignment.CENTER, TextSymbol.VerticalAlignment.BOTTOM);
        Graphic textGraphic = new Graphic(geocodedLocation.getDisplayLocation(), textLabel);
        Graphic mapMarker = new Graphic(geocodedLocation.getDisplayLocation(), geocodedLocation.getAttributes(),
                new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.SQUARE, Color.rgb(255, 0, 0), 12.0f));
        ListenableList allGraphics = mGraphicsOverlay.getGraphics();
        allGraphics.clear();
        allGraphics.add(mapMarker);
        allGraphics.add(textGraphic);
        mMapView.setViewpointCenterAsync(geocodedLocation.getDisplayLocation());
    }

    private void setupLocator() {
        String locatorLocation = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer";
        mLocatorTask = new LocatorTask(locatorLocation);
        mLocatorTask.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                    mGeocodeParameters = new GeocodeParameters();
                    mGeocodeParameters.getResultAttributeNames().add("*");
                    mGeocodeParameters.setMaxResults(5);
                    //set country limit uses 3 digit code
                    mGeocodeParameters.setCountryCode("PHL");
                    mGraphicsOverlay = new GraphicsOverlay();
                    mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
                } else if (mSearchView != null) {
                    mSearchView.setEnabled(false);
                }
            }
        });
        mLocatorTask.loadAsync();
    }

    @Override
    protected void onPause() {
        mMapView.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }
}

