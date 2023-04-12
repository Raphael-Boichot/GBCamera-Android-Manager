package com.mraulio.gbcameramanager.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.mraulio.gbcameramanager.MainActivity;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.ui.gallery.GalleryFragment;

import java.util.ArrayList;
import java.util.List;


public class SettingsFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        Spinner spinnerExport = view.findViewById(R.id.spExportSize);
        Spinner spinnerImages = view.findViewById(R.id.spImagesPage);
//        MainActivity.pressBack=false;
        List<Integer> sizesInteger = new ArrayList<>();
        sizesInteger.add(1);
        sizesInteger.add(2);
        sizesInteger.add(4);
        sizesInteger.add(5);
        sizesInteger.add(6);
        sizesInteger.add(8);
        sizesInteger.add(10);

        List<String> sizes = new ArrayList<>();
        sizes.add("1");
        sizes.add("2");
        sizes.add("4");
        sizes.add("5");
        sizes.add("6");
        sizes.add("8");
        sizes.add("10");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, sizes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerExport.setAdapter(adapter);
        spinnerExport.setSelection(sizesInteger.indexOf(MainActivity.exportSize));

        spinnerExport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // I set the export size on the Main activity int as the selected one
                MainActivity.exportSize = sizesInteger.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Acción que quieres hacer cuando no se selecciona ningún elemento en el Spinner
            }
        });


        List<Integer> sizesIntegerImages = new ArrayList<>();
        sizesIntegerImages.add(6);
        sizesIntegerImages.add(9);
        sizesIntegerImages.add(12);
        sizesIntegerImages.add(15);
        sizesIntegerImages.add(18);
        sizesIntegerImages.add(30);

        List<String> sizesImages = new ArrayList<>();
        sizesImages.add("6");
        sizesImages.add("9");
        sizesImages.add("12");
        sizesImages.add("15");
        sizesImages.add("18");
        sizesImages.add("30");
        ArrayAdapter<String> adapterImages = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, sizesImages);
        adapterImages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerImages.setAdapter(adapterImages);
        spinnerImages.setSelection(sizesIntegerImages.indexOf(MainActivity.imagesPage));
        spinnerImages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // I set the export size on the Main activity int as the selected one
                MainActivity.imagesPage = sizesIntegerImages.get(position);
                GalleryFragment.currentPage = 0;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Acción que quieres hacer cuando no se selecciona ningún elemento en el Spinner
            }
        });

        return view;
    }


}