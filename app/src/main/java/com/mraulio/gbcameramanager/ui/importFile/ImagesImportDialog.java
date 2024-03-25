package com.mraulio.gbcameramanager.ui.importFile;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.mraulio.gbcameramanager.utils.Utils.rotateBitmap;
import static com.mraulio.gbcameramanager.utils.Utils.tagsHash;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;

import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.model.ImageData;
import com.mraulio.gbcameramanager.ui.gallery.SaveImageAsyncTask;
import com.mraulio.gbcameramanager.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class ImagesImportDialog {
    List<GbcImage> newGbcImages;
    List<Bitmap> newImageBitmaps;
    Context context;
    Activity activity;
    TextView tvFileName;
    int numImagesAdded;
    DocumentFile file;

    public ImagesImportDialog(List<GbcImage> newGbcImages, List<Bitmap> newImageBitmaps, DocumentFile file, Context context, Activity activity, TextView tvFileName, int numImagesAdded) {
        this.newGbcImages = newGbcImages;
        this.newImageBitmaps = newImageBitmaps;
        this.file = file;
        this.context = context;
        this.activity = activity;
        this.tvFileName = tvFileName;
        this.numImagesAdded = numImagesAdded;
    }

    //To show the "big" Image dialog when doing a simple tap on the image
    public void createImagesImportDialog() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.images_import_dialog);
        dialog.setCancelable(false);//So it doesn't close when clicking outside or back button
        EditText etImageName = dialog.findViewById(R.id.etImageNameImport);
        CheckBox cbUseModDate = dialog.findViewById(R.id.cbUseModDate);
        final String[] fileName = {""};
        long lastModifiedTime = 0;
        if (file != null) {
            fileName[0] = file.getName();
            etImageName.setText(fileName[0]);
            lastModifiedTime = file.lastModified();
        }else{
            cbUseModDate.setVisibility(GONE);
            etImageName.setText("----");
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH-mm-ss", Locale.getDefault());
        String dateString = dateFormat.format(lastModifiedTime);
        cbUseModDate.setText(context.getString(R.string.cb_use_mod_date) + ": " + dateString);
        List<ImageView> imageViewList = new ArrayList<>();

        LinearLayout layoutSelected = dialog.findViewById(R.id.lyMultipleImagesImport);

        for (int i = 0; i < newGbcImages.size(); i++) {
            GbcImage gbcImage = newGbcImages.get(i);

            ImageView imageViewMini = new ImageView(context);
            imageViewMini.setId(i);
            imageViewMini.setPadding(5, 5, 5, 5);
            Bitmap image = newImageBitmaps.get(i);
            imageViewMini.setImageBitmap(rotateBitmap(image, gbcImage));

            imageViewList.add(imageViewMini);
            layoutSelected.addView(imageViewMini);

        }

        List<String> tempTags = new ArrayList<>();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        Button btnOkWriteTag = dialog.findViewById(R.id.btnOkWriteTagImport);
        Button btnAddImages = dialog.findViewById(R.id.btnAddImagesImport);




        final boolean[] editingName = {false};
        boolean[] editingTags = {false};

        etImageName.addTextChangedListener(new TextWatcher() {
            String placeholderString = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                placeholderString = etImageName.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                placeholderString = etImageName.getText().toString().trim();

                etImageName.setBackgroundColor(context.getColor(R.color.update_image_color));
                editingName[0] = true;
                fileName[0] = new String(placeholderString);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        //Autocomplete text view Text Write tag
        AutoCompleteTextView autoCAddTag = dialog.findViewById(R.id.etWriteTagImport);
        List<String> availableTotalTags = new ArrayList<>(tagsHash);
        List<String> availableTotalTagsSpinner = new ArrayList<>();
        List<String> availableTotalTagsAutoComplete = new ArrayList<>();
        List<String> showingTags = new ArrayList<>();

        for (
                String tag : availableTotalTags) {
            if (tag.equals("__filter:favourite__")) {
                tag = "Favourite \u2764\ufe0f";
            }
            availableTotalTagsAutoComplete.add(tag);
        }
        availableTotalTagsSpinner.add(context.getString(R.string.tags_dialog_title));
        availableTotalTagsSpinner.addAll(availableTotalTagsAutoComplete);

        ArrayAdapter<String> adapterAutoComplete = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, availableTotalTagsAutoComplete);

        autoCAddTag.setAdapter(adapterAutoComplete);
        autoCAddTag.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(autoCAddTag.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        LinearLayout editTagsLayout = dialog.findViewById(R.id.editTagsLayoutImport);
        editTagsLayout.setVisibility(VISIBLE);

        LinearLayout tagsLayout = dialog.findViewById(R.id.tagsCheckBoxesImport);

        Spinner spAvailableTags = dialog.findViewById(R.id.spAvailableTagsImport);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, availableTotalTagsSpinner);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAvailableTags.setAdapter(adapter);
        final boolean[] isSpinnerTouched = {false};

        btnOkWriteTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newTag = autoCAddTag.getText().toString().trim();
                if (newTag.isEmpty())
                    return;
                if (newTag.equals("Favourite \u2764\ufe0f")) {
                    newTag = "__filter:favourite__";//Reverse the tag
                }
                if (!tempTags.contains(newTag)) {
                    //Generate dynamically new checkboxes
                    createTagsCheckBox(newTag, tagsLayout, tempTags);

                    tempTags.add(newTag);

                    editingTags[0] = true;

                    btnAddImages.setEnabled(true);
                }
            }
        });

        spAvailableTags.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSpinnerTouched[0]) {
                    isSpinnerTouched[0] = true;
                    return;
                }
                if (position == 0) {
                    return;
                }
                String selectedTag = adapter.getItem(position);
                if (selectedTag.equals("Favourite \u2764\ufe0f")) {
                    selectedTag = "__filter:favourite__";//Reverse the tag
                }
                if (!showingTags.contains(selectedTag)) {
                    if (!tempTags.contains(selectedTag)) {
                        //Generate dynamically new checkboxes
                        createTagsCheckBox(adapter.getItem(position), tagsLayout, tempTags);
                        tempTags.add(selectedTag);

                    }
                }
                editingTags[0] = true;
                btnAddImages.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnAddImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int nameIndex = 1;
                int maxIndex = newGbcImages.size();
                int numDigits = String.valueOf(maxIndex).length();

                String formatString = "%0" + numDigits + "d";
                int dateIndex = 0;
                Date lastModDate;
                for (GbcImage gbcImageToAdd : newGbcImages) {
                    if (cbUseModDate.isChecked()) {
                        long lastModifiedTime = file.lastModified() + dateIndex++;
                        lastModDate = new Date(lastModifiedTime);
                        gbcImageToAdd.setCreationDate(lastModDate);
                    }
                    if (editingName[0]) {
                        if (maxIndex > 1) {
                            String formattedIndex = String.format(formatString, nameIndex);
                            gbcImageToAdd.setName(fileName[0] + " " + formattedIndex);
                        } else {
                            gbcImageToAdd.setName(fileName[0]);
                        }
                        nameIndex++;
                    }
                    if (editingTags[0]) {
                        HashSet<String> tagsToSave = new HashSet<>(tempTags);//So it doesn't follow the temptags if I select another
                        gbcImageToAdd.setTags(tagsToSave);
                    }
                }
                SaveImageAsyncTask saveImageAsyncTask = new SaveImageAsyncTask(newGbcImages, newImageBitmaps, context, tvFileName, numImagesAdded);
                saveImageAsyncTask.execute();

                dialog.dismiss();
            }
        });

        Button closeButton = dialog.findViewById(R.id.btbCancelImport);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
        dialog.getWindow().

                setAttributes(lp);
    }

    private void createTagsCheckBox(String tag, LinearLayout tagsLayout, List<String> tempTags) {
        CheckBox tagCb = new CheckBox(context);
        String cbText;
        if (tag.equals("__filter:favourite__")) {
            cbText = "Favourite \u2764\ufe0f";
        } else cbText = tag;

        tagCb.setText(cbText);
        tagCb.setChecked(true);

        String finalTag = tag;
        tagCb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tagCb.isChecked()) {
                    if (!tempTags.contains(finalTag))
                        tempTags.add(finalTag);
                } else {
                    if (tempTags.contains(finalTag))
                        tempTags.remove(finalTag);
                }
            }
        });

        tagsLayout.addView(tagCb);
    }
}
