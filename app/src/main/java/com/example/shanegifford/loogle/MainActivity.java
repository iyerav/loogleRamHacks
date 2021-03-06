package com.example.shanegifford.loogle;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

class Toilet {

    private double latitude;
    private double longitude;
    public float cleanliness;
    public boolean isAccessible;

    public Toilet() {       //Class for storing coordinates and/or toilet info
        latitude = 0;
        longitude = 0;
        cleanliness = 1;
        isAccessible = false;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLatitude(double d) {
        latitude = d;
    }

    public void setLongitude(double d) {
        longitude = d;
    }

    public double CalculationByDistance(Toilet StartP, Toilet EndP) {       //Calculates distance between two GPScoords, takes curve of eath into account
        int Radius = 6371;// radius of earth in Km
        double lat1 = StartP.getLatitude();
        double lat2 = EndP.getLatitude();
        double lon1 = StartP.getLongitude();
        double lon2 = EndP.getLongitude();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double valueResult = Radius * c;
        double km = valueResult / 1;
        DecimalFormat newFormat = new DecimalFormat("####");
        int kmInDec = Integer.valueOf(newFormat.format(km));
        double meter = valueResult % 1000;
        int meterInDec = Integer.valueOf(newFormat.format(meter));
        Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
                + " Meter   " + meterInDec);

        return Radius * c;
    }

}

public class MainActivity extends AppCompatActivity {

    private LocationManager locationManager;
    private String provider;
    private Location location;
    private FirebaseDatabase database;
    private DatabaseReference ref;
    private ValueEventListener toiletFinder;
    private Toilet locationC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 200);   //Permission needed for location services & maps interfacing

        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!service.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable GPS", Toast.LENGTH_LONG).show();
        }
        // Get the location manager
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);         //Initialize location manager
        // Define the criteria how to select the location provider -> use
        // default
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        location = new Location(provider);

        database = FirebaseDatabase.getInstance();          //Instantiate firebase


        toiletFinder = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {       //Only called through addListenerForSingleValueEvent, below
                Toilet closest = null;                                  //Finds closest toilet and sends to google maps as navigation destination
                double dist = 0;
                if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED) {
                    location = locationManager.getLastKnownLocation(provider);      //Grabs GPS location of phone
                    locationC = new Toilet();
                    locationC.setLongitude(location.getLongitude());        //Stores coords in Toilet class, for use later
                    locationC.setLatitude(location.getLatitude());
                }
                if (location != null) {
                    for (DataSnapshot child : dataSnapshot.getChildren()) {     //If phone location found, compare against each toilet in firebase
                        Toilet coord = child.getValue(Toilet.class);            //Closest toilet to phone location is stored in 'closest'
                        double currentDist = coord.CalculationByDistance(locationC, coord);
                        if (closest != null) {
                            if (dist > currentDist) {
                                closest = coord;
                                dist = currentDist;
                            }
                        }
                        else {
                            closest = coord;            //Initialize first element in database to be closest
                            dist = currentDist;
                        }
                    }
                    if (closest != null) {
//                        Toast.makeText(MainActivity.this, closest.getLatitude() + " " + closest.getLongitude(), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + closest.getLatitude() + "," + closest.getLongitude()));
                        intent.setPackage("com.google.android.apps.maps");          //Convert coordinates into URI, and send to Maps as navigation point
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);          //Starts Google Maps app
                        }

                    }
                    else {
                        Toast.makeText(MainActivity.this, "Can't find Toilet from server", Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    Toast.makeText(MainActivity.this, "Can't find location", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Database Error", Toast.LENGTH_LONG).show();
            }
        };


        Button btn_filters = findViewById(R.id.angry_btn);
        btn_filters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, FilterActivity.class);     //Click filter button, go to filter screen
                startActivity(i);
            }
        });

        Button btn_emergency = findViewById(R.id.angry_btn2);
        btn_emergency.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View view) {
                 ref = database.getReference();                 //Click emergency button, run toiletFinder above
                 ref.addListenerForSingleValueEvent(toiletFinder);
             }
        });

        Button btn_getCoords = findViewById(R.id.angry_btn3);
        btn_getCoords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED) {      //Click New toilet button, get current coords
                    location = locationManager.getLastKnownLocation(provider);                                                  //and go to new toilet screen
                }
                if (location != null) {
                    Toilet coords = new Toilet();
                    coords.setLongitude(location.getLongitude());       //converting coordinates to Toilet class
                    coords.setLatitude(location.getLatitude());

                    Intent i = new Intent(MainActivity.this, EntryActivity.class);
                    i.putExtra("lat", coords.getLatitude());             //store latitude and longitude as extras in intent then send to new toilet screen
                    i.putExtra("lon", coords.getLongitude());
                    startActivity(i);
//                    Toast.makeText(MainActivity.this, "Data sent to FB", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
