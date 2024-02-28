package com.mraulio.gbcameramanager.ui.importFile;

import static com.mraulio.gbcameramanager.ui.importFile.ImageConversionUtils.checkPaletteColors;
import static com.mraulio.gbcameramanager.ui.importFile.ImageConversionUtils.convertToGrayScale;
import static com.mraulio.gbcameramanager.ui.importFile.ImageConversionUtils.ditherImage;
import static com.mraulio.gbcameramanager.ui.importFile.ImageConversionUtils.resizeImage;
import static com.mraulio.gbcameramanager.ui.usbserial.UsbSerialUtils.magicIsReal;
import static com.mraulio.gbcameramanager.utils.Utils.transparencyHashSet;
import static com.mraulio.gbcameramanager.utils.Utils.transparentBitmap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Adapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.mraulio.gbcameramanager.db.ImageDao;
import com.mraulio.gbcameramanager.db.ImageDataDao;
import com.mraulio.gbcameramanager.model.ImageData;
import com.mraulio.gbcameramanager.ui.gallery.CustomGridViewAdapterImage;
import com.mraulio.gbcameramanager.ui.palettes.CustomGridViewAdapterPalette;
import com.mraulio.gbcameramanager.db.FrameDao;
import com.mraulio.gbcameramanager.MainActivity;
import com.mraulio.gbcameramanager.utils.RomExtractor;
import com.mraulio.gbcameramanager.utils.Utils;
import com.mraulio.gbcameramanager.db.PaletteDao;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.gameboycameralib.constants.IndexedPalette;
import com.mraulio.gbcameramanager.gameboycameralib.saveExtractor.Extractor;
import com.mraulio.gbcameramanager.gameboycameralib.saveExtractor.SaveImageExtractor;
import com.mraulio.gbcameramanager.model.GbcFrame;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.GbcPalette;
import com.mraulio.gbcameramanager.ui.frames.FramesFragment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;


public class ImportFragment extends Fragment {

    static List<Bitmap> importedImagesBitmaps = new ArrayList<>();
    static List<GbcImage> importedImagesList = new ArrayList<>();
    int totalImages = 0;
    List<List<GbcImage>> listActiveImages = new ArrayList<>();
    List<List<GbcImage>> listDeletedImages = new ArrayList<>();
    List<List<Bitmap>> listDeletedBitmaps = new ArrayList<>();
    List<List<Bitmap>> listDeletedBitmapsRedStroke = new ArrayList<>();
    List<GbcImage> finalListImages = new ArrayList<>();
    List<List<Bitmap>> listActiveBitmaps = new ArrayList<>();
    List<Bitmap> finalListBitmaps = new ArrayList<>();
    List<GbcImage> lastSeenImage = new ArrayList<>();
    List<Bitmap> lastSeenBitmap = new ArrayList<>();

    public static List<ImageData> importedImageDatas = new ArrayList<>();
    public static List<byte[]> listImportedImageBytes = new ArrayList<>();
    byte[] fileBytes;
    private AlertDialog loadingDialog;
    private Adapter adapter;
    boolean isGoodSave = true;

    TextView tvFileName;
    static String fileName;

    String fileContent = "";
    List<?> receivedList;
    int numImagesAdded;
    Button btnExtractFile, btnAddImages;
    CheckBox cbLastSeen, cbDeleted, cbAddFrame;
    LinearLayout layoutCb;
    CustomGridViewAdapterPalette customAdapterPalette;
    GridView gridViewImport;

    public enum ADD_WHAT {
        PALETTES,
        FRAMES,
        IMAGES
    }

    public static ADD_WHAT addEnum;

    public enum FILE_TYPE {
        SAV,
        PHOTO_ROM,
        JSON,
        TXT,
        IMAGE
    }

    public static FILE_TYPE fileType;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_import, container, false);
        Button btnSelectFile = view.findViewById(R.id.btnSelectFile);
        btnExtractFile = view.findViewById(R.id.btnExtractFile);
        btnExtractFile.setVisibility(View.GONE);
        cbLastSeen = view.findViewById(R.id.cbLastSeen);
        cbDeleted = view.findViewById(R.id.cbDeletedImages);
        cbAddFrame = view.findViewById(R.id.cbAddFrame);

        layoutCb = view.findViewById(R.id.layout_cb);
        btnAddImages = view.findViewById(R.id.btnAddImages);
        btnAddImages.setVisibility(View.GONE);
        MainActivity.pressBack = false;
        loadingDialog = Utils.loadingDialog(getContext());
        MainActivity.current_fragment = MainActivity.CURRENT_FRAGMENT.IMPORT;

        tvFileName = view.findViewById(R.id.tvFileName);
        gridViewImport = view.findViewById(R.id.gridViewImport);
        btnSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileDialog(v);
            }

        });
        btnExtractFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadingDialog.show();
                new loadDataTask().execute();
            }
        });

        cbLastSeen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImages(cbLastSeen, cbDeleted);
                gridViewImport.setAdapter((ListAdapter) adapter);
            }
        });
        cbDeleted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImages(cbLastSeen, cbDeleted);
                gridViewImport.setAdapter((ListAdapter) adapter);
            }
        });


        btnAddImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (addEnum) {
                    case PALETTES:
                        btnAddImages.setEnabled(false);
                        List<GbcPalette> newPalettes = new ArrayList<>();
                        for (Object palette : receivedList) {
                            boolean alreadyAdded = false;
                            GbcPalette gbcp = (GbcPalette) palette;
                            //If the palette already exists (by the name) it doesn't add it. Same if it's already added
                            for (GbcPalette objeto : Utils.gbcPalettesList) {
                                if (objeto.getPaletteId().toLowerCase(Locale.ROOT).equals(gbcp.getPaletteId())) {
                                    alreadyAdded = true;
                                    break;
                                }
                            }
                            if (!alreadyAdded) {
                                newPalettes.add(gbcp);
                            }
                        }
                        if (newPalettes.size() > 0) {
                            Utils.gbcPalettesList.addAll(newPalettes);
                            for (GbcPalette gbcPalette : newPalettes) {
                                Utils.hashPalettes.put(gbcPalette.getPaletteId(), gbcPalette);
                            }
                            new SavePaletteAsyncTask(newPalettes).execute();
                        } else {
                            Utils.toast(getContext(), getString(R.string.no_new_palettes));
                            tvFileName.setText(getString(R.string.no_new_palettes));
                        }
                        customAdapterPalette.notifyDataSetChanged();
                        break;
                    case FRAMES:
                        btnAddImages.setEnabled(false);
                        List<GbcFrame> newFrames = new ArrayList<>();
                        for (Object frame : receivedList) {
                            boolean alreadyAdded = false;
                            GbcFrame gbcFrame = (GbcFrame) frame;
                            //If the frame already exists (by the name) it doesn't add it. Same if it's already added
                            for (GbcFrame objeto : Utils.framesList) {
                                if (objeto.getFrameName().toLowerCase(Locale.ROOT).equals(gbcFrame.getFrameName().toLowerCase(Locale.ROOT))) {
                                    alreadyAdded = true;
                                    break;
                                }
                            }
                            if (!alreadyAdded) {
                                newFrames.add(gbcFrame);
                            }
                        }
                        if (newFrames.size() > 0) {
                            Utils.framesList.addAll(newFrames);
                            for (GbcFrame gbcFrame : newFrames) {
                                Utils.hashFrames.put(gbcFrame.getFrameName(), gbcFrame);
                            }
                            new SaveFrameAsyncTask(newFrames).execute();//TEST THIS
                        } else {
                            Utils.toast(getContext(), getString(R.string.no_new_frames));
                            tvFileName.setText(getString(R.string.no_new_frames));
                        }
                        break;

                    case IMAGES:
                        btnAddImages.setEnabled(false);
                        numImagesAdded = 0;
                        List<GbcImage> newGbcImages = new ArrayList<>();
                        List<ImageData> newImageDatas = new ArrayList<>();
                        switch (fileType) {
                            case TXT:
                            case JSON: {
                                for (int i = 0; i < importedImagesList.size(); i++) {
                                    GbcImage gbcImage = importedImagesList.get(i);
                                    boolean alreadyAdded = false;
                                    //If the image already exists (by the hash) it doesn't add it. Same if it's already added
                                    for (GbcImage image : Utils.gbcImagesList) {
                                        if (image.getHashCode().toLowerCase(Locale.ROOT).equals(gbcImage.getHashCode())) {
                                            alreadyAdded = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyAdded) {
                                        GbcImage.numImages++;
                                        numImagesAdded++;
                                        ImageData imageData = new ImageData();
                                        imageData.setImageId(gbcImage.getHashCode());
                                        imageData.setData(gbcImage.getImageBytes());
                                        newImageDatas.add(imageData);
                                        Utils.gbcImagesList.add(gbcImage);
                                        newGbcImages.add(gbcImage);
                                        Utils.imageBitmapCache.put(gbcImage.getHashCode(), importedImagesBitmaps.get(i));
                                    }
                                }
                                if (newGbcImages.size() > 0) {
                                    new SaveImageAsyncTask(newGbcImages, newImageDatas).execute();
                                } else {
                                    Utils.toast(getContext(), getString(R.string.no_new_images));
                                    tvFileName.setText(getString(R.string.no_new_images));
                                }
                                break;
                            }
                            case IMAGE:
                            case PHOTO_ROM:
                            case SAV: {
                                if (!cbAddFrame.isChecked()) {
                                    HashSet transparencyHS = transparencyHashSet(finalListBitmaps.get(0));
                                    if (transparencyHS.size() > 0) {
                                        tvFileName.setText((getString(R.string.invalid_transparent_image)));
                                    } else {
                                        for (int i = 0; i < finalListImages.size(); i++) {
                                            GbcImage gbcImage = finalListImages.get(i);
                                            boolean alreadyAdded = false;
                                            //If the palette already exists (by the hash) it doesn't add it. Same if it's already added
                                            for (GbcImage image : Utils.gbcImagesList) {
                                                if (image.getHashCode().toLowerCase(Locale.ROOT).equals(gbcImage.getHashCode())) {
                                                    alreadyAdded = true;
                                                    break;
                                                }
                                            }
                                            if (!alreadyAdded) {
                                                GbcImage.numImages++;
                                                numImagesAdded++;
                                                ImageData imageData = new ImageData();
                                                imageData.setImageId(gbcImage.getHashCode());
                                                imageData.setData(gbcImage.getImageBytes());
                                                newImageDatas.add(imageData);
                                                Utils.gbcImagesList.add(gbcImage);
                                                newGbcImages.add(gbcImage);
                                                Utils.imageBitmapCache.put(gbcImage.getHashCode(), finalListBitmaps.get(i));
                                            }
                                        }
                                        if (newGbcImages.size() > 0) {
                                            new SaveImageAsyncTask(newGbcImages, newImageDatas).execute();
                                        } else {
                                            Utils.toast(getContext(), getString(R.string.no_new_images));
                                            tvFileName.setText(getString(R.string.no_new_images));
                                        }
                                    }

                                } else if (cbAddFrame.isChecked()) {
                                    if (finalListBitmaps.get(0).getHeight() != 144 && finalListBitmaps.get(0).getHeight() != 224) {
                                        Utils.toast(getContext(), getString(R.string.cant_add_frame));
                                        btnAddImages.setEnabled(true);
                                    } else frameImportDialog();
                                }
                                break;
                            }
                        }
                }
            }
        });
        // Inflate the layout for this fragment
        return view;
    }

    private void frameImportDialog() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.frame_name_dialog, null);
        ImageView ivFrame = view.findViewById(R.id.ivFrame);
        GbcFrame gbcFrame = new GbcFrame();
        gbcFrame.setFrameBitmap(finalListBitmaps.get(0));

        Bitmap bitmapCopy = finalListBitmaps.get(0).copy(finalListBitmaps.get(0).getConfig(), true);
        Bitmap bitmap = transparentBitmap(bitmapCopy, gbcFrame);
        gbcFrame.setFrameBitmap(bitmap);

        ivFrame.setImageBitmap(bitmap);
        EditText etFrameName = view.findViewById(R.id.etFrameName);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(view);
        AlertDialog alertdialog = builder.create();

        etFrameName.setImeOptions(EditorInfo.IME_ACTION_DONE);//When pressing enter
        Button btnSaveFrame = view.findViewById(R.id.btnSaveFrame);
        btnSaveFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (finalListBitmaps.get(0).getHeight() == 224) {
                    gbcFrame.setWildFrame(true);
                }

                try {
                    gbcFrame.setFrameBytes(Utils.encodeImage(gbcFrame.getFrameBitmap(), "bw"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                List<GbcFrame> newFrameImages = new ArrayList<>();
                //Add here the dialog for the frame name
                String frameName = etFrameName.getText().toString().toLowerCase(Locale.ROOT);
                if (frameName.equals("")) {
                    Utils.toast(getContext(), getString(R.string.no_empty_frame_name));
                    etFrameName.setBackgroundColor(Color.parseColor("#FF0000"));
                } else if (frameName.contains(" ")) {
                    Utils.toast(getContext(), getString(R.string.no_spaces_frame_name));//Add string
                    etFrameName.setBackgroundColor(Color.parseColor("#FF0000"));
                } else {
                    gbcFrame.setFrameName(frameName);
                    boolean alreadyAdded = false;
                    //If the palette already exists (by the name) it doesn't add it. Same if it's already added
                    if (Utils.hashFrames.containsKey(frameName)) {
                        alreadyAdded = true;
                        etFrameName.setBackgroundColor(Color.parseColor("#FF0000"));
                        Utils.toast(getContext(), getString(R.string.frame_name_exists));//Add string
                    }

                    if (!alreadyAdded) {
                        newFrameImages.add(gbcFrame);
                    }
                    if (newFrameImages.size() > 0) {
                        Utils.framesList.addAll(newFrameImages);
                        for (GbcFrame frame : newFrameImages) {
                            Utils.hashFrames.put(frame.getFrameName(), frame);
                        }
                        new SaveFrameAsyncTask(newFrameImages).execute();
                        alertdialog.dismiss();
                    }
                }
            }
        });

        builder.setNegativeButton(
                getString(R.string.cancel), (dialog, which) ->
                {
                });

        alertdialog.show();
    }

    private void extractFile() {
        //I clear the lists in case I choose several files without leaving
        importedImagesList.clear();
        importedImagesBitmaps.clear();
        listImportedImageBytes.clear();
        cbLastSeen.setChecked(false);
        cbDeleted.setChecked(false);
        switch (fileType) {
            case SAV: {
                isGoodSave = extractSavImages();
                if (isGoodSave) {
                    listActiveImages.add(new ArrayList<>(importedImagesList.subList(0, importedImagesList.size() - MainActivity.deletedCount[0] - 1)));
                    listActiveBitmaps.add(new ArrayList<>(importedImagesBitmaps.subList(0, importedImagesBitmaps.size() - MainActivity.deletedCount[0] - 1)));
                    lastSeenImage.add(importedImagesList.get(importedImagesList.size() - MainActivity.deletedCount[0] - 1));
                    lastSeenBitmap.add(importedImagesBitmaps.get(importedImagesBitmaps.size() - MainActivity.deletedCount[0] - 1));
                    listDeletedImages.add(new ArrayList<>(importedImagesList.subList(importedImagesList.size() - MainActivity.deletedCount[0], importedImagesList.size())));

                    listDeletedBitmaps.add(new ArrayList<>(importedImagesBitmaps.subList(importedImagesBitmaps.size() - MainActivity.deletedCount[0], importedImagesBitmaps.size())));
                    listDeletedBitmapsRedStroke = new ArrayList<>();
                    listDeletedBitmapsRedStroke.add(new ArrayList<>());

                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStrokeWidth(2);
                    int startX = 160;
                    int startY = 0;
                    int endX = 0;
                    int endY = 144;
                    for (Bitmap bitmap : listDeletedBitmaps.get(0)) {
                        Bitmap copiedBitmap = bitmap.copy(bitmap.getConfig(), true);//Need to get a copy of the original bitmap, or else I'll paint on it
                        Canvas canvas = new Canvas(copiedBitmap);
                        canvas.drawLine(startX, startY, endX, endY, paint);
                        listDeletedBitmapsRedStroke.get(0).add(copiedBitmap);
                    }

                    showImages(cbLastSeen, cbDeleted);
                    ImportFragment.addEnum = ImportFragment.ADD_WHAT.IMAGES;
                }
                break;
            }
            case PHOTO_ROM:
                RomExtractor romExtractor = new RomExtractor(fileBytes, fileName);
                romExtractor.romExtract();
                listActiveImages = romExtractor.getListActiveImages();
                listActiveBitmaps = romExtractor.getListActiveBitmaps();
                lastSeenImage = romExtractor.getLastSeenImage();
                lastSeenBitmap= romExtractor.getLastSeenBitmap();
                listDeletedImages= romExtractor.getListDeletedImages();
                listDeletedBitmaps= romExtractor.getListDeletedBitmaps();
                listDeletedBitmapsRedStroke =romExtractor.getListDeletedBitmapsRedStroke();
                totalImages = romExtractor.getTotalImages();
                showImages(cbLastSeen, cbDeleted);
                ImportFragment.addEnum = ImportFragment.ADD_WHAT.IMAGES;
            case IMAGE: {
                break;
            }
            case TXT: {
                try {
                    extractHexImages(fileContent);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, importedImagesList, importedImagesBitmaps, true, true, false, null);

                ImportFragment.addEnum = ImportFragment.ADD_WHAT.IMAGES;
                break;
            }
            case JSON: {
                receivedList = JsonReader.jsonCheck(fileContent);
                if (receivedList == null) {
                    Utils.toast(getContext(), getString(R.string.no_valid_list));
                    return;
                }
                break;
            }
        }
    }

    private class loadDataTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            try {
                extractFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            switch (fileType) {
                case PHOTO_ROM:
                case SAV:
                    if (!isGoodSave) {
                        tvFileName.setText(getString(R.string.no_valid_file));
                        loadingDialog.dismiss();
                        adapter = null;
                        gridViewImport.setAdapter((ListAdapter) adapter);
                        btnAddImages.setVisibility(View.GONE);
                        layoutCb.setVisibility(View.GONE);
                        return;
                    }
                    btnAddImages.setEnabled(true);
                    tvFileName.setText(totalImages + getString(R.string.images_available));
                    btnAddImages.setText(getString(R.string.btn_add_images));
                    btnAddImages.setVisibility(View.VISIBLE);
                    layoutCb.setVisibility(View.VISIBLE);
                    break;

                case TXT: {
                    btnAddImages.setEnabled(true);
                    tvFileName.setText(totalImages + getString(R.string.images_available));
                    btnAddImages.setText(getString(R.string.btn_add_images));
                    btnAddImages.setVisibility(View.VISIBLE);
                    layoutCb.setVisibility(View.GONE);
                    break;
                }
                case JSON: {
                    switch (addEnum) {
                        case PALETTES:
                            btnAddImages.setEnabled(true);
                            adapter = new CustomGridViewAdapterPalette(getContext(), R.layout.palette_grid_item, (ArrayList<GbcPalette>) receivedList, true, true);
                            customAdapterPalette = (CustomGridViewAdapterPalette) adapter;
                            btnAddImages.setText(getString(R.string.btn_add_palettes));
                            btnAddImages.setVisibility(View.VISIBLE);
                            layoutCb.setVisibility(View.GONE);
                            break;

                        case FRAMES:
                            btnAddImages.setEnabled(true);
                            btnAddImages.setText(getString(R.string.btn_add_frames));
                            btnAddImages.setVisibility(View.VISIBLE);
                            layoutCb.setVisibility(View.GONE);
                            adapter = new FramesFragment.CustomGridViewAdapterFrames(getContext(), R.layout.frames_row_items, (List<GbcFrame>) receivedList, true, true);
                            break;
                        case IMAGES:
                            btnAddImages.setEnabled(true);
                            btnAddImages.setText(getString(R.string.btn_add_images));
                            btnAddImages.setVisibility(View.VISIBLE);
                            layoutCb.setVisibility(View.GONE);
                            adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, importedImagesList, importedImagesBitmaps, true, true, false, null);
                            break;
                    }
                    break;
                }
            }

            gridViewImport.setAdapter((ListAdapter) adapter);
            loadingDialog.dismiss();

        }
    }

    //Refactor this with UsbSerialFragment
    public void showImages(CheckBox showLastSeen, CheckBox showDeleted) {
        List<Bitmap> bitmapsAdapterList = new ArrayList<>();
        finalListImages.clear();
        finalListBitmaps.clear();
        if (!showLastSeen.isChecked() && !showDeleted.isChecked()) {
            for (List<GbcImage> gbcImageList : listActiveImages) {
                finalListImages.addAll(gbcImageList);
            }
            for (List<Bitmap> bitmapList : listActiveBitmaps) {
                finalListBitmaps.addAll(bitmapList);
            }
            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);

        } else if (showLastSeen.isChecked() && !showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.add(lastSeenImage.get(i));
            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                finalListBitmaps.add(lastSeenBitmap.get(i));
            }
            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);

        } else if (!showLastSeen.isChecked() && showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.addAll(listDeletedImages.get(i));
            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                bitmapsAdapterList.addAll(listActiveBitmaps.get(i));
                finalListBitmaps.addAll(listDeletedBitmaps.get(i));
                bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke.get(i));

            }
        } else if (showLastSeen.isChecked() && showDeleted.isChecked()) {
            for (int i = 0; i < listActiveImages.size(); i++) {
                finalListImages.addAll(listActiveImages.get(i));
                finalListImages.add(lastSeenImage.get(i));
                finalListImages.addAll(listDeletedImages.get(i));

            }
            for (int i = 0; i < listActiveBitmaps.size(); i++) {
                finalListBitmaps.addAll(listActiveBitmaps.get(i));
                bitmapsAdapterList.addAll(listActiveBitmaps.get(i));

                finalListBitmaps.add(lastSeenBitmap.get(i));
                bitmapsAdapterList.add(lastSeenBitmap.get(i));

                finalListBitmaps.addAll(listDeletedBitmaps.get(i));
                bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke.get(i));
            }
        }

        adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, bitmapsAdapterList, true, true, false, null);    }

    //Refactor, also on UsbSerialFragment
//    private void showImages(CheckBox showLastSeen, CheckBox showDeleted) {
//        List<Bitmap> bitmapsAdapterList = null;
//        if (!showLastSeen.isChecked() && !showDeleted.isChecked()) {
//            finalListImages = new ArrayList<>(listActiveImages);
//            finalListBitmaps = new ArrayList<>(listActiveBitmaps);
//            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);
//        } else if (showLastSeen.isChecked() && !showDeleted.isChecked()) {
//            finalListImages = new ArrayList<>(listActiveImages);
//            finalListBitmaps = new ArrayList<>(listActiveBitmaps);
//            finalListImages.add(lastSeenImage.get(i));
//            finalListBitmaps.add(lastSeenBitmap);
//            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);
//
//        } else if (!showLastSeen.isChecked() && showDeleted.isChecked()) {
//            finalListImages = new ArrayList<>(listActiveImages);
//            finalListBitmaps = new ArrayList<>(listActiveBitmaps);
//            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);
//            finalListImages.addAll(listDeletedImages);
//            finalListBitmaps.addAll(listDeletedBitmaps);
//            bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke);
//        } else if (showLastSeen.isChecked() && showDeleted.isChecked()) {
//            finalListImages = new ArrayList<>(listActiveImages);
//            finalListBitmaps = new ArrayList<>(listActiveBitmaps);
//            finalListImages.add(lastSeenImage);
//            finalListImages.addAll(listDeletedImages);
//            finalListBitmaps.add(lastSeenBitmap);
//            bitmapsAdapterList = new ArrayList<>(finalListBitmaps);
//            finalListBitmaps.addAll(listDeletedBitmaps);
//            bitmapsAdapterList.addAll(listDeletedBitmapsRedStroke);
//        }
//        adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, bitmapsAdapterList, true, true, false, null);
//    }

    private class SaveImageAsyncTask extends AsyncTask<Void, Void, Void> {
        List<GbcImage> gbcImagesList;
        List<ImageData> imageDataList;

        public SaveImageAsyncTask(List<GbcImage> gbcImagesList, List<ImageData> imageDataList) {
            this.gbcImagesList = gbcImagesList;
            this.imageDataList = imageDataList;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ImageDao imageDao = MainActivity.db.imageDao();
            ImageDataDao imageDataDao = MainActivity.db.imageDataDao();
            //Need to insert first the gbcImage because of the Foreign Key
            for (GbcImage gbcImage : gbcImagesList) {
                imageDao.insert(gbcImage);
            }
            for (ImageData imageData : imageDataList) {
                imageDataDao.insert(imageData);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            tvFileName.setText(numImagesAdded + getString(R.string.done_adding_images));
            Utils.toast(getContext(), getString(R.string.images_added) + numImagesAdded);
        }
    }

    private class SavePaletteAsyncTask extends AsyncTask<Void, Void, Void> {
        List<GbcPalette> gbcPalettesList;

        public SavePaletteAsyncTask(List<GbcPalette> gbcPalettesList) {
            this.gbcPalettesList = gbcPalettesList;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            PaletteDao paletteDao = MainActivity.db.paletteDao();
            for (GbcPalette gbcPalette : gbcPalettesList) {
                paletteDao.insert(gbcPalette);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            tvFileName.setText(getString(R.string.done_adding_palettes));
            Utils.toast(getContext(), getString(R.string.palette_added));
        }
    }

    private class SaveFrameAsyncTask extends AsyncTask<Void, Void, Void> {
        List<GbcFrame> gbcFramesList;

        public SaveFrameAsyncTask(List<GbcFrame> gbcFramesList) {
            this.gbcFramesList = gbcFramesList;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            FrameDao frameDao = MainActivity.db.frameDao();
            for (GbcFrame gbcFrame : gbcFramesList) {
                frameDao.insert(gbcFrame);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            tvFileName.setText(getString(R.string.done_adding_frames));
            Utils.toast(getContext(), getString(R.string.frames_added));
        }

    }

    public void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//Any type of file
        startActivityForResult(Intent.createChooser(intent, getString(R.string.btn_select_file)), 123);
    }

    /**
     * //https://www.youtube.com/watch?v=4EKlAvjY74U&t=0s
     *
     * @param view
     */
    public void openFileDialog(View view) {
        Intent data = new Intent(Intent.ACTION_GET_CONTENT);
        data.addCategory(Intent.CATEGORY_OPENABLE);

        data.setType("*/*");
        data.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        data = Intent.createChooser(data, "Choose a file");
        sActivityResultLauncher.launch(data);
    }

    //Method to get the filename, because the uri sometimes ended with a number
    @SuppressLint("Range")
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    ActivityResultLauncher<Intent> sActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        cbAddFrame.setVisibility(View.GONE);
                        cbAddFrame.setChecked(false);
                        Intent data = result.getData();

                        if (data != null) {
                            if (data.getClipData() != null) {
                                // Multiple files were selected
                                int count = data.getClipData().getItemCount();
                                boolean allFilesValid = true;

                                for (int i = 0; i < count; i++) {
                                    Uri uri = data.getClipData().getItemAt(i).getUri();

                                    //Verify if it's a valid image
                                    if (!isImageFile(getFileName(uri))) {
                                        allFilesValid = false;
                                        break; //Leave the loop if it finds a non valid file
                                    }
                                }

                                if (allFilesValid) {
                                    List<Uri> uris = new ArrayList<>();

                                    //Order by name
                                    for (int i = 0; i < count; i++) {
                                        Uri uri = data.getClipData().getItemAt(i).getUri();
                                        uris.add(uri);
                                    }
                                    // Ordenar las Uri por el nombre del archivo
                                    Collections.sort(uris, new Comparator<Uri>() {
                                        @Override
                                        public int compare(Uri uri1, Uri uri2) {
                                            String path1 = getFileName(uri1);
                                            String path2 = getFileName(uri2);

                                            return path1.compareTo(path2);
                                        }
                                    });

                                    finalListImages.clear();
                                    finalListBitmaps.clear();
                                    tvFileName.setText("Selected files: " + count);
                                    for (int i = 0; i < count; i++) {
                                        Uri uri = uris.get(i);
//                                        Uri uri = data.getClipData().getItemAt(i).getUri();

                                        fileName = getFileName(uri);
                                        fileType = FILE_TYPE.IMAGE;

                                        try {
                                            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);

                                            BitmapFactory.Options options = new BitmapFactory.Options();
                                            options.inJustDecodeBounds = true;
                                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                                            bitmap = resizeImage(bitmap);
                                            boolean hasAllColors = checkPaletteColors(bitmap);
                                            if (!hasAllColors) {
                                                bitmap = convertToGrayScale(bitmap);
                                                bitmap = ditherImage(bitmap);
                                            }
                                            GbcImage gbcImage = new GbcImage();
                                            //Add a null frame id for imported images. Need to use a "NO FRAME OPTION, AS IMPORTED"
//                                if (bitmap.getHeight()!=144){
//                                    gbcImage.setFrameId(null);
//                                }
                                            byte[] imageBytes = Utils.encodeImage(bitmap, "bw");
                                            gbcImage.setImageBytes(imageBytes);
                                            byte[] hash = MessageDigest.getInstance("SHA-256").digest(imageBytes);
                                            String hashHex = Utils.bytesToHex(hash);
                                            gbcImage.setHashCode(hashHex);
                                            ImageData imageData = new ImageData();
                                            imageData.setImageId(hashHex);
                                            imageData.setData(imageBytes);
                                            importedImageDatas.add(imageData);
                                            gbcImage.setName(fileName);
                                            finalListBitmaps.add(bitmap);
                                            finalListImages.add(gbcImage);
//
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    }
                                    btnExtractFile.setVisibility(View.GONE);
                                    btnAddImages.setVisibility(View.VISIBLE);
                                    btnAddImages.setEnabled(true);
                                    adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, finalListBitmaps, true, true, false, null);
                                    gridViewImport.setAdapter((ListAdapter) adapter);
                                    ImportFragment.addEnum = ImportFragment.ADD_WHAT.IMAGES;
//                                    cbAddFrame.setVisibility(View.VISIBLE);
                                    gridViewImport.setAdapter((ListAdapter) adapter);

                                } else {
                                    // Show error message
                                    tvFileName.setText("Please, select only valid image files (.jpg, .jpeg, .png, .bmp).");

                                }
                            } else if (data.getData() != null) {
                                // Single file was selected
                                Uri uri = data.getData();
                                fileName = getFileName(uri);
                                //I check the extension of the file
                                if (fileName.endsWith("sav")) {
                                    ByteArrayOutputStream byteStream = null;
                                    fileType = FILE_TYPE.SAV;

                                    try {
                                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                                        byteStream = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = inputStream.read(buffer)) != -1) {
                                            byteStream.write(buffer, 0, len);
                                        }
                                        byteStream.close();
                                        inputStream.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    fileBytes = byteStream.toByteArray();
                                    tvFileName.setText(getString(R.string.file_name) + fileName);
                                    btnExtractFile.setVisibility(View.VISIBLE);
                                } else if (fileName.endsWith("gbc")) {
                                    ByteArrayOutputStream byteStream = null;
                                    fileType = FILE_TYPE.PHOTO_ROM;

                                    try {
                                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                                        byteStream = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = inputStream.read(buffer)) != -1) {
                                            byteStream.write(buffer, 0, len);
                                        }
                                        byteStream.close();
                                        inputStream.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    fileBytes = byteStream.toByteArray();
                                    tvFileName.setText(getString(R.string.file_name) + fileName);
                                    btnExtractFile.setVisibility(View.VISIBLE);

                                } else if (fileName.endsWith("txt")) {
                                    fileType = FILE_TYPE.TXT;
                                    try {
                                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                                        StringBuilder stringBuilder = new StringBuilder();
                                        try {
                                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                                            String line = bufferedReader.readLine();
                                            while (line != null) {
                                                stringBuilder.append(line).append('\n');
                                                line = bufferedReader.readLine();
                                            }
                                            bufferedReader.close();
                                            inputStream.close();

                                        } catch (FileNotFoundException e) {
                                            e.printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        fileContent = stringBuilder.toString();
                                        fileBytes = fileContent.getBytes(StandardCharsets.UTF_8);
                                        tvFileName.setText(getString(R.string.file_name) + fileName);
                                        btnExtractFile.setVisibility(View.VISIBLE);
                                    } catch (Exception e) {
                                    }
                                } else if (fileName.endsWith("json")) {
                                    fileType = FILE_TYPE.JSON;

                                    try {
                                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                                        StringBuilder stringBuilder = new StringBuilder();
                                        try {
                                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                                            String line = bufferedReader.readLine();
                                            while (line != null) {
                                                stringBuilder.append(line).append('\n');
                                                line = bufferedReader.readLine();
                                            }
                                            bufferedReader.close();
                                            inputStream.close();

                                        } catch (FileNotFoundException e) {
                                            e.printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        fileContent = stringBuilder.toString();
                                        fileBytes = fileContent.getBytes(StandardCharsets.UTF_8);
                                        tvFileName.setText(getString(R.string.file_name) + fileName);
                                        btnExtractFile.setVisibility(View.VISIBLE);

                                    } catch (Exception e) {
                                    }
                                } else if (fileName.endsWith("png") || fileName.endsWith("jpg") || fileName.endsWith("jpeg") || fileName.endsWith("bmp")) {
                                    fileType = FILE_TYPE.IMAGE;
                                    finalListImages.clear();
                                    finalListBitmaps.clear();
                                    try {
                                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);

                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inJustDecodeBounds = true;
                                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                                        bitmap = resizeImage(bitmap);
                                        boolean hasAllColors = checkPaletteColors(bitmap);
                                        if (!hasAllColors) {
                                            bitmap = convertToGrayScale(bitmap);
                                            bitmap = ditherImage(bitmap);
                                        }
                                        GbcImage gbcImage = new GbcImage();
                                        //Add a null frame id for imported images. Need to use a "NO FRAME OPTION, AS IMPORTED"
//                                if (bitmap.getHeight()!=144){
//                                    gbcImage.setFrameId(null);
//                                }
                                        byte[] imageBytes = Utils.encodeImage(bitmap, "bw");
                                        gbcImage.setImageBytes(imageBytes);
                                        byte[] hash = MessageDigest.getInstance("SHA-256").digest(imageBytes);
                                        String hashHex = Utils.bytesToHex(hash);
                                        gbcImage.setHashCode(hashHex);
                                        ImageData imageData = new ImageData();
                                        imageData.setImageId(hashHex);
                                        imageData.setData(imageBytes);
                                        importedImageDatas.add(imageData);
                                        gbcImage.setName(fileName);
                                        finalListBitmaps.add(bitmap);
                                        finalListImages.add(gbcImage);
                                        tvFileName.setText(getString(R.string.file_name) + fileName);
                                        btnExtractFile.setVisibility(View.GONE);
                                        btnAddImages.setVisibility(View.VISIBLE);
                                        btnAddImages.setEnabled(true);

                                        adapter = new CustomGridViewAdapterImage(getContext(), R.layout.row_items, finalListImages, finalListBitmaps, true, true, false, null);
                                        gridViewImport.setAdapter((ListAdapter) adapter);
                                        ImportFragment.addEnum = ImportFragment.ADD_WHAT.IMAGES;
                                        cbAddFrame.setVisibility(View.VISIBLE);
                                        gridViewImport.setAdapter((ListAdapter) adapter);

//                                } else {
//                                    tvFileName.setText(getString(R.string.file_name) + fileName);
//                                    tvFileName.setText(getString(R.string.no_valid_image_file));
//                                    btnExtractFile.setVisibility(View.GONE);
//                                    btnAddImages.setVisibility(View.GONE);
//                                    layoutCb.setVisibility(View.GONE);
//                                    gridViewImport.setAdapter(null);
//                                    bitmap.recycle();
//                                }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                } else {
                                    btnExtractFile.setVisibility(View.GONE);

                                    tvFileName.setText(getString(R.string.no_valid_file));
                                }
                            }
                        }

                    }

                }
            }
    );

    /**
     * Checks if the selected file are images
     *
     * @return true if all are images
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        String[] validExtensions = {".jpg", ".jpeg", ".png", ".bmp"};
        for (String extension : validExtensions) {
            if (fileName.toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public boolean extractSavImages() {
        Extractor extractor = new SaveImageExtractor(new IndexedPalette(IndexedPalette.EVEN_DIST_PALETTE));
        try {
            //Extract the images
            listImportedImageBytes = extractor.extractBytes(fileBytes, 0);
            //Check for Magic or FF bytes
            if (!magicIsReal(fileBytes)) {
                return false;
            }
            int nameIndex = 1;
            for (byte[] imageBytes : listImportedImageBytes) {
                GbcImage gbcImage = new GbcImage();
                String formattedIndex = String.format("%02d", nameIndex);
                if (nameIndex == listImportedImageBytes.size() - MainActivity.deletedCount[0]) {//Last seen image
                    gbcImage.setName(fileName + " [last seen]");
                } else if (nameIndex > listImportedImageBytes.size() - MainActivity.deletedCount[0]) {//Deleted images
                    gbcImage.setName(fileName + " [deleted]");
                } else {
                    gbcImage.setName(fileName + " " + formattedIndex);
                }
                nameIndex++;
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(imageBytes);
                String hashHex = Utils.bytesToHex(hash);
                gbcImage.setHashCode(hashHex);
                ImageCodec imageCodec = new ImageCodec(128, 112, gbcImage.isLockFrame());
                Bitmap image = imageCodec.decodeWithPalette(Utils.hashPalettes.get(gbcImage.getPaletteId()).getPaletteColorsInt(), Utils.hashPalettes.get(gbcImage.getFramePaletteId()).getPaletteColorsInt(), imageBytes, false, false, false);
                if (image.getHeight() == 112 && image.getWidth() == 128) {
                    //I need to use copy because if not it's inmutable bitmap
                    Bitmap framed = Utils.hashFrames.get("Nintendo_Frame").getFrameBitmap().copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(framed);
                    canvas.drawBitmap(image, 16, 16, null);
                    image = framed;
                    imageBytes = Utils.encodeImage(image, gbcImage.getPaletteId());
                }
                ImageData imageData = new ImageData();
                imageData.setImageId(gbcImage.getHashCode());
                imageData.setData(imageBytes);
                importedImageDatas.add(imageData);
                gbcImage.setImageBytes(imageBytes);
                importedImagesBitmaps.add(image);
                importedImagesList.add(gbcImage);
                totalImages = importedImagesList.size();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public static void extractHexImages(String fileContent) throws NoSuchAlgorithmException {
        List<String> dataList = HexToTileData.separateData(fileContent);
        String data = "";
        int index = 1;
        for (String string : dataList) {
            data = string.replaceAll(System.lineSeparator(), " ");
            byte[] bytes = convertToByteArray(data);
            GbcImage gbcImage = new GbcImage();
            gbcImage.setImageBytes(bytes);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            String hashHex = Utils.bytesToHex(hash);
            gbcImage.setHashCode(hashHex);
            ImageData imageData = new ImageData();
            imageData.setImageId(hashHex);
            imageData.setData(bytes);
            importedImageDatas.add(imageData);
            String formattedIndex = String.format("%02d", index++);
            gbcImage.setName(fileName + " " + formattedIndex);
            int height = (data.length() + 1) / 120;//To get the real height of the image
            ImageCodec imageCodec = new ImageCodec(160, height, false);
            Bitmap image = imageCodec.decodeWithPalette(Utils.hashPalettes.get(gbcImage.getPaletteId()).getPaletteColorsInt(), Utils.hashPalettes.get(gbcImage.getFramePaletteId()).getPaletteColorsInt(), gbcImage.getImageBytes(), false, false, false);
            importedImagesBitmaps.add(image);
            importedImagesList.add(gbcImage);
        }
    }

    public static byte[] convertToByteArray(String data) {
        String[] byteStrings = data.split(" ");
        byte[] bytes = new byte[byteStrings.length];
        try {
            for (int i = 0; i < byteStrings.length; i++) {
                bytes[i] = (byte) ((Character.digit(byteStrings[i].charAt(0), 16) << 4)
                        + Character.digit(byteStrings[i].charAt(1), 16));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytes;
    }
}